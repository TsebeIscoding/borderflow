#!/bin/bash
# Concurrent write conflict test: forces a stale (lower lamport_ts) write
# at one site after a newer one has already landed, and confirms the
# conflict-resolution trigger rejects it and logs it instead of corrupting
# state.

set -e
TRIP_ID="33333333-3333-3333-3333-333333333333"

kubectl exec -i -n border border-db-0 -- psql -U postgres -d borderflow -c \
  "UPDATE trip_state SET current_site_id = 'border', status = 'Arrived', lamport_ts = 5 WHERE trip_id = '$TRIP_ID';"

echo "=== Attempting stale write at Port (should be rejected) ==="
kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \
  "UPDATE trip_state SET current_site_id = 'port', status = 'Arrived', lamport_ts = 3 WHERE trip_id = '$TRIP_ID';"

echo "=== Checking conflict_exceptions was populated ==="
kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \
  "SELECT table_name, row_key, detected_at FROM conflict_exceptions ORDER BY detected_at DESC LIMIT 1;"

echo "=== Confirming Port's trip_state was NOT overwritten ==="
kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \
  "SELECT current_site_id, status, lamport_ts FROM trip_state WHERE trip_id = '$TRIP_ID';"
