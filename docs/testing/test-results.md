# Test results — Kubernetes deployment

Full corridor and failure-mode testing on the live 4-namespace Minikube
cluster (Depot / Border / Port / Destination), after resolving a Calico
CNI image-pull stall that initially blocked all pod scheduling.

## 1. Full corridor replication test — PASS

Seeded a trip at Depot, walked custody through Border -> Port ->
Destination via the `handover` event log and `trip_state` updates.
All four sites converged on `destination` / `Delivered` / `lamport_ts=4`.
Proves multi-leader replication is correct over real Kubernetes Service
DNS and cross-namespace networking, not just Docker's bridge network.

Script: `scripts/corridor-test.sh`

## 2. Pod-deletion failure/recovery test — PASS

Deleted `border-db-0` mid-mesh. The StatefulSet recreated it; once
`Running` again, `trip_state` had caught back up to the latest converged
value via logical replication with no data loss.

Script: `scripts/failure-recovery-test.sh`

## 3. Concurrent write conflict test — PASS

Forced a stale write (lower `lamport_ts`) at Port after a newer one had
already landed at Border. The `resolve_trip_state_conflict` trigger
rejected the write at the statement level (`UPDATE 0`), logged it to
`conflict_exceptions`, and left Port's local state untouched.

Script: `scripts/conflict-resolution-test.sh`

## 4. Duplicate event idempotency test — PASS

Re-inserted a `handover` event with an `event_id` that already existed.
`skip_duplicate_handover` absorbed it (`INSERT 0 0`), row count stayed
at 1 — no duplicate, no error.

## 5. Master fragment single-leader enforcement — FOUND BROKEN, FIXED, VERIFIED

Initial test: a non-origin site (Border) was able to locally `UPDATE
trip_master`, and the write silently never replicated anywhere —
permanent, undetected divergence from every other site, since no
conflict-resolution trigger exists on Master fragments (only State
fragments have one).

**Root cause:** Postgres logical replication subscribers are not
automatically read-only; only the connecting role's privileges determine
what can be written locally. The cluster was only using the `postgres`
superuser, which bypasses all privilege checks.

**Fix:** created a least-privilege `app_user` role
(`db/migrations/*/V2__app_user_least_privilege.sql`), then revoked
`INSERT, UPDATE, DELETE` on `trip_master` from `app_user` at Border,
Port, and Destination (`db/migrations/non-depot/V3__...sql`).

**Re-test, connecting as `app_user` instead of `postgres`:** Border's
write now fails with `permission denied for table trip_master`; Depot's
write still succeeds normally. Same pattern applied preemptively to the
new Master fragments added later (`V5`), rather than discovered as a
second bug.

## 6. Backend login flow — FOUND BROKEN, FIXED, VERIFIED

First real end-to-end test of the backend (not just unit tests):
starting the Spring Boot service and calling `POST /api/auth/login`
against the live cluster. Login failed with a confusing `401 "A valid
token is required for this request"` — misleading, because the actual
failure was a `500` from the database that Spring's default error
handling silently re-routed through `/error`, which then correctly got
blocked by the security filter chain (since `/error` isn't a permitted
path), masking the real error behind an unrelated-looking 401.

