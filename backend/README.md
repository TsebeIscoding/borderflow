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
- **Trip create + delete** — `POST /api/trips`,
  `DELETE /api/trips/{tripId}`. Origin-only (Depot), same enforcement
  pattern as everywhere else — see the CRUD section below.
  **Verified against live data** — see section 8 of
  `docs/testing/test-results.md`.
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
- **Container create + delete** — `POST /api/containers`,
  `DELETE /api/containers/{containerId}`. Same origin-only pattern as
  Trip's. Create validates the
  referenced consignment exists (clean 404, backed by the V7 foreign key). **Verified against live data** (section 8 of
  `docs/testing/test-results.md`).
- **Vehicle and Driver read + relocate endpoints** —
  `GET /api/vehicles`, `GET /api/vehicles/{id}`,
  `POST /api/vehicles/{id}/relocate`, and the equivalent under
  `/api/drivers`. Identical shape to Container's, unit tested, and
  fully verified against live data on the real cluster: list, get,
  relocate, business-rule rejection (409), and AUDITOR-blocked (403)
  all confirmed working end to end.
- **Vehicle and Driver create + delete** — same origin-only pattern.
  **Verified against live data** (section 8 of
  `docs/testing/test-results.md`).
- **Client and Consignment read endpoints** — `GET /api/clients`,
  `GET /api/clients/{id}`, `GET /api/consignments`,
  `GET /api/consignments/{id}`. Read-only, both roles — there's no
  relocation concept for either (they're static Master data with no
  State fragment). Verified against live data. **`client_contact`
  (the PII table) has no entity, repository, or endpoint anywhere in
  this codebase** — see `ClientCore`'s class javadoc for why that's a
  deliberate choice, not an oversight.
- **Client and Consignment create + delete** — same origin-only
  pattern; deleting a Client with existing Consignments correctly
  fails (409) rather than raising a raw foreign-key error.
  **Verified against live data** (section 8 of
  `docs/testing/test-results.md`).

What's not implemented yet:

- A proper secrets pipeline for the JWT keys — they currently ship as
  PEM files in `src/main/resources/keys/`, fine for running this
  project locally, not fine for anything beyond that. See the warning
  in that section.
- Any endpoint for the PII table (`client_contact`) — see the CRUD
  section below for why that's permanent, not a "not yet."
- A frontend view for Vehicle, Driver, Client, or Consignment — the
  backend for all four is fully built and verified, nothing in the
  Angular app calls any of it yet.

## CRUD, and how it maps onto the design's actual constraints

Every entity (Trip, Container, Vehicle, Driver, Client, Consignment)
now has full CRUD, but "full CRUD" here means something more specific
than four generic endpoints per entity — it means whatever operations
the design in
`../docs/design/vertical-fragmentation-design.md` actually permits:

- **Create** — origin-only. Only Depot may create a Master fragment
  record (`TripCreationService`, `ContainerCreationService`, etc.), the
  same single-leader rule that already governs everything else about
  Master fragments. A non-Depot instance gets a clean
  `OriginSiteOnlyException` (403) before the request ever reaches the
  database — the database's own `REVOKE` (`db/migrations/non-depot/V3`,
  `V5`) is the actual enforcement backstop, this is just a cleaner
  first line, same relationship as every other business rule enforced
  twice in this codebase (see `HandoverService`'s class javadoc for
  the same pattern).
- **Read** — every site, both roles (OPERATOR and AUDITOR). Unchanged
  from before.
- **Update** — deliberately does **not** exist as a generic
  "edit a trip" or "edit a container" endpoint. Master fields are
  immutable by design once created. What *does* exist is the
  domain-specific State-fragment transition each entity already had —
  `HandoverService.handOff` for Trip, `*RelocationService.relocate`
  for Container/Vehicle/Driver — which **is** the Update operation for
  the part of each entity that's actually meant to change. Client and
  Consignment have no State fragment and no such operation, because
  they have nothing that changes after creation.
- **Delete** — origin-only, same reasoning as Create. Deleting a
  Master record removes its State-fragment row first (where one
  exists), then the Master row itself. A delete blocked by a real
  foreign key (e.g. deleting a Client that still has Consignments)
  surfaces as a clean `EntityInUseException` (409) rather than a raw
  `DataIntegrityViolationException`.

