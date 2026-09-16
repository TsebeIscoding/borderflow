# BorderFlow

Cross-border container logistics tracking system. Built to demonstrate
a specific distributed-systems problem and its solution: how do four
physically separate sites (a depot, a border post, a port, and a
destination hub) each keep their own database, accept writes even with
zero connectivity to the others, and still end up agreeing on the
truth?

The answer implemented here: **vertical fragmentation** (splitting
each entity into a rarely-changing "Master" half and a
constantly-changing "State" half, each replicated differently) plus
**multi-leader logical replication with Lamport-timestamp conflict
resolution**. All of it is deployed on real Kubernetes and tested
against real failure conditions — not just described on paper.

This README walks through the whole thing from zero: standing up the
cluster, applying the schema, wiring up replication, and running the
backend, frontend, and every test — assuming you've never seen this
project before and nothing is currently running.

---

## What you'll end up with

Four Postgres instances (one per site) replicating to each other, a
Spring Boot backend (one instance per site) sitting on top, and an
Angular frontend talking to it — all running locally via Minikube.
Not deployed anywhere public; this is a local, from-scratch build.

---

## Prerequisites

Install these before starting:

| Tool | Used for | Check with |
|---|---|---|
| [Minikube](https://minikube.sigs.k8s.io/) | Local Kubernetes cluster | `minikube version` |
| `kubectl` | Talking to the cluster | `kubectl version --client` |
| Docker | Minikube's driver | `docker --version` |
| `psql` (PostgreSQL client) | Applying migrations, manual queries | `psql --version` (install with `sudo apt install postgresql-client` on Ubuntu/Debian) |
| Java 21 + Maven | Running the backend | `java -version`, `mvn -version` |
| Node.js 18.19+ / 20.11+ / 22+ and npm | Running the frontend | `node -v`, `npm -v` |
| `openssl` | Generating local dev RSA keys (usually preinstalled) | `openssl version` |

---

## Step 1 — Start the cluster

```bash
minikube start --cni=calico --memory=4096 --cpus=4
```

Calico can take a few minutes to become healthy the first time (it
pulls fairly large images) — this is normal, not a hang. Confirm it's
up before moving on:

```bash
kubectl get pods -n kube-system | grep calico
```

Both `calico-node` and `calico-kube-controllers` need to show
`Running`. If they sit in `Init`/`ContainerCreating` for a long time,
give it a few more minutes before assuming something's wrong.

---

## Step 2 — Deploy the four sites

**Set a real database password first.** Every site's manifest ships
with a placeholder — replace it in all four files before applying
anything:

```bash
cd borderflow
sed -i 's/CHANGE_ME_local_dev_only/YOUR_PASSWORD_HERE/' infra/k8s/depot/depot-db.yaml
sed -i 's/CHANGE_ME_local_dev_only/YOUR_PASSWORD_HERE/' infra/k8s/border/border-db.yaml
sed -i 's/CHANGE_ME_local_dev_only/YOUR_PASSWORD_HERE/' infra/k8s/port/port-db.yaml
sed -i 's/CHANGE_ME_local_dev_only/YOUR_PASSWORD_HERE/' infra/k8s/destination/destination-db.yaml
```

Then deploy:

```bash
kubectl apply -f infra/k8s/00-namespaces.yaml
kubectl apply -f infra/k8s/depot/depot-db.yaml
kubectl apply -f infra/k8s/border/border-db.yaml
kubectl apply -f infra/k8s/port/port-db.yaml
kubectl apply -f infra/k8s/destination/destination-db.yaml
```

Wait until all four are up:

```bash
kubectl get pods -A | grep -E 'depot|border|port|destination'
```

All four should show `1/1 Running`. Each site's Postgres container
runs its own baseline schema automatically on first start (the same
content as `db/migrations/*/V1`) — you don't need to apply `V1`
yourself.

---

## Step 3 — Apply the rest of the schema

Migrations are split into `db/migrations/depot/` (Depot only — it's
the sole site allowed to write Master fragments, and the only one with
the PII table) and `db/migrations/non-depot/` (Border, Port,
Destination — identical to each other). Full explanation:
`db/migrations/README.md`.

```bash
# Depot
kubectl exec -i -n depot depot-db-0 -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 < db/migrations/depot/V2__app_user_least_privilege.sql
kubectl exec -i -n depot depot-db-0 -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 < db/migrations/depot/V4__container_vehicle_driver_client_fragments.sql
kubectl exec -i -n depot depot-db-0 -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 < db/migrations/depot/V6__app_users.sql

# Border, Port, Destination
for site in border port destination; do
  kubectl exec -i -n $site ${site}-db-0 -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 < db/migrations/non-depot/V2__app_user_least_privilege.sql
  kubectl exec -i -n $site ${site}-db-0 -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 < db/migrations/non-depot/V3__enforce_trip_master_single_leader.sql
  kubectl exec -i -n $site ${site}-db-0 -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 < db/migrations/non-depot/V4__container_vehicle_driver_client_fragments.sql
  kubectl exec -i -n $site ${site}-db-0 -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 < db/migrations/non-depot/V5__lock_new_master_fragments.sql
  kubectl exec -i -n $site ${site}-db-0 -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 < db/migrations/non-depot/V6__app_users.sql
done
```

This creates the `app_user` least-privilege role (password:
`change_me_later` — change it in `V2` if you want something else
before running this), the Container/Vehicle/Driver/Client schema, and
the `app_users` table that backs login (seeded with a demo `operator1`
account everywhere and a demo `auditor1` account at Depot only —
both use password `ChangeMe123!`).

---

## Step 4 — Wire up replication

```bash
export DEPOT_DB_PASSWORD=YOUR_PASSWORD_HERE
export BORDER_DB_PASSWORD=YOUR_PASSWORD_HERE
export PORT_DB_PASSWORD=YOUR_PASSWORD_HERE
export DESTINATION_DB_PASSWORD=YOUR_PASSWORD_HERE

cd infra/k8s
./setup-replication-k8s.sh
```

Must end with all subscriptions confirmed enabled on all 4 sites. Then
wire up the Container/Vehicle/Driver/Client tables too:

```bash
./extend-replication-k8s.sh
```

Must end with `ALL SUBSCRIPTIONS CONFIRMED ENABLED AFTER SCHEMA EXTENSION`.

---

## Step 5 — Run the database-level tests

```bash
cd ../../scripts
./corridor-test.sh              # seeds a trip, walks it through all 4 sites, confirms convergence
./failure-recovery-test.sh       # kills a pod, confirms it catches back up via replication
./conflict-resolution-test.sh    # forces a conflicting write, confirms it's correctly rejected
./container-test.sh              # same corridor-style proof, for the Container fragment
```

If a script fails because leftover data exists from a previous run
(e.g. after redeploying the cluster), clean it up first:

```bash
./reset-test-data.sh
```

What each script actually proves, and how to read the output, is
explained in `scripts/README.md`.

---

## Step 6 — Run the backend

You need a way for your machine to reach Depot's database. In a
**separate terminal**, left running the whole time:

```bash
kubectl port-forward -n depot depot-db-0 5432:5432
```

If this fails to bind or the backend can't authenticate afterward,
something else on your machine is probably already using port 5432
(commonly a local Postgres install) — check with
`sudo ss -tlnp | grep 5432` and stop whatever's listed there besides
`kubectl`, or forward to a different local port instead
(`5433:5432`) and point the backend at that port via
`SPRING_DATASOURCE_URL` instead.

In another terminal:

```bash
cd backend
mvn clean test          # confirms it compiles and all tests pass
export SITE_ID=depot
export DB_APP_USER_PASSWORD=change_me_later
mvn spring-boot:run
```

Leave this running. Confirm it's actually working with a login call in
a third terminal:

```bash
curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"operator1","password":"ChangeMe123!"}'
```

Should return a JSON body with a `token` field. If instead you see
`permission denied` in the backend's logs, or a `401` here, see
`backend/README.md`'s Authentication and authorization section.

Then run the full auth flow test:

```bash
cd ../scripts
./auth-flow-test.sh 33333333-3333-3333-3333-333333333333
```

(Use whatever trip ID `corridor-test.sh` actually seeded, if you
changed it.) All 8 checks should pass — the important one is step 7:
an `AUDITOR` token must get `403` trying to hand off a trip.

To run a second site's backend instance alongside this one (to test
that an `OPERATOR` token from one site is rejected at another), repeat
Step 6 in new terminals with a different `SITE_ID`
(`border`/`port`/`destination`), a port-forward to that site's pod
instead, and a different local HTTP port
(`export SERVER_PORT=8081`) so it doesn't collide with Depot's
instance.

