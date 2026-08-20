# BorderFlow — Kubernetes deployment

One StatefulSet per site (Depot, Border, Port, Destination), one
namespace per site, a full-mesh Postgres logical replication setup
across all four, wired up entirely through `kubectl` and Kubernetes
Service DNS. This is the real target infrastructure — earlier
docker-compose testing was a stand-in for this, not the other way
around.

## Prerequisites

- Minikube, with enough headroom for four Postgres instances (and later,
  four Spring Boot JVMs):
  ```bash
  minikube start --cni=calico --memory=4096 --cpus=4
  ```
- Calico CNI healthy before doing anything else:
  ```bash
  kubectl get pods -n kube-system | grep calico
  ```
  Both `calico-node` and `calico-kube-controllers` must show `Running`.
  If they're stuck on `ContainerCreating` or `Init`, it's almost always
  a slow/flaky image pull from `quay.io`, not a real config problem —
  give it a few minutes before troubleshooting further.
- **Set real Postgres passwords before deploying.** Each site's Secret
  manifest (`<site>/<site>-db.yaml`) ships with
  `POSTGRES_PASSWORD: CHANGE_ME_local_dev_only` — replace that with an
  actual value in all four files, then export the matching env vars for
  `setup-replication-k8s.sh` (step 4 below), e.g.:
  ```bash
  export DEPOT_DB_PASSWORD=... BORDER_DB_PASSWORD=... PORT_DB_PASSWORD=... DESTINATION_DB_PASSWORD=...
  ```
  These are only ever used for in-cluster Postgres-to-Postgres
  replication connections — nothing here is internet-facing (see notes
  at the bottom of this file) — but don't leave the placeholder in a
  real deployment.

## 1. Deploy the namespaces and StatefulSets

```bash
kubectl apply -f 00-namespaces.yaml
kubectl apply -f depot/depot-db.yaml
kubectl apply -f border/border-db.yaml
kubectl apply -f port/port-db.yaml
kubectl apply -f destination/destination-db.yaml
```

## 2. Wait for all four pods to be Running

```bash
kubectl get pods -A | grep -E 'depot|border|port|destination'
```
(`kubectl get pods -n depot -n border -n port -n destination` does NOT
work as expected — only the last `-n` flag is respected, so that
command silently only checks one namespace.)

Each should show `<site>-db-0` at `1/1 Running`. First run can take a
minute or two while `postgres:18` pulls. If a pod sits `Pending` or
`ContainerCreating` for a long time:
```bash
kubectl describe pod -n depot depot-db-0
kubectl get events -n depot --sort-by='.lastTimestamp'
```
Read the Events at the bottom — most likely causes at this stage are
unbound PVCs (check `kubectl get pvc -n depot`) or the same slow image
pull issue Calico itself can hit.

## 3. Apply the database schema

Schema is not embedded in these manifests as a one-shot init script —
it's versioned in `../../db/migrations/`, split into `depot/` and
`non-depot/` sets. See `../../db/migrations/README.md` for why, and
apply it before continuing to step 4.

## 4. Wire up the replication mesh

```bash
chmod +x setup-replication-k8s.sh
./setup-replication-k8s.sh
```
Must end with `ALL SUBSCRIPTIONS CONFIRMED ENABLED ON ALL 4 SITES`. This
creates the publications/subscriptions for the original Trip/Handover
schema (migration V1).

If you've since applied the Container/Vehicle/Driver/Client fragments
(migration V4+), also run the extension script:
```bash
chmod +x extend-replication-k8s.sh
./extend-replication-k8s.sh
```
Must end with `ALL SUBSCRIPTIONS CONFIRMED ENABLED AFTER SCHEMA EXTENSION`.

## 5. Verify it actually works

Don't hand-write test commands against this cluster — use the scripts
in `../../scripts/` (`corridor-test.sh`, `failure-recovery-test.sh`,
`conflict-resolution-test.sh`), documented in `../../scripts/README.md`.
Results from the last run against this exact infrastructure are in
`../../docs/testing/`.

## 6. Tear down

```bash
kubectl delete namespace depot border port destination
```
This deletes the PVCs too — all data is lost. There's no equivalent of
"delete but keep data"; if you need to preserve state, `pg_dump` out of
each pod first.

## Notes on what's here vs. what's not

- **Resource limits** (`cpu`/`memory` requests/limits) are set
  conservatively per pod in each `<site>-db.yaml` — tuned for a 4-site
  mesh running on a single Minikube node, not for production load.
- **Secrets** (`<site>-db-secret`) are created inline in each manifest
  for local dev convenience. Do not reuse these values anywhere real —
  this was never intended to be internet-facing.
- **No Ingress/LoadBalancer** is defined. Every site's Postgres is only
  reachable from inside the cluster
  (`<site>-db.<site>.svc.cluster.local`) or via `kubectl exec` /
  `kubectl port-forward` from your machine. That matches the design's
  premise: sites are physically separate, low-connectivity locations,
  not services meant to be publicly exposed.
