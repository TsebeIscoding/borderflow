# Documentation

## `design/`

`vertical-fragmentation-design.md` — the full architecture: why each
tracked entity (Trip, Container, Vehicle, Driver, Client) is split into
a Master fragment (static columns, single-leader, written once at an
origin site) and a State fragment (volatile columns, multi-leader,
conflict-resolved via Lamport timestamp comparison), why event logs
(Handover, Milestone, Incident, Trip_Container) are horizontally
partitioned by originating site instead, and the reasoning behind the
PII carve-out on `Client_Contact` (Depot-only, never replicated to
operational sites).

Read this first if you want the "why," not just the "what."

## `testing/`

- `replication-bugs-found.md` — four real replication bugs found while
  building the mesh, each diagnosed from Postgres logs and permanently
  fixed rather than worked around (empty `search_path` on apply
  workers, subscription enable/disable verification, subscription
  naming collisions, insufficient `max_logical_replication_workers` for
  a full 4-site mesh).
- `test-results.md` — the five tests run against the live Kubernetes
  deployment: corridor replication, pod-deletion failure/recovery,
  concurrent-write conflict resolution, duplicate-event idempotency,
  and Master-fragment single-leader enforcement (found broken during
  testing, fixed, and re-verified — see #5 for the full story).

Together these two files are the evidence that the design in `design/`
actually holds up under real failure conditions, not just on paper.
