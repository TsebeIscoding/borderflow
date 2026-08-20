# Database migrations

DDL is **not** replicated by Postgres logical replication — only DML
(row-level changes) travels over a publication/subscription. That means
every site needs its own independent Flyway migration history, run
against its own local database, rather than one shared history applied
once. This folder is split accordingly:

```
migrations/
├── depot/         Run at the Depot site only
└── non-depot/     Run identically at Border, Port, and Destination
```

## Why the split, specifically

Depot is the **origin site** for every Master fragment (`trip_master`,
`container_master`, `vehicle_profile`, `driver_profile`, `client_core`,
`consignment`) — see
`../../docs/design/vertical-fragmentation-design.md` for the fragmentation
rationale. That has two consequences for migrations:

1. **Only Depot keeps write access** to Master fragments. Border, Port,
   and Destination get a `REVOKE` migration locking them to read-only on
   those tables (`non-depot/V3`, `non-depot/V5`) — Depot has no
   equivalent file, since revoking its own write access would break the
   whole design.
2. **Only Depot gets `client_contact`**, the PII fragment. It's created
   in `depot/V4` and deliberately has no counterpart anywhere in
   `non-depot/` — it's never replicated to operational sites at all, by
   design, not just access-controlled after the fact.

Everything else (`V1`, `V2`, and the non-PII columns of `V4`) is
byte-for-byte identical between the two folders.

## Migration history

| Version | Depot | Non-depot | What it does |
|---|---|---|---|
| V1 | ✅ | ✅ | Trip Master/State fragments, Handover event log, `conflict_exceptions` table, dedup + conflict-resolution triggers |
| V2 | ✅ | ✅ | Creates `app_user`, a least-privilege role. Found necessary during testing — the cluster only had the `postgres` superuser available, which bypasses every privilege check, meaning `REVOKE`-based enforcement had nothing to actually restrict |
| V3 | — | ✅ | `REVOKE` on `trip_master` from `app_user`. Fixes a real bug found in testing: without this, a non-origin site could silently write to a Master fragment and the change would never replicate anywhere — permanent, undetected divergence, since Master fragments have no conflict-resolution trigger the way State fragments do |
| V4 | ✅ (+ PII) | ✅ | Container / Vehicle / Driver / Client Master + State fragments, Milestone / Incident / Trip_Container event logs, matching dedup + conflict-resolution triggers |
| V5 | — | ✅ | Same `REVOKE` pattern as V3, applied preemptively to the new Master fragments from V4, rather than waiting to rediscover the same bug per table |

Full write-up of the V3 bug — how it was found, why it happened, and how
the fix was verified — is in `../../docs/testing/test-results.md`.

## Applying migrations

Using the Flyway CLI, one config file per site pointed at that site's
own database and the matching folder:

```bash
# Depot
flyway -url=jdbc:postgresql://localhost:5432/borderflow \
       -user=postgres -password=<depot-secret> \
       -locations=filesystem:./depot migrate

# Border / Port / Destination — same command, swap connection details
# and use ./non-depot instead
flyway -url=jdbc:postgresql://localhost:5432/borderflow \
       -user=postgres -password=<border-secret> \
       -locations=filesystem:./non-depot migrate
```

In the current Kubernetes setup, these haven't yet been wired into an
automated `flyway migrate` step — they were applied manually via
`kubectl exec ... psql < V*.sql` during testing, in version order,
before running `../../infra/k8s/setup-replication-k8s.sh` /
`extend-replication-k8s.sh`. Automating this (e.g. an init container
running Flyway before the app container starts) is a reasonable next
piece of infrastructure work, not yet done.

## Adding a new migration

1. Decide: does it touch a Master fragment, a State fragment, or an
   event log? That determines whether it needs a `REVOKE` companion
   migration in `non-depot/` (Master fragments do, State fragments and
   event logs don't — they're legitimately multi-leader/append-only).
2. Write the same DDL in both `depot/` and `non-depot/` unless it's
   PII, in which case it goes in `depot/` only.
3. Keep version numbers in sync across both folders where the migration
   is shared, so it stays obvious at a glance which migrations were
   meant to run together.
4. If it's a State fragment, copy the conflict-resolution trigger
   pattern from `V1` (`trip_state` → `resolve_trip_state_conflict`) or
   `V4` (`container_state`, `vehicle_availability`,
   `driver_availability`) rather than inventing a new approach — keeping
   this consistent across fragments is more valuable than any
   per-table cleverness.
