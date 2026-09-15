#!/bin/bash
# End-to-end auth test against a RUNNING backend instance (default
# localhost:8080). Confirms the whole JWT trust model actually holds:
# login works, OPERATOR can hand off, AUDITOR is correctly blocked
# from writing, and a request with no token at all is rejected.
#
# Requires: the backend running locally (mvn spring-boot:run) with
# SITE_ID set to whichever site you're testing against, AND a trip
# already seeded there (run scripts/corridor-test.sh against the DB
# first, or use any existing trip_id this site currently holds).
#
# Usage: ./auth-flow-test.sh <trip_id> [base_url]
# Example: ./auth-flow-test.sh 33333333-3333-3333-3333-333333333333

set -e

TRIP_ID=${1:?Usage: ./auth-flow-test.sh <trip_id> [base_url]}
BASE_URL=${2:-http://localhost:8080}

echo "=== 1. Reject unauthenticated request ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/trips")
echo "GET /api/trips with no token -> HTTP $CODE (expect 401)"

echo
echo "=== 2. Login as operator1 ==="
OPERATOR_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"operator1","password":"ChangeMe123!"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo "Got operator token (truncated): ${OPERATOR_TOKEN:0:20}..."

echo
echo "=== 3. Authenticated read as OPERATOR ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/trips" -H "Authorization: Bearer $OPERATOR_TOKEN")
echo "GET /api/trips as OPERATOR -> HTTP $CODE (expect 200)"

echo
echo "=== 4. OPERATOR can hand off (if this site currently holds the trip) ==="
curl -s -X POST "$BASE_URL/api/trips/$TRIP_ID/handover" \
  -H "Authorization: Bearer $OPERATOR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"toSiteId":"border","verifiedBy":"liaison-01"}'
echo
echo "(a 409 here just means this site doesn't currently hold that trip -- expected unless you seeded it fresh)"

echo
echo "=== 5. Login as auditor1 (only works if this is Depot) ==="
AUDITOR_TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"auditor1","password":"ChangeMe123!"}' | python3 -c "import sys,json; print(json.load(sys.stdin).get('token',''))" 2>/dev/null || echo "")

if [ -n "$AUDITOR_TOKEN" ]; then
  echo "Got auditor token (truncated): ${AUDITOR_TOKEN:0:20}..."

  echo
  echo "=== 6. AUDITOR can read ==="
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/trips" -H "Authorization: Bearer $AUDITOR_TOKEN")
  echo "GET /api/trips as AUDITOR -> HTTP $CODE (expect 200)"

  echo
  echo "=== 7. AUDITOR is BLOCKED from handover (the important one) ==="
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/trips/$TRIP_ID/handover" \
    -H "Authorization: Bearer $AUDITOR_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"toSiteId":"border","verifiedBy":"liaison-01"}')
  echo "POST handover as AUDITOR -> HTTP $CODE (expect 403 -- if you see 200, RBAC is broken)"
else
  echo "No auditor1 account on this site -- expected unless this is Depot (see db/migrations/depot/V6)"
fi

echo
echo "=== 8. Garbage token is rejected ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/trips" -H "Authorization: Bearer not-a-real-token")
echo "GET /api/trips with garbage token -> HTTP $CODE (expect 401)"
