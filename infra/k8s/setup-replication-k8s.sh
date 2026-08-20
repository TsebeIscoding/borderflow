#!/bin/bash
# Sets up the full 4-site BorderFlow replication mesh inside Kubernetes.
# Run this ONCE, after all 4 StatefulSet pods are Running.
# Same logic as the docker-compose version, adapted for kubectl + K8s DNS.

set -e

SITES=(depot border port destination)
declare -A PASSWORDS=( [depot]=depot_pw [border]=border_pw [port]=port_pw [destination]=destination_pw )

# Cross-namespace DNS: <service>.<namespace>.svc.cluster.local
dns_for() { echo "${1}-db.${1}.svc.cluster.local"; }

run_sql() {
  local site=$1
  local sql=$2
  echo "  -> $site: $sql" | head -c 120; echo
  kubectl exec -i -n "$site" "${site}-db-0" -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 -c "$sql"
}

echo "=== Step 1: each site publishes its own state + event fragments ==="
for site in "${SITES[@]}"; do
  run_sql "$site" "CREATE PUBLICATION pub_state_local FOR TABLE trip_state;"
  run_sql "$site" "CREATE PUBLICATION pub_events_local FOR TABLE handover;"
done

echo "=== Step 2: Depot additionally publishes the Master fragment ==="
run_sql depot "CREATE PUBLICATION pub_master FOR TABLE trip_master;"

echo "=== Step 3: Border, Port, Destination each subscribe to Depot's Master fragment (one-way) ==="
for site in border port destination; do
  run_sql "$site" "CREATE SUBSCRIPTION sub_master_from_depot_at_${site} CONNECTION 'host=$(dns_for depot) port=5432 dbname=borderflow user=postgres password=${PASSWORDS[depot]}' PUBLICATION pub_master;"
done

echo "=== Step 4: full state/event mesh — every site subscribes to every OTHER site ==="
for site in "${SITES[@]}"; do
  for peer in "${SITES[@]}"; do
    if [ "$site" != "$peer" ]; then
      run_sql "$site" "CREATE SUBSCRIPTION sub_state_from_${peer}_at_${site} CONNECTION 'host=$(dns_for "$peer") port=5432 dbname=borderflow user=postgres password=${PASSWORDS[$peer]}' PUBLICATION pub_state_local, pub_events_local WITH (origin = none);"
    fi
  done
done

echo "=== Step 5: verify every subscription on every site is enabled ==="
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
  echo "=== ALL SUBSCRIPTIONS CONFIRMED ENABLED ON ALL 4 SITES ==="
else
  echo "=== WARNING: one or more subscriptions did not enable correctly — check output above before testing ==="
fi
