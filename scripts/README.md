# Test scripts

Reproducible versions of the manual tests run against the live
Minikube cluster, documented with results in `../docs/testing/test-results.md`.
All three require `kubectl` pointed at the cluster and the mesh already
deployed and wired up (see `../infra/k8s/README.md`).

Run them in this order — each depends on state left behind by the one
before it.

## `corridor-test.sh`

Seeds a new trip at Depot and walks custody through Border, Port, and
Destination via the handover/trip_state pattern, then queries all four
sites and prints their view of the trip's final state side by side.
Confirms multi-leader replication converges correctly across real
Kubernetes Service DNS and cross-namespace networking.

Takes no arguments. Safe to re-run — it uses a fixed trip ID, so
re-running will fail on the initial insert if a previous run's data is
still present; drop the row from `trip_master`/`trip_state` at Depot
first if you need a clean re-run.

## `failure-recovery-test.sh`

Deletes a site's StatefulSet pod, waits for Kubernetes to recreate it,
then confirms the site's local `trip_state` has caught back up to the
converged value via logical replication once it's healthy again.

Takes one optional argument: the site to kill (`depot`, `border`,
`port`, or `destination`). Defaults to `border`. Requires
`corridor-test.sh` to have been run first, since it checks against the
trip ID that script seeds.

## `conflict-resolution-test.sh`

Forces a stale write (a lower `lamport_ts` than what's already landed)
directly at Port, after a newer write has already landed at Border.
Confirms the `resolve_trip_state_conflict` trigger rejects the stale
write, logs it to `conflict_exceptions`, and leaves Port's local state
untouched.

Polls Port for up to 30s until Border's write has actually replicated
there before attempting the stale write. **Found via testing**: without
this wait, the test was racy — on a freshly re-established replication
mesh, Port could still be behind when the "stale" write landed, in
which case it wasn't stale from Port's own local point of view and got
legitimately accepted. That's the trigger behaving correctly on
outdated information, not a database bug — but it made the test
non-deterministic, which needed fixing in the test itself.

Requires `corridor-test.sh` to have been run first.

## `reset-test-data.sh`

Deletes the fixed trip ID every other script here uses, across all
four sites, so `corridor-test.sh` can re-seed it cleanly. Needed
whenever a previous test run's data is still sitting in the database —
most commonly after redeploying the cluster (e.g. after a Minikube
restart) without also clearing old PVC-backed data, or after any test
script fails partway through.

Only ever deletes `trip_master` at Depot, then lets that deletion
replicate outward — deleting it directly at any other site would
recreate the exact silent single-leader-violation bug documented in
`../docs/testing/test-results.md` #5.

## `auth-flow-test.sh`

The one test that exercises the backend over real HTTP rather than
`psql` directly. Requires a backend instance actually running
(`mvn spring-boot:run`) rather than just the database — this is
testing `SecurityConfig`/`JwtService`/`@PreAuthorize`, none of which
exist at the database layer.

Confirms, in order: an unauthenticated request is rejected (401), a
demo OPERATOR account can log in and read, an OPERATOR can attempt a
handover (may correctly 409 if this site doesn't hold the trip — that's
`HandoverService`'s own business rule, not an auth failure), a demo
AUDITOR account (Depot only) can read but is blocked from handover with
a 403, and a garbage token is rejected.

**Step 7 is the one that matters most** — if an AUDITOR ever gets a
`200` on a handover instead of `403`, the role enforcement is broken
and needs fixing before anything else.

```bash
./auth-flow-test.sh <trip_id> [base_url]
# base_url defaults to http://localhost:8080
```

## What's not covered here yet

- Duplicate-event idempotency and the Master-fragment single-leader
  enforcement test were run manually rather than scripted — see
  `../docs/testing/test-results.md` for the exact commands used, they're
  short enough not to warrant their own script yet.
- No script exists for the Container/Vehicle/Driver/Client fragments
  added in `db/migrations/*/V4*` — those schema changes have been
  generated but not yet applied or tested on the live cluster.
