#!/bin/bash
# Live check of trip assembly (V12 + backend) against a running Depot backend
# and the four databases: assigning and releasing a vehicle and driver, the
# "only one active trip" rules, the assignment history, and replication.
#
# Creates its own trips, vehicles, driver and container, and removes them
# (and the history rows they produced) at the end, so it can be re-run.
#
# Needs: Depot backend on :8080, the four DB port-forwards (5432-5435), the
# Depot database pod reachable with kubectl, jq, psql, and at least one
# consignment and one delivery point.
# Usage: bash scripts/trip-assembly-test.sh

set -u

BASE=${BASE:-http://localhost:8080}
PGPW=${DB_APP_USER_PASSWORD:-change_me_later}
pass=0
fail=0

check() { # name expected actual
  if [ "$2" = "$3" ]; then
    echo "PASS  $1 ($3)"; pass=$((pass + 1))
  else
    echo "FAIL  $1 (expected $2, got $3)"; fail=$((fail + 1))
  fi
}

login() {
  curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"ChangeMe123!\"}" | jq -r .token
}

TOKEN=$(login operator1)
AUDITOR=$(login auditor1)
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Login failed -- is the Depot backend running on $BASE?"; exit 1
fi
AUTH="Authorization: Bearer $TOKEN"

# req METHOD PATH [JSON] -> prints the HTTP status, leaves the body in /tmp/trip-assembly-body.json
req() {
  if [ $# -ge 3 ]; then
    curl -s -o /tmp/trip-assembly-body.json -w '%{http_code}' -X "$1" "$BASE$2" -H "$AUTH" -H 'Content-Type: application/json' -d "$3"
  else
    curl -s -o /tmp/trip-assembly-body.json -w '%{http_code}' -X "$1" "$BASE$2" -H "$AUTH"
  fi
}
field() { jq -r "$1" /tmp/trip-assembly-body.json; }
psql_app() { PGPASSWORD=$PGPW psql -h localhost -p "$1" -U app_user -d borderflow -tAc "$2"; }

DP=$(curl -s "$BASE/api/delivery-points" -H "$AUTH" | jq -r '.[0].deliveryPointId')
CONSIGNMENT=$(curl -s "$BASE/api/consignments" -H "$AUTH" | jq -r '.[0].consignmentId')
if [ "$DP" = "null" ] || [ "$CONSIGNMENT" = "null" ]; then
  echo "Need at least one delivery point and one consignment first."; exit 1
fi

echo "=== Setup: two trips, two vehicles, a driver, a container (all at Depot) ==="
trip_json=$(jq -n --arg d "$DP" '{destinationSiteId:"destination",deliveryPointId:$d}')
req POST /api/trips "$trip_json" >/dev/null; T1=$(field .tripId)
req POST /api/trips "$trip_json" >/dev/null; T2=$(field .tripId)
req POST /api/vehicles '{"registrationNumber":"ASM-TEST-1","capacity":10}' >/dev/null; V1=$(field .vehicleId)
req POST /api/vehicles '{"registrationNumber":"ASM-TEST-2","capacity":10}' >/dev/null; V2=$(field .vehicleId)
req POST /api/drivers '{"name":"Assembly Test Driver","licenseNumber":"ASM-LIC-1"}' >/dev/null; D1=$(field .driverId)
cont_json=$(jq -n --arg c "$CONSIGNMENT" '{containerNumber:"ASM-CONT-1",consignmentId:$c,size:"20ft"}')
req POST /api/containers "$cont_json" >/dev/null; C1=$(field .containerId)
for id in "$T1" "$T2" "$V1" "$V2" "$D1" "$C1"; do
  if [ -z "$id" ] || [ "$id" = "null" ]; then echo "Setup failed -- aborting."; exit 1; fi
done
echo "trips $T1 $T2"

echo "=== 1. Vehicle: assign, conflict, swap, release ==="
check "assign vehicle 1 to trip 1" 204 "$(req POST /api/trips/$T1/vehicle/$V1)"
req GET /api/trips/$T1 >/dev/null
check "trip 1 now shows vehicle 1" "$V1" "$(field .vehicleId)"
check "vehicle 1 cannot go on trip 2 as well" 409 "$(req POST /api/trips/$T2/vehicle/$V1)"
check "...with a clear message" yes "$(field '.message | contains("another active trip") | if . then "yes" else "no" end')"
check "swap trip 1 to vehicle 2" 204 "$(req POST /api/trips/$T1/vehicle/$V2)"
req GET /api/trips/$T1 >/dev/null
check "trip 1 now shows vehicle 2" "$V2" "$(field .vehicleId)"
check "vehicle 1 is free again, so trip 2 can take it" 204 "$(req POST /api/trips/$T2/vehicle/$V1)"
check "assigning the vehicle already on the trip is a no-op" 204 "$(req POST /api/trips/$T2/vehicle/$V1)"
check "release vehicle 2 from trip 1" 204 "$(req DELETE /api/trips/$T1/vehicle)"
req GET /api/trips/$T1 >/dev/null
check "trip 1 has no vehicle" null "$(field .vehicleId)"
check "releasing when none is assigned is a no-op" 204 "$(req DELETE /api/trips/$T1/vehicle)"

echo "=== 2. Driver: assign and conflict ==="
check "assign driver to trip 1" 204 "$(req POST /api/trips/$T1/driver/$D1)"
check "same driver cannot go on trip 2" 409 "$(req POST /api/trips/$T2/driver/$D1)"
req GET /api/trips/$T1 >/dev/null
check "trip 1 shows the driver" "$D1" "$(field .driverId)"

echo "=== 3. Rejections ==="
check "unknown vehicle returns 404" 404 "$(req POST /api/trips/$T1/vehicle/99999999-9999-9999-9999-999999999999)"
check "unknown trip returns 404" 404 "$(req POST /api/trips/99999999-9999-9999-9999-999999999999/vehicle/$V1)"
check "auditor cannot assign" 403 "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/trips/$T1/vehicle/$V2" -H "Authorization: Bearer $AUDITOR")"
check "auditor can read the history" 200 "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/trips/$T1/assignments" -H "Authorization: Bearer $AUDITOR")"