**Root cause, found in the backend's own log:**
`org.postgresql.util.PSQLException: ERROR: permission denied for table
app_users`. `V2`'s `GRANT ... ON ALL TABLES IN SCHEMA public TO
app_user` is a snapshot grant — it only covers tables that existed at
the moment `V2` ran. `app_users` didn't exist yet (it's created later,
in `V6`), so `app_user` never had any privileges on it at all.

**Fix:** added `GRANT SELECT ON app_users TO app_user;` to the end of
`V6` (both `depot` and `non-depot` variants). `SELECT` only —
`app_user` never needs to write to its own credentials table, since
there's no self-registration feature.

**Lesson for the rest of the schema:** any future migration that adds
a new table **after** `V2` needs either its own explicit `GRANT` (as
above) or a one-time `ALTER DEFAULT PRIVILEGES` so future tables
inherit `app_user`'s access automatically — `V4`'s Container/Vehicle/
Driver/Client fragments already re-grant at the end of that migration
for exactly this reason; `V6` had simply been missed.

**Re-test:** login succeeded, returned a valid JWT, and the full
`auth-flow-test.sh` sequence passed end to end, including the
important one — an `AUDITOR` token correctly receiving `403` on a
handover attempt while an `OPERATOR` token succeeds.

## 7. Container, Vehicle, Driver, Client, Consignment — full backend verification — PASS

Applied `V4`/`V5` and `extend-replication-k8s.sh` to the live cluster,
then verified every new entity's backend endpoints against real,
seeded data (not just unit tests):

- **Container**: `container-test.sh` (DB-level convergence across all
  4 sites) passed, then `GET /api/containers`, `GET
  /api/containers/{id}`, and `POST /api/containers/{id}/relocate` all
  verified over HTTP — including the business-rule rejection (409,
  wrong site) and AUDITOR correctly blocked (403).
- **Vehicle**: seeded directly via SQL, then `GET /api/vehicles`,
  `GET /api/vehicles/{id}`, and `POST /api/vehicles/{id}/relocate`
  verified — successful relocation (`lamportTs` 1→2), a repeat
  relocation attempt from the now-wrong site correctly rejected (409),
  and AUDITOR correctly blocked (403).
- **Driver**: same sequence as Vehicle, same results.
- **Client / Consignment**: read-only endpoints verified against the
  data `container-test.sh` had already seeded (a client and a
  consignment) — both return correctly, no PII (`client_contact`) is
  reachable through any endpoint, by design.

One real bug found and fixed along the way, unrelated to any of the
above: a malformed Javadoc comment in `TripSummaryResponse.java`
containing the literal sequence `*/` mid-sentence (inside a file path
reference), which Java's compiler interpreted as the comment's closing
delimiter — everything after it was parsed as code, causing a
compile error. The same mistake was repeated in three new Container
files while writing them and caught by a repo-wide sweep before they
were ever tested. Also found: a Spring dependency-injection ambiguity
in `JwtKeyProvider` (two constructors, neither marked `@Autowired`,
so Spring couldn't tell which one to use) and a multi-catch clause in
`JwtService` listing a class alongside its own superclass, which Java
rejects as redundant. All three are one-time mistakes in how the code
was written, not design flaws — fixed, and confirmed not to recur
anywhere else in the codebase via repo-wide greps before every
subsequent package was built.

## 8. Full CRUD across all six entities — FOUND BROKEN (Delete), FIXED, VERIFIED

Manually tested Create and Delete for Trip, Container, Vehicle,
Driver, Client, and Consignment against the live cluster for the
first time.

**Create passed cleanly for all six** — including chained creation
(Client → Consignment referencing it → Container referencing that
consignment), each returning correct data with the right defaults
(`AtOrigin`/`Available`, `lamportTs: 1`).

**Delete surfaced a real bug**: deleting a Client that still had a
Consignment referencing it (via the `consignment.client_id` foreign
key) should have returned a clean `409` from `EntityInUseException`.
Instead it returned a confusing `401 "A valid token is required"` —
misleading in exactly the same way the `app_users` grant bug was
earlier (see #6): a real server-side failure was getting silently
rerouted through Spring's `/error` handling, which then correctly
rejected it as unauthenticated, masking the actual problem.

**Root cause, found in the backend's own log**: the
`DataIntegrityViolationException` was thrown at the servlet layer,
**not** inside `ClientCreationService.delete()`'s `try/catch` block.
`repository.deleteById()` doesn't execute the `DELETE` statement
immediately — Hibernate defers flushing to the database until the
transaction commits, which happens *after* the `@Transactional`
method has already returned. The foreign-key violation only
surfaced at commit time, outside anywhere our code could catch it,
so it propagated as an unhandled exception.

**Fix**: call `.flush()` on the repository immediately after
`deleteById()`, forcing the `DELETE` (and its constraint check) to
execute right there, inside the `try` block, where it can actually be
caught. Applied to all six `*CreationService.delete()` methods for
consistency, even though only `ClientCreationService` currently has a
real foreign key to violate (`consignment.client_id`) — the other
five would have the same latent bug the moment any future migration
adds a constraint referencing them.

**Re-test**: created a fresh Client and a Consignment referencing it,
attempted to delete the Client — correctly rejected with a clean
`409` and the expected message. Deleted the Consignment first, then
the Client — both succeeded (`204`). Delete for Container, Vehicle,
Driver, Trip, and Consignment (with nothing referencing them) all
verified working (`204`) earlier in the same session.


## 9. Trip_Container, Milestone, Incident — live backend verification — PASS

These three parts of the schema were built after sections 1-8 and
verified the same way: four separate backend processes (one per site)
against the live Minikube cluster, with real HTTP requests. Unit tests
(47 total, 9 of them new for these three services) passed first; this
section covers what the unit tests cannot — the real schema, real
permissions, real replication.

Test data: Trip `33333333-...` and Container `44444444-...`, both held
at Port at the time of testing.

| Check | Expected | Result |
|---|---|---|
| Link at Depot (holds neither entity) | 409 | 409, message names where trip, container and this site are |
| Link at Port (holds both) | 204 | 204 |
| Read back, both directions | container under trip, trip under container | both correct |
| Duplicate link | clean response, no second row | 204, row count stayed 1 |
| Milestone at Port | created and listed | created and listed |
| Incident at Port | created and listed | created and listed |
| Milestone at Depot for a trip held at Port | 409 | 409 |
| AUDITOR POST milestone / link | 403 | 403 / 403 |
| AUDITOR GET milestones | 200 | 200 |
| Border OPERATOR token at Port | 401 | 401 |
| Unknown trip (milestone) / unknown container (link) | 404 | 404 / 404 |
| Blank `milestoneType` | 400 | 400 |
| Insert replication | row present at all 4 sites | 1 / 1 / 1 at every site |
| Unlink at Port | 204, row gone everywhere | 204, count 0 at all 4 sites |

**No bugs were found in the new code on this run.** The first
`mvn clean test` compiled cleanly and the backends booted against the
real schema, so the composite-key `TripContainer` mapping and the
`app_user` grants on the new tables worked without changes.

**One false alarm worth recording**: the first link attempt returned a
confusing `401 "A valid token is required"`. The token was fine — the
shell variables holding the trip and container IDs were empty, so the
request went to `/api/trips//containers/`, which matches no route and
fell through to Spring's `/error` handling (the same masking pattern as
sections 6 and 8). Setting the variables fixed it. Lesson: when a
confusing 401 appears, check the request URL as well as the backend log.

