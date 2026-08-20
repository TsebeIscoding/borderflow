#!/bin/bash
# Full-corridor test: seeds a trip at Depot, walks custody through
# Border -> Port -> Destination, then confirms all 4 sites converge on
# the same final state. Requires the mesh to already be deployed and
# setup-replication-k8s.sh already run.

set -e
TRIP_ID="33333333-3333-3333-3333-333333333333"

kubectl exec -i -n depot depot-db-0 -- psql -U postgres -d borderflow -c \
  "INSERT INTO trip_master (trip_id, origin_site_id, destination_site_id, created_at) VALUES ('$TRIP_ID', 'depot', 'destination', now());
   INSERT INTO trip_state (trip_id, current_site_id, status, lamport_ts) VALUES ('$TRIP_ID', 'depot', 'AtOrigin', 1);"

kubectl exec -i -n border border-db-0 -- psql -U postgres -d borderflow -c \
  "INSERT INTO handover (event_id, trip_id, from_site_id, to_site_id, verified_by) VALUES ('55555555-5555-5555-5555-555555555551', '$TRIP_ID', 'depot', 'border', 'liaison-01');
   UPDATE trip_state SET current_site_id = 'border', status = 'Arrived', lamport_ts = 2 WHERE trip_id = '$TRIP_ID';"

kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \
  "INSERT INTO handover (event_id, trip_id, from_site_id, to_site_id, verified_by) VALUES ('55555555-5555-5555-5555-555555555552', '$TRIP_ID', 'border', 'port', 'liaison-01');
   UPDATE trip_state SET current_site_id = 'port', status = 'Arrived', lamport_ts = 3 WHERE trip_id = '$TRIP_ID';"

kubectl exec -i -n destination destination-db-0 -- psql -U postgres -d borderflow -c \
  "INSERT INTO handover (event_id, trip_id, from_site_id, to_site_id, verified_by) VALUES ('55555555-5555-5555-5555-555555555553', '$TRIP_ID', 'port', 'destination', 'liaison-01');
   UPDATE trip_state SET current_site_id = 'destination', status = 'Delivered', lamport_ts = 4 WHERE trip_id = '$TRIP_ID';"

echo "=== Convergence check across all 4 sites ==="
for ns in depot border port destination; do
  echo "-- $ns --"
  kubectl exec -i -n $ns ${ns}-db-0 -- psql -U postgres -d borderflow -c \
    "SELECT current_site_id, status, lamport_ts FROM trip_state WHERE trip_id = '$TRIP_ID';"
done