---

## Step 7 — Run the frontend

In a fourth terminal:

```bash
cd frontend
npm install
npm start
```

The first `npm install` can take several minutes depending on your
connection — let it finish rather than interrupting it, an interrupted
install can leave a corrupted `node_modules` that needs
`rm -rf node_modules package-lock.json && npm cache clean --force`
before retrying.

Once it says `Application bundle generation complete`, open
`http://localhost:4200` in a browser. You should land on `/login`.
Sign in with `operator1` / `ChangeMe123!`, and you should see the
manifest dashboard with whatever trips `corridor-test.sh` seeded.
Click into one to see the route rail and, if this site currently holds
that trip, a working handover form. Use the **Containers** link in the
header for the equivalent view of whatever `container-test.sh` seeded.

---

## Project layout

```
borderflow/
├── docs/
│   ├── design/       Full architecture rationale
│   └── testing/       Bugs found, fixes applied, full test results
├── infra/k8s/         Kubernetes manifests, one StatefulSet per site,
│                      replication setup scripts
├── db/migrations/      Versioned schema, split into depot/ and
│                      non-depot/ (see db/migrations/README.md for why)
├── backend/            Spring Boot, one instance per site
├── frontend/           Angular, one build per site
└── scripts/            The test scripts used in Step 5 and Step 6
```

