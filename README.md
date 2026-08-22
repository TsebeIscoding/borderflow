# BorderFlow

Cross-border container logistics tracking system, built as a portfolio
project to demonstrate distributed database correctness under failure
conditions: vertical fragmentation, multi-leader replication with
per-row ownership handover, and tested failure/recovery behavior on
real Kubernetes infrastructure — plus a working backend and frontend on
top of it.

Runs locally via Minikube. Not production-deployed.

---

## Table of contents

1. [Architecture](#architecture)
2. [Project layout](#project-layout)
3. [Status](#status)
4. [Getting started](#getting-started)
   - [1. Kubernetes cluster](#1-kubernetes-cluster)
   - [2. Database schema](#2-database-schema)
   - [3. Replication mesh](#3-replication-mesh)
   - [4. Verify it works](#4-verify-it-works)
   - [5. Backend](#5-backend)
   - [6. Frontend](#6-frontend)
5. [Design decisions, in depth](#design-decisions-in-depth)
6. [Bugs found during testing](#bugs-found-during-testing)
7. [Test results](#test-results)
8. [What's not built yet](#whats-not-built-yet)

---

## Architecture

Four operational sites — Depot, Border Post, Port Agent, Destination
Hub — each run their own local Postgres instance and must accept
writes with zero connectivity to the others. Each tracked entity
(Trip, Container, Vehicle, Driver, Client) is **vertically
fragmented** into:

- a **Master fragment** — static/descriptive columns, written once at
  an origin site (Depot), replicated read-only everywhere else
  (single-leader)
- a **State fragment** — volatile/custody columns, written by
  whichever site currently holds the item, replicated to every other
  site (multi-leader, conflict-resolved via Lamport timestamp
  comparison)

Append-only event logs (`Handover`, `Milestone`, `Incident`,
`Trip_Container`) are horizontally partitioned by originating site
instead, since there's nothing to vertically split in an append-only
row.

One entity, `Client`, has a PII carve-out: `client_contact` exists
only at Depot and is never replicated to operational sites at all —
not access-controlled after the fact, structurally absent everywhere
else.

Two services sit on top of the database, one deployed per site:

- **Backend** (Spring Boot) — reads the local, already-replicated
  Postgres instance and exposes it over REST; writes go through
  `HandoverService`, which enforces the two business rules the
  database can't (a site can only hand off a trip it holds; a
  `Delivered` trip is terminal).
- **Frontend** (Angular) — a manifest dashboard and a handover form,
  each build talking only to its own site's backend. There is no
  "global" view across sites by design — see
  [Design decisions](#design-decisions-in-depth).

Full rationale for all of the above:
`docs/design/vertical-fragmentation-design.md`.

---

## Project layout

```
borderflow/
├── docs/
│   ├── design/                Full architecture + fragmentation rationale
│   └── testing/                Bugs found, fixes applied, test results
├── infra/
│   └── k8s/                    Kubernetes manifests — one StatefulSet per
│                                site, replication mesh setup scripts
├── db/
│   └── migrations/              Flyway-style migration history, split into
│                                depot/ and non-depot/
├── backend/                     Spring Boot — one instance per site, Trip
│                                read endpoints + the Handover use case
├── frontend/                     Angular — one build per site, manifest
│                                dashboard + handover form
└── scripts/                     Reproducible test scripts for the corridor,
                                 failure-recovery, and conflict-resolution
                                 tests
```

Every folder above also has its own `README.md` with more detail
specific to that layer; this file is the single place that pulls all
of it together.

---

## Status

- ✅ Design finalized and documented
- ✅ 4 replication bugs found, diagnosed, and fixed
- ✅ Deployed and tested on real Kubernetes (Minikube): corridor
  replication, pod-deletion failure/recovery, concurrent-write
  conflict resolution, duplicate-event idempotency, single-leader
  enforcement on Master fragments (found broken during testing, fixed,
  re-verified)
- ✅ Backend: Trip read endpoints (`GET /api/trips`,
  `GET /api/trips/{id}`) and the `Handover` use case
  (`POST /api/trips/{tripId}/handover`), unit tested
- ✅ Frontend: manifest dashboard + trip detail with a working
  handover form
- 🚧 Container / Vehicle / Driver / Client fragments: schema +
  replication wiring generated, **not yet applied or tested on the
  live cluster**
- 🚧 JWT auth config not yet implemented — both backend and frontend
  are currently unauthenticated, local dev cluster only
- ⏳ Neither the backend nor the frontend has actually been compiled
  yet in the environment these docs were written in (no Maven Central
  / npm registry access) — run `mvn clean test` and
  `npm install && ng build` yourself before trusting either

---

## Getting started

Run these in order — each step depends on the one before it.

### 1. Kubernetes cluster

```bash
minikube start --cni=calico --memory=4096 --cpus=4
kubectl get pods -n kube-system | grep calico   # both calico-node and
                                                  # calico-kube-controllers
                                                  # must show Running
```

If Calico sits on `ContainerCreating`/`Init` for a while, it's almost
always a slow image pull from `quay.io`, not a real config problem —
give it a few minutes.

**Set real Postgres passwords before deploying.** Each site's Secret
manifest (`infra/k8s/<site>/<site>-db.yaml`) ships with a placeholder
password — replace it in all four files.

```bash
kubectl apply -f infra/k8s/00-namespaces.yaml
kubectl apply -f infra/k8s/depot/depot-db.yaml
kubectl apply -f infra/k8s/border/border-db.yaml
kubectl apply -f infra/k8s/port/port-db.yaml
kubectl apply -f infra/k8s/destination/destination-db.yaml

# Confirm all four are Running (only the LAST -n flag is respected by
# kubectl, so check all namespaces at once like this, not with
# multiple -n flags):
kubectl get pods -A | grep -E 'depot|border|port|destination'
```

### 2. Database schema

Schema is versioned in `db/migrations/`, split into `depot/` (run at
Depot only — it's the only site with write access to Master fragments
and the only one that gets the PII table) and `non-depot/` (run
identically at Border, Port, Destination). Applied via
`kubectl exec ... psql < V*.sql`, in version order, against each
site's pod. Full explanation and the exact migration history:
`db/migrations/README.md`.

### 3. Replication mesh

```bash
cd infra/k8s
chmod +x setup-replication-k8s.sh
./setup-replication-k8s.sh
# Must end with: ALL SUBSCRIPTIONS CONFIRMED ENABLED ON ALL 4 SITES
```

If you've also applied the V4+ Container/Vehicle/Driver/Client
migrations:

```bash
chmod +x extend-replication-k8s.sh
./extend-replication-k8s.sh
# Must end with: ALL SUBSCRIPTIONS CONFIRMED ENABLED AFTER SCHEMA EXTENSION
```

### 4. Verify it works

```bash
cd scripts
./corridor-test.sh              # seeds a trip, walks it through all 4 sites
./failure-recovery-test.sh       # kills a pod, confirms it catches back up
./conflict-resolution-test.sh    # forces a stale write, confirms it's rejected
```

Details on what each script proves: `scripts/README.md`. Full write-up
of every test result (including two run manually, not scripted):
`docs/testing/test-results.md`.

### 5. Backend

```bash
cd backend
mvn clean test          # confirm it compiles and the Handover tests pass
mvn spring-boot:run      # SITE_ID env var selects which site this instance is
```

One instance per site in a real run, each with a different `SITE_ID`
and datasource pointed at that site's own `*-db-0` pod. Details:
`backend/README.md`.

### 6. Frontend

```bash
cd frontend
npm install
npm start                # ng serve, proxies /api to localhost:8080
```

Requires the matching backend instance running first, with its
`SITE_ID` matching `environment.ts`'s `siteId` — otherwise the
handover form will never think this site holds any trip. Details:
`frontend/README.md`.

---

## Design decisions, in depth

**Why vertical fragmentation, not just "one big table per site."**
Master data (who's the driver, what's the container's size) and state
data (where is it right now) have completely different write
patterns — one is set once and barely changes, the other changes
constantly and needs to be writable from wherever the item physically
is. Splitting them lets each half use the replication strategy that
actually fits it, instead of forcing one compromise strategy onto
everything.

**Why the PII table has no counterpart at other sites, instead of
being access-controlled.** Even a perfectly enforced permission can be
misconfigured later. Not creating the table at all removes the
possibility entirely, rather than relying on someone remembering to
lock it down correctly forever.

**Why there's no cross-site "global" view in the frontend.** Every
site's frontend only talks to that site's own backend, which only
reads from that site's own local Postgres — already complete thanks to
replication, no cross-site call needed. Building a "see every site at
once" screen would mean picking one site to be a dependency for
everyone else's dashboard, which is exactly the single point of
failure the whole multi-leader design exists to avoid.

**Why `HandoverService` is so thin.** It only enforces what the
database structurally can't: same-request rules like "this site
doesn't hold this trip" or "this trip is already done." Everything
else — event idempotency, concurrent-write conflict resolution,
write-locking on Master fragments — is left to Postgres triggers on
purpose, because those rules have to hold even for writes the service
itself never made (ones arriving via replication from another site).

Full version of all of this, plus the auth design (site-local Spring
Security for operational roles, offline-verifiable RS256 JWT for
cross-site roles): `docs/design/vertical-fragmentation-design.md`.

---

## Bugs found during testing

Four replication bugs, each diagnosed from Postgres logs and fixed
permanently rather than worked around:

1. **Logical replication apply workers run with an empty
   `search_path`.** Trigger functions with unqualified table names
   worked locally but failed under replication. Fixed with
   `ALTER FUNCTION ... SET search_path = public` plus
   `ENABLE ALWAYS TRIGGER`.
2. **Subscription `DISABLE`/`ENABLE` needs independent verification**
   on both the publisher and subscriber side — `subenabled = t` has to
   be checked on both ends, not assumed.
3. **Subscription names must encode both publisher and subscriber**,
   or replication slot names collide once more than one subscriber
   connects to the same publisher.
4. **Default `max_logical_replication_workers` (4) is too low** for a
   full 4-site mesh where every site subscribes to every other site.
   Raised to 20.

Plus one real bug found via testing rather than code review — the
Master-fragment single-leader enforcement gap (see next section).

Full detail: `docs/testing/replication-bugs-found.md`.

---

## Test results

| # | Test | Result |
|---|---|---|
| 1 | Full corridor replication (Depot → Border → Port → Destination) | ✅ Pass |
| 2 | Pod-deletion failure/recovery | ✅ Pass |
| 3 | Concurrent-write conflict resolution (Lamport rejection) | ✅ Pass |
| 4 | Duplicate-event idempotency | ✅ Pass |
| 5 | Master-fragment single-leader enforcement | ⚠️ Found broken → ✅ Fixed and re-verified |

**#5 is the most interesting one.** A non-origin site (Border) was
able to locally `UPDATE trip_master`, and the write silently never
replicated anywhere — permanent, undetected divergence, since Master
fragments have no conflict-resolution trigger the way State fragments
do. Root cause: the cluster only had the `postgres` superuser
available, which bypasses every privilege check, so a `REVOKE` had
nothing to actually restrict. Fixed by creating a least-privilege
`app_user` role and revoking write access to Master fragments from it
at every non-origin site; re-tested connecting as `app_user` instead
of `postgres` to confirm the fix actually holds.

Full write-up with the exact commands and output at each step:
`docs/testing/test-results.md`.

---

## What's not built yet

- **JWT auth config** — the design calls for site-local Spring
  Security plus offline-verifiable RS256 JWTs for cross-site roles;
  neither exists yet. Both the backend and frontend are currently
  fully open. Don't point either at anything but a local dev cluster.
- **Container / Vehicle / Driver / Client fragments on the live
  cluster** — the schema and replication wiring are written
  (`db/migrations/*/V4*`, `V5`, `infra/k8s/extend-replication-k8s.sh`)
  but never applied or tested against the running Minikube cluster.
  The same 5-test pattern used for Trip should be repeated once they
  are.
- **Read endpoints/UI for anything other than Trip** — the backend and
  frontend both only know about trips right now.
- **Confirmed build** — `mvn clean test` (backend) and
  `npm install && ng build` (frontend) haven't actually been run in
  the environment these docs were written in. Do that before trusting
  either as working code.
