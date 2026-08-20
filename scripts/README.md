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

Requires `corridor-test.sh` to have been run first.

## What's not covered here yet

- Duplicate-event idempotency and the Master-fragment single-leader
  enforcement test were run manually rather than scripted — see
  `../docs/testing/test-results.md` for the exact commands used, they're
  short enough not to warrant their own script yet.
- No script exists for the Container/Vehicle/Driver/Client fragments
  added in `db/migrations/*/V4*` — those schema changes have been
  generated but not yet applied or tested on the live cluster.
