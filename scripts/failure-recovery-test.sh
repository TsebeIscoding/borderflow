#!/bin/bash
# Failure/recovery test: kills a site's pod mid-mesh, waits for the
# StatefulSet to recreate it, then confirms replication catches it back up
# with no data loss. Requires corridor-test.sh to have been run first.

set -e
SITE=${1:-border}
TRIP_ID="33333333-3333-3333-3333-333333333333"

echo "=== Deleting pod: ${SITE}-db-0 ==="
kubectl delete pod -n "$SITE" "${SITE}-db-0"

echo "=== Waiting for it to come back Running ==="
kubectl wait --for=condition=Ready pod/"${SITE}-db-0" -n "$SITE" --timeout=120s

echo "=== Confirming state caught back up via replication ==="
kubectl exec -i -n "$SITE" "${SITE}-db-0" -- psql -U postgres -d borderflow -c \
  "SELECT trip_id, current_site_id, status, lamport_ts FROM trip_state WHERE trip_id = '$TRIP_ID';"
