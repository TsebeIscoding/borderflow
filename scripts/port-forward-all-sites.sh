#!/bin/bash
# Starts all 4 port-forwards needed to run every site's backend
# simultaneously on one machine, each on its own local port so they
# don't collide (see backend/src/main/resources/application-*.yml for
# which port maps to which site). Runs them all in the background of
# THIS terminal and cleans them up automatically when you Ctrl+C --
# leave this running in its own terminal for as long as you need more
# than one site's backend up at once.
#
#   depot        -> localhost:5432
#   border       -> localhost:5433
#   port         -> localhost:5434
#   destination  -> localhost:5435

set -e

PIDS=()

cleanup() {
  echo
  echo "Stopping all port-forwards..."
  for pid in "${PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null
  echo "Done."
}
trap cleanup EXIT INT TERM

kubectl port-forward -n depot depot-db-0 5432:5432 > /tmp/pf-depot.log 2>&1 &
PIDS+=($!)
kubectl port-forward -n border border-db-0 5433:5432 > /tmp/pf-border.log 2>&1 &
PIDS+=($!)
kubectl port-forward -n port port-db-0 5434:5432 > /tmp/pf-port.log 2>&1 &
PIDS+=($!)
kubectl port-forward -n destination destination-db-0 5435:5432 > /tmp/pf-destination.log 2>&1 &
PIDS+=($!)

sleep 2

echo "Port-forwards running (logs in /tmp/pf-<site>.log if any of these fail silently):"
echo "  depot        -> localhost:5432  (PID ${PIDS[0]})"
echo "  border       -> localhost:5433  (PID ${PIDS[1]})"
echo "  port         -> localhost:5434  (PID ${PIDS[2]})"
echo "  destination  -> localhost:5435  (PID ${PIDS[3]})"
echo
echo "Leave this running. In separate terminals, start each site's backend, e.g.:"
echo "  cd backend && SPRING_PROFILES_ACTIVE=depot DB_APP_USER_PASSWORD=change_me_later mvn spring-boot:run"
echo "  cd backend && SPRING_PROFILES_ACTIVE=border DB_APP_USER_PASSWORD=change_me_later mvn spring-boot:run"
echo "  (etc. for port and destination)"
echo
echo "Press Ctrl+C here to stop all four port-forwards."

wait
