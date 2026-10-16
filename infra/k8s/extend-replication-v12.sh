#!/bin/bash
# Wires the V12 trip_assignment event log into the replication mesh.
# Run ONLY after V12 has been applied at all four sites.
#
# trip_assignment is an Event log: every site writes its own rows and
# publishes them, and every site subscribes to every peer -- the same
# pattern as milestone, incident and trip_container.
# (The new trip_state columns need no wiring: trip_state is already
# published and the columns travel with it.)

set -e

SITES=(depot border port destination)

run_sql() {
  local site=$1
  local sql=$2
  echo "  -> $site: $sql" | head -c 120; echo
  kubectl exec -i -n "$site" "${site}-db-0" -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 -c "$sql"
}

echo "=== Step 1: each site adds trip_assignment to its local events publication ==="
for site in "${SITES[@]}"; do
  run_sql "$site" "ALTER PUBLICATION pub_events_local ADD TABLE trip_assignment;"
done

echo "=== Step 2: every site refreshes its subscription to every peer ==="
for site in "${SITES[@]}"; do
  for peer in "${SITES[@]}"; do
    if [ "$site" != "$peer" ]; then
      run_sql "$site" "ALTER SUBSCRIPTION sub_state_from_${peer}_at_${site} REFRESH PUBLICATION;"
    fi
  done
done

echo "=== Step 3: verify every subscription on every site is still enabled ==="
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
  echo "=== ALL SUBSCRIPTIONS CONFIRMED ENABLED AFTER V12 ==="
else
  echo "=== WARNING: one or more subscriptions did not stay enabled -- check output above ==="
fi
