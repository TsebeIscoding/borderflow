#!/bin/bash
# Extends the existing BorderFlow replication mesh to cover the new tables
# added by 02_extend_schema.sql / 02_extend_schema_depot.sql.
# Run this ONLY after the schema extension SQL has been applied to all 4 pods.

set -e

SITES=(depot border port destination)

dns_for() { echo "${1}-db.${1}.svc.cluster.local"; }

run_sql() {
  local site=$1
  local sql=$2
  echo "  -> $site: $sql" | head -c 120; echo
  kubectl exec -i -n "$site" "${site}-db-0" -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 -c "$sql"
}

echo "=== Step 1: each site adds new State + Event tables to its local publications ==="
for site in "${SITES[@]}"; do
  run_sql "$site" "ALTER PUBLICATION pub_state_local ADD TABLE container_state, vehicle_availability, driver_availability;"
  run_sql "$site" "ALTER PUBLICATION pub_events_local ADD TABLE milestone, incident, trip_container;"
done

echo "=== Step 2: Depot adds new Master fragments to pub_master ==="
run_sql depot "ALTER PUBLICATION pub_master ADD TABLE container_master, vehicle_profile, driver_profile, client_core, consignment;"
# NOTE: client_contact is deliberately NOT added here — PII stays Depot-only, never published.

echo "=== Step 3: Border, Port, Destination refresh their Master subscription ==="
for site in border port destination; do
  run_sql "$site" "ALTER SUBSCRIPTION sub_master_from_depot_at_${site} REFRESH PUBLICATION;"
done

echo "=== Step 4: every site refreshes its State/Event subscriptions to every peer ==="
for site in "${SITES[@]}"; do
  for peer in "${SITES[@]}"; do
    if [ "$site" != "$peer" ]; then
      run_sql "$site" "ALTER SUBSCRIPTION sub_state_from_${peer}_at_${site} REFRESH PUBLICATION;"
    fi
  done
done

echo "=== Step 5: lock down new Master fragments at non-origin sites (same REVOKE pattern as trip_master) ==="
for site in border port destination; do
  run_sql "$site" "REVOKE INSERT, UPDATE, DELETE ON container_master, vehicle_profile, driver_profile, client_core, consignment FROM app_user;"
done

echo "=== Step 6: verify every subscription on every site is still enabled after refresh ==="
all_good=true
for site in "${SITES[@]}"; do
  echo "--- $site ---"
  result=$(kubectl exec -i -n "$site" "${site}-db-0" -- psql -U postgres -d borderflow -t -c "SELECT subname, subenabled FROM pg_subscription;")
  echo "$result"
  if echo "$result" | grep -q " f$"; then
    echo "  !!! WARNING: at least one subscription on $site is NOT enabled"
    all_good=false
  fi
done

echo
if [ "$all_good" = true ]; then
  echo "=== ALL SUBSCRIPTIONS CONFIRMED ENABLED AFTER SCHEMA EXTENSION ==="
else
  echo "=== WARNING: one or more subscriptions did not stay enabled — check output above ==="
fi