echo "=== 4. A container can be on only one active trip ==="
check "link container to trip 1" 204 "$(req POST /api/trips/$T1/containers/$C1)"
check "link the same container to trip 2" 409 "$(req POST /api/trips/$T2/containers/$C1)"
check "...with a clear message" yes "$(field '.message | contains("another active trip") | if . then "yes" else "no" end')"
check "unlink it from trip 1" 204 "$(req DELETE /api/trips/$T1/containers/$C1)"
check "now trip 2 can have it" 204 "$(req POST /api/trips/$T2/containers/$C1)"

echo "=== 5. History ==="
req GET /api/trips/$T1/assignments >/dev/null
check "trip 1 has 5 history rows" 5 "$(field length)"
check "first row is vehicle 1 assigned" "VEHICLE ASSIGNED" "$(field '.[0] | .resourceType + " " + .action')"
check "rows are in order: vehicle 1 released before vehicle 2 assigned" "RELEASED ASSIGNED" "$(field '.[1].action + " " + .[2].action')"
req GET /api/trips/$T2/assignments >/dev/null
check "trip 2 has 1 history row" 1 "$(field length)"

echo "=== 6. Replication to all four sites ==="
sleep 3
for p in 5432 5433 5434 5435; do
  check "history rows at port $p" 6 "$(psql_app $p "select count(*) from trip_assignment where trip_id in ('$T1','$T2');")"
  check "trip 1 driver at port $p" "$D1" "$(psql_app $p "select driver_id from trip_state where trip_id='$T1';")"
  check "trip 1 has no vehicle at port $p" "" "$(psql_app $p "select coalesce(vehicle_id::text,'') from trip_state where trip_id='$T1';")"
done

echo "=== Cleanup ==="
check "unlink container from trip 2" 204 "$(req DELETE /api/trips/$T2/containers/$C1)"
check "delete trip 1" 204 "$(req DELETE /api/trips/$T1)"
check "delete trip 2" 204 "$(req DELETE /api/trips/$T2)"
check "delete vehicle 1" 204 "$(req DELETE /api/vehicles/$V1)"
check "delete vehicle 2" 204 "$(req DELETE /api/vehicles/$V2)"
check "delete driver" 204 "$(req DELETE /api/drivers/$D1)"
check "delete container" 204 "$(req DELETE /api/containers/$C1)"

# trip_assignment is append-only for the application, so its rows are removed
# here as the database admin; the deletes replicate to the other sites.
kubectl exec -n depot depot-db-0 -- psql -U postgres -d borderflow -c \
  "delete from trip_assignment where trip_id in ('$T1','$T2');" >/dev/null 2>&1 \
  || echo "WARN  could not remove the test history rows with kubectl -- remove trip_assignment rows for $T1 and $T2 by hand"
sleep 3
for p in 5432 5433 5434 5435; do
  check "test history rows gone at port $p" 0 "$(psql_app $p "select count(*) from trip_assignment where trip_id in ('$T1','$T2');")"
done

echo
echo "Passed: $pass   Failed: $fail"
[ "$fail" -eq 0 ]