**Not separately exercised live**: Incident's negative cases (wrong
site, unknown trip). Incident has the same shape and rules as
Milestone and is covered by `IncidentServiceTest`, but only its
success path and replication were run against the cluster.

## 10. Container → Consignment foreign key — FOUND MISSING, FIXED, VERIFIED

**Found during frontend testing.** The Consignments page showed "No
consignments on this site's manifest yet" while the test container
`TEST-CONT-0001` still pointed at consignment `66666666-…`. The API
confirmed it (`GET /api/consignments` returned `[]`), and a row count
showed `consignment = 0, container_master = 1` at all four sites.

**Root cause.** The design doc specifies `Container_Master(...,
consignment_id FK, ...)`, but `V4` declares `container_master` before
`consignment` and left `consignment_id` as a bare `UUID NOT NULL`. Only
`consignment_client_id_fkey` existed (confirmed against `pg_constraint`).
So a Consignment could be deleted while a Container still referenced it
— the earlier delete tests (section 8) did exactly that to the test
consignment — and a Container could be created against a consignment
that never existed. Same class of gap as the Client → Consignment case
fixed in section 8, just on the other relationship.

**Fix.**
- `V7__container_consignment_fk.sql` (depot and non-depot): adds
  `container_master_consignment_id_fkey`. The data had to be repaired
  first — the missing consignment was restored under its original ID
  and client — because the constraint cannot be added while a row
  dangles. Applied at all four sites (`ALTER TABLE` at each).
- `ContainerCreationService.create()` now checks the consignment exists
  and throws `ConsignmentNotFoundException` (404), the same pattern
  `ConsignmentCreationService` uses for clients, instead of leaving the
  database to reject it.
- One new unit test (48 total, all passing).

**Verification (live, Depot backend):**

| Check | Expected | Result |
|---|---|---|
| Delete a consignment a container references | 409 | 409 `EntityInUseException` (the key fired inside `flush()`) |
| Create a container for a nonexistent consignment | 404 | 404, "No consignment found with id 9999…" |
| Create a container for the real consignment | 200 | 200, new container at `depot`, `AtOrigin`, `lamportTs: 1` |

