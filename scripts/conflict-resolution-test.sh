#!/bin/bash
# Concurrent write conflict test: forces a stale (lower lamport_ts) write
# at one site after a newer one has already landed, and confirms the
# conflict-resolution trigger rejects it and logs it instead of corrupting
# state.
#
# Waits for Border's write to actually REPLICATE to Port before
# attempting the "stale" write -- without this wait, the test is racy:
# on a freshly re-established replication mesh (or just unlucky
# timing), Port might still be behind, in which case a "stale"
# lamport_ts can look newer than whatever Port still has locally and
# get legitimately accepted. That's the trigger working correctly on
# outdated information, not a bug -- but it makes the test
# non-deterministic, which is worse than useless as a regression check.

set -e
TRIP_ID="33333333-3333-3333-3333-333333333333"

kubectl exec -i -n border border-db-0 -- psql -U postgres -d borderflow -c \
  "UPDATE trip_state SET current_site_id = 'border', status = 'Arrived', lamport_ts = 5 WHERE trip_id = '$TRIP_ID';"

echo "=== Waiting for Border's write (lamport_ts=5) to replicate to Port ==="
for i in $(seq 1 30); do
  CURRENT_TS=$(kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -t -c \
    "SELECT lamport_ts FROM trip_state WHERE trip_id = '$TRIP_ID';" | tr -d '[:space:]')
  if [ "$CURRENT_TS" = "5" ]; then
    echo "Port caught up after ${i}s."
    break
  fi
  if [ "$i" = "30" ]; then
    echo "Port never caught up to lamport_ts=5 after 30s -- check subscriptions with:"
    echo "  kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \"SELECT subname, subenabled FROM pg_subscription;\""
    exit 1
  fi
  sleep 1
done

echo "=== Attempting stale write at Port (should be rejected) ==="
kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \
  "UPDATE trip_state SET current_site_id = 'port', status = 'Arrived', lamport_ts = 3 WHERE trip_id = '$TRIP_ID';"

echo "=== Checking conflict_exceptions was populated ==="
kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \
  "SELECT table_name, row_key, detected_at FROM conflict_exceptions ORDER BY detected_at DESC LIMIT 1;"

echo "=== Confirming Port's trip_state was NOT overwritten (should still show lamport_ts=5) ==="
kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \
  "SELECT current_site_id, status, lamport_ts FROM trip_state WHERE trip_id = '$TRIP_ID';"
