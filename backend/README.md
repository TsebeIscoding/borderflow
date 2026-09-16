# BorderFlow site service

Per-site Spring Boot service. The same JAR is deployed once per
operational site (Depot, Border, Port, Destination), each instance
pointed at that site's own local Postgres and carrying its own `SITE_ID`
— consistent with the design requirement that every site keep operating
and accepting writes with zero connectivity to the others.

## Status

What's implemented:

- Application entry point and Spring Boot wiring (`BorderFlowSiteApplication`)
- `TripState` and `TripMaster` JPA entities + repositories, mapped to
  their respective tables
- **Trip read endpoints** — `GET /api/trips` (every trip this site's
  local database currently knows about) and `GET /api/trips/{id}`
  (single trip). Both read from a join of the Master and State
  fragments purely at the response-DTO level (`TripSummaryResponse`) —
  the fragment boundary only constrains writes, not reads. No
  cross-site network call is involved; see the class javadoc on
  `TripController` for why that's safe given replication.
- **The `Handover` use case** — `POST /api/trips/{tripId}/handover`.
  Accepts a handover request, writes a `Handover` event, advances
  `TripState`, and enforces the business rules the database
  deliberately does *not* enforce (see below). Covered by unit tests in
  `src/test/java/com/borderflow/handover/HandoverServiceTest.java`.
- `application.yml` with per-site config placeholders (`SITE_ID`,
  datasource, and `spring.flyway.enabled: false` since migrations are
  applied via the `db/migrations` + `infra/k8s` flow, not by this
  service on boot)
- **Authentication and authorization** — `POST /api/auth/login`,
  RS256 JWTs, and role-based access control on every endpoint. See
  [Authentication and authorization](#authentication-and-authorization)
  below for the full trust model.
- **Container read + relocate endpoints** — `GET /api/containers`,
  `GET /api/containers/{id}`, `POST /api/containers/{id}/relocate`.
  Same shape as Trip's endpoints, same auth rules (OPERATOR or AUDITOR
  to read, OPERATOR only to write), unit tested in
  `src/test/java/com/borderflow/container/ContainerRelocationServiceTest.java`,
  and **fully verified against the live cluster** — see
  `docs/testing/test-results.md`. Two known simplifications versus
  Trip, documented in `ContainerRelocationService`'s class javadoc: no
  terminal "Delivered" status, no matching event-log row.
- **Vehicle and Driver read + relocate endpoints** —
  `GET /api/vehicles`, `GET /api/vehicles/{id}`,
  `POST /api/vehicles/{id}/relocate`, and the equivalent under
  `/api/drivers`. Identical shape to Container's, unit tested. **Not
  yet applied against live data or exercised end to end** — same
  caveat as Container was before its own testing pass.
- **Client and Consignment read endpoints** — `GET /api/clients`,
  `GET /api/clients/{id}`, `GET /api/consignments`,
  `GET /api/consignments/{id}`. Read-only, both roles — there's no
  relocation concept for either (they're static Master data with no
  State fragment). **`client_contact` (the PII table) has no entity,
  repository, or endpoint anywhere in this codebase** — see
  `ClientCore`'s class javadoc for why that's a deliberate choice, not
  an oversight.

What's not implemented yet:

- A proper secrets pipeline for the JWT keys — they currently ship as
  PEM files in `src/main/resources/keys/`, fine for running this
  project locally, not fine for anything beyond that. See the warning
  in that section.
- Any write endpoint for Client/Consignment, or for the PII table.
- End-to-end testing for Vehicle, Driver, Client, and Consignment —
  all four are unit tested only so far; none has been run against a
  live, replicated database the way Trip and Container have.

## The Handover use case, and what it does vs. leaves to the database

`HandoverService` is deliberately thin. It only enforces the two rules
that the database structurally *cannot* express on its own:

1. **A site can only hand off a trip it currently holds** — checked
   against `TripState.currentSiteId` before writing anything. This is a
   same-request business rule, not a replication concern.
2. **A `Delivered` trip is terminal** — no further handover is valid
   once a trip has reached its destination.

