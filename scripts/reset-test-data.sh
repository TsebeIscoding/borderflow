#!/bin/bash
# Cleans up the fixed trip_id used by every other test script, so
# corridor-test.sh can insert it fresh instead of colliding with
# leftover data from a previous run (e.g. after a cluster was rebuilt
# mid-test, as happened here). Safe to run even if some/all of this
# data doesn't exist yet.

set -e
TRIP_ID="33333333-3333-3333-3333-333333333333"

for site in depot border port destination; do
  echo "-- cleaning $site --"
  kubectl exec -i -n "$site" "${site}-db-0" -- psql -U postgres -d borderflow -c \
    "DELETE FROM conflict_exceptions WHERE row_key = '$TRIP_ID';
     DELETE FROM handover WHERE trip_id = '$TRIP_ID';
     DELETE FROM trip_state WHERE trip_id = '$TRIP_ID';"
done

# trip_master is single-leader -- only ever delete it at Depot. The
# deletion replicates outward from there; deleting it directly at any
# other site would just create the exact silent-divergence bug
# documented in docs/testing/test-results.md #5.
kubectl exec -i -n depot depot-db-0 -- psql -U postgres -d borderflow -c \
  "DELETE FROM trip_master WHERE trip_id = '$TRIP_ID';"

echo "Done. Trip $TRIP_ID cleared from every site -- corridor-test.sh can now re-seed it cleanly."
