#!/usr/bin/env bash
# Starts (or stops) the whole platform as local processes, for development before Docker (Phase 5).
#
#   scripts/run-local.sh          build if needed, start everything, wait until all are ready
#   scripts/run-local.sh stop     stop everything started by this script
#
# Logs: ./logs/<service>.log   PIDs: ./logs/<service>.pid
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/logs"
VERSION="0.1.0-SNAPSHOT"

# name:port:module-dir
APPS=(
  "user-service:8082:services/user-service"
  "product-service:8083:services/product-service"
  "inventory-service:8084:services/inventory-service"
  "order-service:8085:services/order-service"
  "payment-service:8086:services/payment-service"
  "notification-service:8087:services/notification-service"
  "master-service:8081:services/master-service"
  "api-gateway:8080:api-gateway"
)

stop_all() {
  for app in "${APPS[@]}"; do
    name="${app%%:*}"
    pid_file="$LOGS/$name.pid"
    if [[ -f "$pid_file" ]]; then
      kill "$(cat "$pid_file")" 2>/dev/null && echo "stopped $name" || true
      rm -f "$pid_file"
    fi
  done
}

if [[ "${1:-}" == "stop" ]]; then
  stop_all
  exit 0
fi

mkdir -p "$LOGS"
if [[ ! -f "$ROOT/api-gateway/target/api-gateway-$VERSION.jar" || "${1:-}" == "--build" ]]; then
  echo "Building (tests skipped; run ./mvnw verify for those)..."
  "$ROOT/mvnw" -q -B -DskipTests install
fi

for app in "${APPS[@]}"; do
  IFS=: read -r name port dir <<< "$app"
  jar="$ROOT/$dir/target/$name-$VERSION.jar"
  SERVER_PORT="$port" nohup java -XX:TieredStopAtLevel=1 -jar "$jar" > "$LOGS/$name.log" 2>&1 &
  echo $! > "$LOGS/$name.pid"
  echo "starting $name on :$port (log: logs/$name.log)"
done

echo -n "waiting for readiness"
for app in "${APPS[@]}"; do
  IFS=: read -r name port _ <<< "$app"
  for _ in $(seq 1 90); do
    curl -sf "http://localhost:$port/actuator/health/readiness" > /dev/null && break
    sleep 1
  done
  curl -sf "http://localhost:$port/actuator/health/readiness" > /dev/null \
    || { echo; echo "$name did not become ready, see logs/$name.log"; exit 1; }
  echo -n "."
done
echo
echo "All services ready. Public entry point: http://localhost:8080  (stop with: scripts/run-local.sh stop)"
