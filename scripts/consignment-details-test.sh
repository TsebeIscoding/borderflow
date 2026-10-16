#!/bin/bash
# Live check of consignment details (V10 + backend) against a running Depot
# backend and the four databases. Creates one consignment, checks it
# replicated, then deletes it again so no test data is left behind.
#
# Needs: Depot backend on :8080, the four DB port-forwards (5432-5435), jq, psql.
# Usage: bash scripts/consignment-details-test.sh

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

CLIENT=$(curl -s "$BASE/api/clients" -H "$AUTH" | jq -r '.[0].clientId')
DP=$(curl -s "$BASE/api/delivery-points" -H "$AUTH" | jq -r '.[0].deliveryPointId')
if [ "$CLIENT" = "null" ] || [ "$DP" = "null" ]; then
  echo "Need at least one client and one delivery point first."; exit 1
fi

post() { curl -s -o /tmp/consignment-test-body.json -w '%{http_code}' -X POST "$BASE/api/consignments" \
  -H "$AUTH" -H 'Content-Type: application/json' -d "$1"; }

before=$(curl -s "$BASE/api/consignments" -H "$AUTH" | jq length)

echo "=== 1. Valid create ==="
valid=$(jq -n --arg c "$CLIENT" --arg d "$DP" '{clientId:$c,description:"V10 live test",deliveryPointId:$d,cargoType:"Refrigerated",totalWeightKg:1250.5,packageCount:12,declaredValue:12000,currency:"LSL",deliveryLocation:"Warehouse 4",expectedDeliveryDate:"2026-12-01"}')
check "valid create returns 200" 200 "$(post "$valid")"
REF=$(jq -r .referenceNumber /tmp/consignment-test-body.json)
ID=$(jq -r .consignmentId /tmp/consignment-test-body.json)
if [[ "$REF" =~ ^CON-[0-9]{4}-[0-9]{4}$ ]]; then check "reference number issued ($REF)" ok ok; else check "reference number issued" "CON-yyyy-nnnn" "$REF"; fi
check "cargo type echoed" Refrigerated "$(jq -r .cargoType /tmp/consignment-test-body.json)"
check "package count echoed" 12 "$(jq -r .packageCount /tmp/consignment-test-body.json)"

echo "=== 2. Rejected requests ==="
bad=$(jq -n --arg c "$CLIENT" --arg d "$DP" '{clientId:$c,deliveryPointId:$d,cargoType:"Liquid",totalWeightKg:10,packageCount:1}')
check "unknown cargo type returns 400" 400 "$(post "$bad")"

bad=$(jq -n --arg c "$CLIENT" --arg d "$DP" '{clientId:$c,deliveryPointId:$d,cargoType:"Bulk",totalWeightKg:10,packageCount:1,declaredValue:500}')
check "declared value without currency returns 400" 400 "$(post "$bad")"
check "...with the right message" yes "$(jq -r '.message | contains("together") | if . then "yes" else "no" end' /tmp/consignment-test-body.json)"

bad=$(jq -n --arg c "$CLIENT" --arg d "$DP" '{clientId:$c,deliveryPointId:$d,cargoType:"Bulk",totalWeightKg:0,packageCount:1}')
check "zero weight returns 400" 400 "$(post "$bad")"

bad=$(jq -n --arg c "$CLIENT" --arg d "$DP" '{clientId:$c,deliveryPointId:$d,cargoType:"Bulk",totalWeightKg:10}')
check "missing package count returns 400" 400 "$(post "$bad")"

after=$(curl -s "$BASE/api/consignments" -H "$AUTH" | jq length)
check "only the valid request created a row" $((before + 1)) "$after"

echo "=== 3. Replication of the created consignment ==="
sleep 3
for p in 5432 5433 5434 5435; do
  n=$(PGPASSWORD=$PGPW psql -h localhost -p $p -U app_user -d borderflow -tAc \
    "select count(*) from consignment where consignment_id='$ID' and reference_number='$REF' and cargo_type='Refrigerated' and package_count=12;")
  check "row present with details at port $p" 1 "$n"
done

echo "=== 4. Cleanup: delete it and confirm the delete replicated ==="
check "delete returns 204" 204 "$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE/api/consignments/$ID" -H "$AUTH")"
sleep 3
for p in 5432 5433 5434 5435; do
  n=$(PGPASSWORD=$PGPW psql -h localhost -p $p -U app_user -d borderflow -tAc "select count(*) from consignment where consignment_id='$ID';")
  check "row gone at port $p" 0 "$n"
done

echo
echo "Passed: $pass   Failed: $fail"
[ "$fail" -eq 0 ]