**`client_contact` (PII) is the one deliberate exception to all of
this.** It has no entity, repository, controller, or any code path
anywhere in this codebase — not read, not write, regardless of role
or site. That's not a missing CRUD operation; it's a boundary this
project has decided never to cross at the application layer at all,
consistent with the schema-level decision (see the design doc) not to
even replicate that table to operational sites.

## Trip_Container, Milestone, and Incident

Three parts of the schema (`db/migrations/*/V4`) existed with no
backend code at all until this was built — not an oversight left in
the design doc's "not yet built" list indefinitely, but closed
outright:

- **`POST /api/trips/{tripId}/containers/{containerId}`** /
  **`DELETE .../containers/{containerId}`** — links or unlinks a
  Container onto a Trip's manifest (`TripContainerService`). Business
  rule: a site can only link a pair it currently holds **both halves
  of** — the same "must hold" reasoning as `HandoverService`, just
  checked against two entities' State fragments instead of one.
  `GET /api/trips/{tripId}/containers` lists the containers on a trip;
  `GET /api/containers/{containerId}/trips` lists the trips a
  container has been linked to. Idempotent at the database level
  (`skip_duplicate_trip_container`) — linking the same pair twice is a
  silent no-op, not an error.
- **`POST /api/trips/{tripId}/milestones`** / **`GET
  .../milestones`** — records a timestamped milestone
  (`MilestoneService`) against a trip. Same "must currently hold this
  trip" rule as `HandoverService` (reuses `InvalidHandoverException`
  rather than inventing a parallel exception for the identical rule).
- **`POST /api/trips/{tripId}/incidents`** / **`GET
  .../incidents`** — same shape as Milestone, for logging an
  incident's free-text description instead of a milestone type.

All three OPERATOR-to-write, either role to read — same pattern as
everywhere else. Unit tested and **verified against the live 4-site
cluster** — see section 9 of `docs/testing/test-results.md` for exactly
what was exercised. **None of this has a frontend yet** — see
`frontend/README.md`'s "Not yet built".

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
mvn spring-boot:run      # single instance, defaults to Depot -- requires
                          # SITE_ID=depot to be set if you don't use a
                          # profile (see below)
```

### Running all four sites at once

Each site is a Spring profile
(`src/main/resources/application-<site>.yml`) that fixes the port
this instance listens on, which local port its database
port-forward uses, and `site.id` together, so you don't have to set
three or four environment variables by hand and keep them consistent
across four terminals:

| Site | Backend port | DB port-forward |
|---|---|---|
| `depot` | 8080 | 5432 |
| `border` | 8081 | 5433 |
| `port` | 8082 | 5434 |
| `destination` | 8083 | 5435 |

**1. Start all four port-forwards together**, in their own terminal:
```bash
cd ../scripts
./port-forward-all-sites.sh
```
Leave that running. Ctrl+C there stops all four cleanly.

**2. Start each site's backend**, each in its own terminal:
```bash
SPRING_PROFILES_ACTIVE=depot DB_APP_USER_PASSWORD=change_me_later mvn spring-boot:run
SPRING_PROFILES_ACTIVE=border DB_APP_USER_PASSWORD=change_me_later mvn spring-boot:run
SPRING_PROFILES_ACTIVE=port DB_APP_USER_PASSWORD=change_me_later mvn spring-boot:run
SPRING_PROFILES_ACTIVE=destination DB_APP_USER_PASSWORD=change_me_later mvn spring-boot:run
```

Now all four sites are genuinely running as separate processes, each
with its own port, its own database connection, and — because
`JwtService` and every `*CreationService`/`*RelocationService` reads
`site.id` at runtime — each correctly enforcing its own site's
business rules with no code changes needed. This is the moment you
can actually verify things like "an OPERATOR token issued by Border
is rejected at Port" for real, rather than reasoning about it from
the code.

Log in via `POST /api/auth/login` (against whichever port that site
is running on) with one of the demo accounts above before calling
anything else; every endpoint except `/api/auth/login` itself
requires a valid token.