Everything else is left to the Postgres triggers on purpose, because
they have to hold even for writes this service didn't make (i.e. ones
arriving via replication from another site's instance):

- **Event idempotency** — retrying the same handover request is safe;
  `skip_duplicate_handover` absorbs the duplicate `event_id` silently.
- **Concurrent-write conflict resolution** — if two sites' instances
  somehow raced on the same trip, `resolve_trip_state_conflict`
  rejects the stale write based on `lamport_ts`, not this service.
- **Single-leader enforcement on Master fragments** — `fromSiteId` is
  never taken from the request body specifically so a compromised or
  buggy client can't forge a handover on another site's behalf; the
  `REVOKE`-based lockdown in `db/migrations` is the actual backstop.

`fromSiteId` always comes from this instance's own `site.id` config,
never from the caller — see `HandoverRequest`'s javadoc.

## Authentication and authorization

Two roles, two trust boundaries, both RS256 JWTs:

- **OPERATOR** — site-local staff. Signed with the issuing site's own
  private key, and only ever trusted when verified against that SAME
  site's public key. A Border-issued token means nothing at Port.
- **AUDITOR** — cross-site, read-only. Signed with **Depot's** private
  key regardless of which site's `AuthController` issued it in
  practice (in this deployment, only Depot's `app_users` table has any
  `AUDITOR` rows — see `db/migrations/depot/V6`). Every site is
  configured with Depot's public key and can verify one of these
  tokens completely offline, with no network call back to Depot at
  request time — that's what makes it a genuinely cross-site
  credential rather than just "a token Depot happens to have issued."

`POST /api/auth/login` is the only unauthenticated endpoint
(`SecurityConfig`). Everything else requires a valid `Authorization:
Bearer <token>` header; `GET /api/trips/**` accepts either role,
`POST /api/trips/{id}/handover` requires OPERATOR (`@PreAuthorize` on
each controller — see `HandoverController`, `TripController`).

**How `JwtService.validate()` decides which role to grant** is the
one piece of this worth reading directly
(`src/main/java/com/borderflow/auth/JwtService.java`) before trusting
it: it checks the token's signature against this site's own key AND
Depot's key independently, then only grants OPERATOR if it verified
against the local key and AUDITOR if it verified against Depot's key
— never trusting the token's own `role` claim on its own. A bug in an
earlier draft of this logic (short-circuiting on whichever key check
ran first) silently broke Depot's ability to validate its own AUDITOR
tokens, since Depot's own key and Depot's key are the same key. Fixed,
and covered by a regression test in `JwtServiceTest`.

**Local dev credentials** (seeded by `db/migrations/*/V6`, change or
remove before any real deployment):

| Username | Password | Role | Where |
|---|---|---|---|
| `operator1` | `ChangeMe123!` | OPERATOR | every site |
| `auditor1` | `ChangeMe123!` | AUDITOR | Depot only |

**⚠️ The RSA keypairs in `src/main/resources/keys/` are local-dev
only**, generated once and checked in purely so this project runs out
of the box without an extra setup step. A real deployment would load
these from a secrets manager or a mounted volume, never ship them
inside the built JAR.

## Why the schema isn't managed by JPA

`spring.jpa.hibernate.ddl-auto` is set to `validate`, not `update` or
`create`. Schema ownership belongs entirely to the Flyway migrations in
`../db/migrations` — JPA entities here describe the shape of tables that
already exist, they never create or alter them. This matters
specifically because of the single-leader enforcement work done during
testing (see `../docs/testing/test-results.md` #5): if JPA were allowed
to manage schema, it would run as whatever role the application
connects as, which would need write access to Master fragments
everywhere — defeating the whole point of the `REVOKE` lockdown.

## Running locally

```bash
mvn clean test          # confirm it compiles and all tests pass
mvn spring-boot:run      # reads SITE_ID env var to pick this instance's site
```

Running the full mesh locally means four instances, one per site, each
with a different `SITE_ID` and datasource pointed at that site's own
`*-db-0` pod — mirroring how they'll actually be deployed. Log in via
`POST /api/auth/login` with one of the demo accounts above before
calling anything else; every endpoint except `/api/auth/login` itself
requires a valid token.
