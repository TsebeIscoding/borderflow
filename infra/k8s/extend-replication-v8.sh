#!/bin/bash
# Wires the V8 delivery_point table into the replication mesh.
# Run ONLY after V8 has been applied at all four sites (non-depot first,
# then depot -- see db/migrations/depot/V8__delivery_points.sql).
#
# delivery_point is a Master fragment: Depot publishes it, the other three
# sites subscribe. Write access at non-depot sites is already revoked inside
# non-depot/V8.

set -e

SITES=(depot border port destination)

run_sql() {
  local site=$1
  local sql=$2
  echo "  -> $site: $sql" | head -c 120; echo
  kubectl exec -i -n "$site" "${site}-db-0" -- psql -U postgres -d borderflow -v ON_ERROR_STOP=1 -c "$sql"
}

echo "=== Step 1: Depot adds delivery_point to pub_master ==="
run_sql depot "ALTER PUBLICATION pub_master ADD TABLE delivery_point;"

echo "=== Step 2: Border, Port, Destination refresh their Master subscription (copies the seeded rows) ==="
for site in border port destination; do
  run_sql "$site" "ALTER SUBSCRIPTION sub_master_from_depot_at_${site} REFRESH PUBLICATION;"
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
  echo "=== ALL SUBSCRIPTIONS CONFIRMED ENABLED AFTER V8 ==="
else
  echo "=== WARNING: one or more subscriptions did not stay enabled — check output above ==="
fi