**A second instance of the masked-401 pattern, worth recording.** Before
the Depot backend was restarted on the new code, the nonexistent-
consignment create returned the generic `401 "A valid token is
required"`. The backend log showed the real cause: the insert was only
flushed at transaction commit (`commitTransactionAfterReturning`), where
the foreign-key violation was thrown outside any `try/catch`. Checking
for the row's existence before saving avoids that path entirely; the
restart onto the new code then returned the clean 404. Same lesson as
sections 6 and 8: look at the backend's own log when a 401 appears where
it shouldn't.

**Not separately exercised live:** replication of a container created
after the constraint was added, and the non-depot sites' copy of the
constraint beyond the `ALTER TABLE` succeeding. Replication apply does
not fire foreign-key checks, so this is not expected to differ from
before, but it has not been confirmed.


## 11. Delivery points — schema, replication, backend — PASS

Towns such as Maseru and Butha-Buthe are **delivery points inside the
Destination site**, not separate sites, so the four-site corridor and
the replication mesh are unchanged. `delivery_point` is a Master
fragment: written only at Depot, replicated read-only to the other three
sites. Trips and consignments each carry a nullable `delivery_point_id`
(nullable only because rows created before V8 needed backfilling; the
application requires one on every new row).

**Schema and replication (live, all four sites):**

| Check | Expected | Result |
|---|---|---|
| V8 applied at Border, Port, Destination, then Depot | no errors, Depot seeds 10 towns | clean, `INSERT 0 10` at Depot |
| `extend-replication-v8.sh` | all subscriptions enabled afterwards | all 16 subscriptions `t` |
| `delivery_point` row count at every site | 10 | 10 / 10 / 10 / 10 |
| Write to `delivery_point` as `app_user` at Border | refused | `permission denied for table delivery_point` |
| V9 backfill at Depot | existing trips and consignments pointed at Maseru | `UPDATE 2` for each table |
| Backfill replicated | same counts at all four sites | 2 trips, 2 consignments at every site |
| Dangling references (trip or consignment pointing at a missing delivery point) | 0 | 0 / 0 at every site |

**Ordering note.** Postgres does not replicate DDL, so V8 was applied at
the three non-Depot sites *before* Depot. Had Depot sent rows carrying
the new column first, the other sites would have failed to apply them
and their subscriptions would have stalled. The V9 backfill was
deliberately run only after the table had replicated everywhere.

**Backend (reported passing; individual response bodies were not
recorded here):** 53 unit tests pass, 5 of them new. Against the live
cluster: a trip created without a delivery point is rejected (400), one
with a nonexistent delivery point returns a clean 404, and a non-Depot
backend lists the replicated towns.

**Not yet exercised:** the browser UI for delivery points (the dropdown
on the trip and consignment create forms, and the town shown on the trip
page), and creating and deleting delivery points through the API. Delete
relies on the same flush-then-catch pattern as the other Master fragments
and is covered only by a unit test for the non-Depot rejection.


## 12. Consignment details — schema, backend, live verification — PASS

A consignment used to be only a client, a description and a delivery
point. V10 adds the details freight work needs: a backend-issued
reference number (`CON-2026-0001`, from a Depot-only sequence), cargo
type (General, Refrigerated, Hazardous, Bulk), total weight, package
count, optional declared value with currency, customs reference,
handling notes, a business delivery location (free text, business
addresses only) and an expected delivery date. They are Master data like
the rest of the consignment: set at creation, never edited. All columns
are nullable only because rows created before V10 have none of them;
the application requires cargo type, weight and package count.

**Schema (live, all four sites):**

| Check | Expected | Result |
|---|---|---|
| V10 at Border, Port, Destination, then Depot | no errors; Depot also creates the sequence | clean |
| New columns present at every site | 10 of 10 | 10 / 10 / 10 / 10 |
| `UPDATE ... SET total_weight_kg = -5` | refused by the CHECK constraint | `violates check constraint "consignment_total_weight_kg_check"` |
| V11 backfill of reference numbers | existing consignments numbered from the sequence | `UPDATE 2`, same two numbers at all four sites |