Every folder above has its own `README.md` with more detail specific
to that layer.

---

## Architecture, in brief

Each tracked entity (Trip, Container, Vehicle, Driver, Client) is
split into:

- a **Master fragment** — static columns, written once at Depot,
  replicated read-only everywhere else
- a **State fragment** — volatile custody columns, written by whichever
  site currently holds the item, replicated to every other site and
  conflict-resolved by comparing Lamport timestamps if two sites ever
  write concurrently

Append-only event logs (`Handover`, `Milestone`, `Incident`,
`Trip_Container`) are horizontally partitioned by originating site
instead. One entity, `Client`, has a PII carve-out: `client_contact`
exists only at Depot and is never replicated anywhere else — not
access-controlled after the fact, structurally absent everywhere else.

Auth: two JWT roles. `OPERATOR` tokens are site-local — signed and
only ever trusted at the site that issued them. `AUDITOR` tokens are
cross-site and read-only — issued only at Depot, verified offline by
every other site using Depot's public key, no live call back to Depot
required. Full detail: `backend/README.md`.

Full rationale for all of the above:
`docs/design/vertical-fragmentation-design.md`.

---

## Current status

- ✅ Trip: schema, replication, backend (`Handover` use case), and
  frontend all built, deployed, and tested end to end
- ✅ Container: schema, replication, backend, and frontend all built,
  deployed, and tested end to end — same rigor as Trip, with two
  documented simplifications (no "Delivered" terminal status, no
  matching event-log row — see `backend/README.md`)
- 🚧 Vehicle / Driver: schema and backend built (read + relocate,
  same pattern as Container), unit tested — **not yet applied against
  live data or tested end to end, no frontend view**
- 🚧 Client / Consignment: schema and backend built (read-only, no
  relocation concept) — **not yet tested end to end, no frontend
  view**. `client_contact` (PII) deliberately has no entity, endpoint,
  or any code path anywhere in this project — see `ClientCore`'s class
  javadoc in the backend.
- ✅ Authentication: fully built and verified — login, both roles,
  cross-site trust, RBAC enforcement, all tested against the live
  cluster
- ❌ No secrets pipeline for the JWT keys (local-dev PEM files only),
  no token refresh

Full test results, including every bug found and fixed while building
this: `docs/testing/test-results.md`.

---

## Troubleshooting

A few things that came up repeatedly while building and testing this
project, in case they come up for you too:

- **`kubectl` says "no route to host"** — Minikube's VM/container
  likely stopped (e.g. after a reboot). Run `minikube start` again;
  it restarts the existing cluster without wiping data, but see the
  next point.
- **Namespaces are gone / pods from a previous session are missing**
  — if the cluster was fully deleted and recreated rather than just
  restarted, you're starting from Step 2 again with an empty cluster.
  `kubectl get namespaces` will tell you whether `depot`/`border`/
  `port`/`destination` still exist.
- **Backend can't authenticate to Postgres despite a correct
  password** — check for a local Postgres install competing for port
  5432 (`sudo ss -tlnp | grep 5432`); see Step 6.
- **A test script shows `0 rows` everywhere except one site** —
  replication isn't actually wired up yet; re-run Step 4.
- **A test script fails with a duplicate-key error** — leftover data
  from a previous run; run `scripts/reset-test-data.sh`.
- **Backend fails with "Port 8080 was already in use"** — an earlier
  `mvn spring-boot:run` didn't fully exit (common after Ctrl+C during
  a failed startup). Find and stop it: `sudo ss -tlnp | grep 8080`,
  then `kill <PID>` shown there, before retrying.
