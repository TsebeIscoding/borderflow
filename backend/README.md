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
- **The `Handover` use case** — `POST /api/trips/{tripId}/handover`.
  Accepts a handover request, writes a `Handover` event, advances
  `TripState`, and enforces the business rules the database
  deliberately does *not* enforce (see below). Covered by unit tests in
  `src/test/java/com/borderflow/handover/HandoverServiceTest.java`.
- `application.yml` with per-site config placeholders (`SITE_ID`,
  datasource, and `spring.flyway.enabled: false` since migrations are
  applied via the `db/migrations` + `infra/k8s` flow, not by this
  service on boot)

What's not implemented yet:

- **`config/`** — Spring Security setup. Two auth mechanisms per the
  design: site-local Spring Security for operational roles (staff at
  that physical site), and offline-verifiable RS256 JWTs (via
  `spring-boot-starter-oauth2-resource-server`, already in `pom.xml`)
  for cross-site roles, so a site can validate a token without needing
  connectivity back to an auth server. Currently the `/api/trips/**`
  endpoints are unauthenticated — do not point this at anything but a
  local dev cluster until that's built.
- Read endpoints (e.g. `GET /api/trips/{tripId}`) — only the write path
  exists so far.

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

Not yet wired to a build/run script. Once the `handover` use case
exists, running one instance per site (four total, each with a
different `SITE_ID` and datasource pointed at its corresponding
`*-db-0` pod) is the intended local dev setup — mirroring how they'll
actually be deployed.
