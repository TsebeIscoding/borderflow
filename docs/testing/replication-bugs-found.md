# Replication bugs found during testing

Four bugs surfaced while building and testing the logical replication
mesh, each diagnosed from Postgres logs and fixed permanently in the
schema/setup rather than worked around.

## 1. Logical replication apply workers run with an empty `search_path`

Trigger functions with unqualified table names worked fine on local
writes but silently failed (or behaved unexpectedly) when fired by a
replication apply worker, because Postgres runs apply workers with an
empty `search_path` by default.

**Fix:** `ALTER FUNCTION ... SET search_path = public;` on every trigger
function, plus `ALTER TABLE ... ENABLE ALWAYS TRIGGER` so the trigger
fires on replicated writes as well as local ones (by default, triggers
are skipped when `session_replication_role = 'replica'`, which is how
apply workers run).

## 2. Subscription `DISABLE`/`ENABLE` needs independent verification

`subenabled` must be explicitly checked as `t` on both the publisher and
subscriber side — assuming a `CREATE SUBSCRIPTION` succeeded without
checking `pg_subscription.subenabled` on both ends led to silent
non-replication.

## 3. Subscription names must encode both publisher and subscriber

Using a generic subscription name across multiple site-pairs caused
replication slot name collisions on the publisher side once more than
one subscriber connected. Fixed by naming subscriptions
`sub_<fragment>_from_<publisher>_at_<subscriber>`.

## 4. Default `max_logical_replication_workers` too low for a 4-site mesh

The Postgres default (`4`) was insufficient once every site subscribes
to every other site (a full mesh, not a star). Raised to
`max_logical_replication_workers = 20` with `max_worker_processes = 24`
in each StatefulSet's Postgres startup args.
