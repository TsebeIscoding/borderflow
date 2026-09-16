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
