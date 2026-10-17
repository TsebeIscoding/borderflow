#!/bin/bash
# Live check of whole-trip handover: handing a trip over moves its linked
# containers, assigned vehicle and assigned driver with it, and a hand-off is
# refused (moving nothing) if the sending site does not hold all of them.
#
# Creates its own records and removes them at the end (as the database admin
# for the append-only event tables), so it can be re-run.
#
# Needs: Depot backend on :8080, the four DB port-forwards (5432-5435), kubectl
# access to the Depot database pod, jq, psql, and at least one consignment and
# one delivery point.
# Usage: bash scripts/whole-trip-handover-test.sh

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

TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"operator1","password":"ChangeMe123!"}' | jq -r .token)
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Login failed -- is the Depot backend running on $BASE?"; exit 1
fi
AUTH="Authorization: Bearer $TOKEN"

req() {
  if [ $# -ge 3 ]; then
    curl -s -o /tmp/whole-trip-body.json -w '%{http_code}' -X "$1" "$BASE$2" -H "$AUTH" -H 'Content-Type: application/json' -d "$3"
  else
    curl -s -o /tmp/whole-trip-body.json -w '%{http_code}' -X "$1" "$BASE$2" -H "$AUTH"
  fi
}
field() { jq -r "$1" /tmp/whole-trip-body.json; }
psql_app() { PGPASSWORD=$PGPW psql -h localhost -p "$1" -U app_user -d borderflow -tAc "$2"; }

DP=$(curl -s "$BASE/api/delivery-points" -H "$AUTH" | jq -r '.[0].deliveryPointId')
CONSIGNMENT=$(curl -s "$BASE/api/consignments" -H "$AUTH" | jq -r '.[0].consignmentId')
if [ "$DP" = "null" ] || [ "$CONSIGNMENT" = "null" ]; then
  echo "Need at least one delivery point and one consignment first."; exit 1
fi

echo "=== Setup: two trips, each with a container, vehicle and driver (all at Depot) ==="
trip_json=$(jq -n --arg d "$DP" '{destinationSiteId:"destination",deliveryPointId:$d}')
make_set() { # suffix -> sets T C V D globals
  req POST /api/trips "$trip_json" >/dev/null; T=$(field .tripId)
  req POST /api/vehicles "{\"registrationNumber\":\"WTH-$1\",\"capacity\":10}" >/dev/null; V=$(field .vehicleId)
  req POST /api/drivers "{\"name\":\"WTH Driver $1\",\"licenseNumber\":\"WTH-LIC-$1\"}" >/dev/null; D=$(field .driverId)
  local cj; cj=$(jq -n --arg c "$CONSIGNMENT" --arg n "WTH-CONT-$1" '{containerNumber:$n,consignmentId:$c,size:"20ft"}')
  req POST /api/containers "$cj" >/dev/null; C=$(field .containerId)
  req POST /api/trips/$T/vehicle/$V >/dev/null
  req POST /api/trips/$T/driver/$D >/dev/null
  req POST /api/trips/$T/containers/$C >/dev/null
}
make_set A; TA=$T; CA=$C; VA=$V; DA=$D
make_set B; TB=$T; CB=$C; VB=$V; DB=$D
for id in "$TA" "$CA" "$VA" "$DA" "$TB" "$CB" "$VB" "$DB"; do
  if [ -z "$id" ] || [ "$id" = "null" ]; then echo "Setup failed -- aborting."; exit 1; fi
done

echo "=== 1. Handing trip A to Border moves its whole load ==="
check "hand-off returns 200" 200 "$(req POST /api/trips/$TA/handover '{"toSiteId":"border","verifiedBy":"wth-test"}')"
check "response says one container moved" 1 "$(field .containersMoved)"
check "response says the vehicle moved" true "$(field .vehicleMoved)"
check "response says the driver moved" true "$(field .driverMoved)"
check "trip is Arrived" Arrived "$(field .newStatus)"
sleep 3
for p in 5432 5433 5434 5435; do
  check "trip A at border at port $p" border "$(psql_app $p "select current_site_id from trip_state where trip_id='$TA';")"
  check "container A at border at port $p" "border Arrived" "$(psql_app $p "select current_site_id || ' ' || status from container_state where container_id='$CA';")"
  check "vehicle A at border at port $p" border "$(psql_app $p "select current_site_id from vehicle_availability where vehicle_id='$VA';")"
  check "driver A at border at port $p" border "$(psql_app $p "select current_site_id from driver_availability where driver_id='$DA';")"
done

echo "=== 2. Refused when the sending site does not hold all of the load ==="
# Move trip B's vehicle to Port on its own, then try to hand trip B to Border.
check "relocate vehicle B to port" 200 "$(req POST /api/vehicles/$VB/relocate '{"toSiteId":"port","verifiedBy":"wth-test"}')"
check "hand-off of trip B is refused" 409 "$(req POST /api/trips/$TB/handover '{"toSiteId":"border","verifiedBy":"wth-test"}')"
check "...and the message names the vehicle" yes "$(field '.message | contains("vehicle") | if . then "yes" else "no" end')"
req GET /api/trips/$TB >/dev/null
check "trip B did not move" depot "$(field .currentSiteId)"
sleep 3
check "container B did not move" depot "$(psql_app 5432 "select current_site_id from container_state where container_id='$CB';")"
check "driver B did not move" depot "$(psql_app 5432 "select current_site_id from driver_availability where driver_id='$DB';")"
check "no handover event was written for trip B" 0 "$(psql_app 5432 "select count(*) from handover where trip_id='$TB';")"

echo "=== Cleanup ==="
# Event tables are append-only for the application, and the container is now at
# another site (so it cannot be unlinked from Depot): remove these rows as the
# database admin; the deletes replicate.
kubectl exec -n depot depot-db-0 -- psql -U postgres -d borderflow -c \
  "delete from trip_container where trip_id in ('$TA','$TB'); delete from trip_assignment where trip_id in ('$TA','$TB'); delete from handover where trip_id in ('$TA','$TB');" >/dev/null 2>&1 \
  || echo "WARN  could not remove event rows with kubectl -- remove trip_container, trip_assignment and handover rows for $TA and $TB by hand"
for t in "$TA" "$TB"; do check "delete trip $t" 204 "$(req DELETE /api/trips/$t)"; done
for v in "$VA" "$VB"; do check "delete vehicle $v" 204 "$(req DELETE /api/vehicles/$v)"; done
for d in "$DA" "$DB"; do check "delete driver $d" 204 "$(req DELETE /api/drivers/$d)"; done
for c in "$CA" "$CB"; do check "delete container $c" 204 "$(req DELETE /api/containers/$c)"; done
sleep 3
for p in 5432 5433 5434 5435; do
  check "no test rows left at port $p" 0 "$(psql_app $p "select (select count(*) from trip_state where trip_id in ('$TA','$TB')) + (select count(*) from handover where trip_id in ('$TA','$TB')) + (select count(*) from trip_assignment where trip_id in ('$TA','$TB'));")"
done

echo
echo "Passed: $pass   Failed: $fail"
[ "$fail" -eq 0 ]