**Backend, via `scripts/consignment-details-test.sh` against the live
cluster (19 checks, all passing):**

| Check | Expected | Result |
|---|---|---|
| Valid create | 200, reference issued | 200, `CON-2026-0003` (follows the two backfilled numbers) |
| Cargo type and package count echoed | as sent | as sent |
| Unknown cargo type | 400 | 400 |
| Declared value without currency | 400, message mentions "together" | 400, correct message |
| Zero weight | 400 | 400 |
| Missing package count | 400 | 400 |
| Rejected requests leave no rows | only the valid request counted | confirmed |
| Created row with details at all four sites | present everywhere | 1 at every site |
| Delete | 204, row gone at all four sites | 204, 0 at every site |

The script deletes its own test row, so it can be re-run without leaving
data behind. Reference numbers are not reused, so a deleted test row
leaves a gap in the sequence; that is normal for reference numbers.
53 unit tests became 56 (three new consignment tests); reported passing.

**False alarm worth recording.** The first live attempt returned 200 for
requests that should have been rejected, and the response contained only
the old four fields. The Depot backend had not been restarted onto the
new code, so none of the new validation applied, and four consignments
with no details were created and had to be deleted. Lesson: after a code
change, check that the response *shape* contains the new fields before
trusting any status code. A second, separate slip: the first version of
the script mistyped a variable name (`$PGW` for `$PGPW`), so its database
checks failed with "unbound variable" instead of testing anything. That
was a script bug, not a system bug, and it was fixed and re-run clean.

**Not yet exercised:** the browser UI for consignment details (the
two-column create form and the richer list), and the same details shown
from a non-Depot site.


## 13. Trip assembly: vehicle and driver on a trip — schema, backend — PASS

A trip's **current vehicle and driver** are volatile (a driver is swapped,
a truck breaks down), so under the project's vertical fragmentation they
belong in the trip's State fragment (`trip_state.vehicle_id` /
`driver_id`, V12), resolved by Lamport timestamp like position and
status. The history of who was assigned or released is an append-only
event log (`trip_assignment`), deduped by primary key like handover,
milestone and incident.

**Schema and replication (live, all four sites):**

| Check | Expected | Result |
|---|---|---|
| V12 applied at Border, Port, Destination, then Depot, back to back | no errors | ten statements each, clean |
| `extend-replication-v12.sh` | all subscriptions enabled afterwards | all 16 `t` |
| New columns on `trip_state` and the new table at every site | 2 columns, 1 table | 2 / 1 at every site |
| Event inserted at Border | present at all four sites | 1 / 1 / 1 / 1 |
| `delete` and `update` on `trip_assignment` as `app_user` | refused (append-only) | `permission denied` for both |

**Design note: no unique index on assignments.** A unique index on a
replicated table would make two simultaneous assignments at different
sites fail while being *applied*, which halts replication. "One active
trip per driver or vehicle" is instead checked by the application at the
site that holds the trip, whose "must hold" rule keeps concurrent
assignment from happening in normal operation. (An earlier proposal in
this project promised database-level enforcement; this corrects it.)

**Backend, via `scripts/trip-assembly-test.sh` against the live cluster
(reported: all checks passing):** assign, swap and release a vehicle;
assign a driver; a vehicle or driver already on another active trip is
refused with a 409; unknown IDs return 404; an auditor cannot assign but
can read the history; a container can be linked to only one active trip;
the history rows come back in order (vehicle 1 assigned, released, vehicle
2 assigned...); and the assignments and trip state replicate to all four
sites. The script removes everything it creates, including its history
rows, so it can be re-run. 68 unit tests (11 new for assembly, 1 for the
one-active-trip container rule); reported passing.

**False alarm worth recording.** The first live run failed 31 of 50
checks: every new endpoint returned 401 and the container rule did not
apply. The Depot backend had not been restarted onto the new code, so the
routes did not exist and Spring's error handling answered with the
generic 401 (the masked-401 pattern of sections 6 and 8). Setup and
cleanup still worked, which also confirmed the script's cleanup path.
After the restart the same script passed.

**Not yet built or exercised:** whole-trip handover (moving a trip's
containers, vehicle and driver together with it), and any UI for
assignment, containers, milestones and incidents on the trip page.
