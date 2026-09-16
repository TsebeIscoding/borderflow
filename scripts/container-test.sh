#!/bin/bash
# Container equivalent of corridor-test.sh: seeds a container at Depot,
# relocates it through Border and Port, then confirms all 4 sites
# converge on the same final state. Proves the Container fragments
# (added in V4/V5) replicate correctly, same as Trip already does.
#
# Unlike Trip, there's no "Delivered" terminal status here -- see
# ContainerRelocationService's class javadoc for why -- so this test
# just confirms convergence on a status of "Arrived" at the final site.
#
# Waits for each INSERT/UPDATE to actually replicate before the next
# site tries to act on it -- see conflict-resolution-test.sh for why
# this matters (a real race condition was found and fixed there).

set -e
CONTAINER_ID="44444444-4444-4444-4444-444444444444"
CONSIGNMENT_ID="66666666-6666-6666-6666-666666666666"
CLIENT_ID="77777777-7777-7777-7777-777777777777"

wait_for_row() {
  local site=$1
  local expected_ts=$2
  for i in $(seq 1 30); do
    local ts
    ts=$(kubectl exec -i -n "$site" "${site}-db-0" -- psql -U postgres -d borderflow -t -c \
      "SELECT lamport_ts FROM container_state WHERE container_id = '$CONTAINER_ID';" 2>/dev/null | tr -d '[:space:]')
    if [ "$ts" = "$expected_ts" ]; then
      echo "$site caught up to lamport_ts=$expected_ts after ${i}s."
      return 0
    fi
    sleep 1
  done
  echo "$site never reached lamport_ts=$expected_ts after 30s -- check subscriptions."
  exit 1
}

echo "=== Seeding Master fragments at Depot (client, consignment, container) ==="
kubectl exec -i -n depot depot-db-0 -- psql -U postgres -d borderflow -c \
  "INSERT INTO client_core (client_id, name) VALUES ('$CLIENT_ID', 'Test Client Co');
   INSERT INTO consignment (consignment_id, client_id, description) VALUES ('$CONSIGNMENT_ID', '$CLIENT_ID', 'Test consignment');
   INSERT INTO container_master (container_id, container_number, consignment_id, size) VALUES ('$CONTAINER_ID', 'TEST-CONT-0001', '$CONSIGNMENT_ID', '40ft');
   INSERT INTO container_state (container_id, status, current_site_id, lamport_ts) VALUES ('$CONTAINER_ID', 'AtOrigin', 'depot', 1);"

echo "=== Waiting for it to replicate to Border ==="
wait_for_row border 1

echo "=== Confirming Master fragment (container_master) also replicated to Border ==="
kubectl exec -i -n border border-db-0 -- psql -U postgres -d borderflow -c \
  "SELECT container_number, size FROM container_master WHERE container_id = '$CONTAINER_ID';"

echo "=== Relocating Depot -> Border ==="
kubectl exec -i -n border border-db-0 -- psql -U postgres -d borderflow -c \
  "UPDATE container_state SET current_site_id = 'border', status = 'Arrived', lamport_ts = 2 WHERE container_id = '$CONTAINER_ID';"

echo "=== Waiting for it to replicate to Port ==="
wait_for_row port 2

echo "=== Relocating Border -> Port ==="
kubectl exec -i -n port port-db-0 -- psql -U postgres -d borderflow -c \
  "UPDATE container_state SET current_site_id = 'port', status = 'Arrived', lamport_ts = 3 WHERE container_id = '$CONTAINER_ID';"

echo "=== Waiting for it to replicate everywhere else before the final check ==="
wait_for_row depot 3
wait_for_row destination 3

echo "=== Convergence check across all 4 sites ==="
for ns in depot border port destination; do
  echo "-- $ns --"
  kubectl exec -i -n $ns ${ns}-db-0 -- psql -U postgres -d borderflow -c \
    "SELECT current_site_id, status, lamport_ts FROM container_state WHERE container_id = '$CONTAINER_ID';"
done
