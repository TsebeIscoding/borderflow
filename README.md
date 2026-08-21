# BorderFlow

Cross-border container logistics tracking system, built as a portfolio
project to demonstrate distributed database correctness under failure
conditions: vertical fragmentation, multi-leader replication with
per-row ownership handover, and tested failure/recovery behavior on real
Kubernetes infrastructure.

Runs locally via Minikube. Not production-deployed.

## Project layout

```
borderflow/
├── docs/
│   ├── design/            Full architecture + fragmentation rationale
│   └── testing/           Bugs found, fixes applied, test results
├── infra/
│   └── k8s/                Kubernetes manifests -- one StatefulSet per
│                            site (Depot / Border / Port / Destination),
│                            replication mesh setup scripts
├── db/
│   └── migrations/          Flyway-style migration history, split into
│                            depot/ and non-depot/ (DDL is not replicated
│                            by Postgres logical replication -- see
│                            db/migrations/README.md)
├── backend/                 Spring Boot -- one service instance deployed
│                            per site, Trip read endpoints + the
│                            Handover use case implemented and unit
│                            tested, auth config not yet implemented
├── frontend/                 Angular UI -- one build deployed per site,
│                            manifest dashboard + handover form, talks
│                            only to that site's own backend
└── scripts/                 Reproducible test scripts for the corridor,
                             failure-recovery, and conflict-resolution
                             tests documented in docs/testing/
```

## Architecture summary

Four operational sites (Depot, Border Post, Port Agent, Destination Hub)
each run their own local Postgres instance and must accept writes with
zero connectivity to the others. Each tracked entity (Trip, Container,
Vehicle, Driver, Client) is **vertically fragmented** into:

- a **Master fragment** -- static/descriptive columns, written once at
  an origin site, replicated read-only everywhere else (single-leader)
- a **State fragment** -- volatile/custody columns, written by whichever
  site currently holds the item, replicated to every other site
  (multi-leader, conflict-resolved via Lamport timestamp comparison)

Append-only event logs (`Handover`, `Milestone`, `Incident`,
`Trip_Container`) are horizontally partitioned by originating site
instead, since there's nothing to vertically split in an append-only
row. Full rationale: `docs/design/vertical-fragmentation-design.md`.

## Status

- ✅ Design finalized and documented
- ✅ 4 replication bugs found, diagnosed, and fixed (`docs/testing/replication-bugs-found.md`)
- ✅ Deployed and tested on real Kubernetes (Minikube): corridor
  replication, pod-deletion failure/recovery, concurrent-write conflict
  resolution, duplicate-event idempotency, single-leader enforcement on
  Master fragments (`docs/testing/test-results.md`)
- 🚧 Container / Vehicle / Driver / Client fragments: schema + replication
  wiring generated (`db/migrations/*/V4*`, `infra/k8s/extend-replication-k8s.sh`),
  not yet applied/tested on the live cluster
- ✅ Spring Boot service: Trip read endpoints (`GET /api/trips`,
  `GET /api/trips/{id}`) and the `Handover` use case implemented and
  unit tested (`POST /api/trips/{tripId}/handover`) — accepts a
  handover request, writes the event, advances trip state, enforces
  the business rules the database doesn't (site-holds-trip, terminal
  status). See `backend/README.md` for what it does vs. leaves to the
  database's own triggers.
- ✅ Angular frontend: manifest dashboard + trip detail with a working
  handover form, reading and writing against the endpoints above. See
  `frontend/README.md` for why there's no cross-site "global" view by
  design.
- 🚧 JWT auth config not yet implemented — both backend and frontend
  are currently unauthenticated, local dev cluster only

## Getting started

See `infra/k8s/README.md` for cluster setup and deployment, then
`db/migrations/README.md` for applying schema, then run the scripts in
`scripts/` in order (corridor → failure-recovery → conflict-resolution)
to reproduce the documented test results.
