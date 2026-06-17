#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Fast Local E2E Test Runner — Artemis Benchmarking
# =============================================================================
# Runs the server (./gradlew bootRun) and client (pnpm start) directly on the
# host, with MySQL in a small throwaway container. No production image build,
# so it is much faster than ./run-e2e-tests-local.sh. Services are left running
# between runs, so re-runs (with --skip-*) take only seconds.
#
# If you already have a MySQL on localhost:3307 (db "artemis-benchmarking",
# empty root password — matching the dev profile), use --skip-db to run with
# no Docker at all.
#
# Usage:
#   ./run-e2e-tests-local-fast.sh [options] [-- <extra playwright args>]
#
# Options:
#   --stop           Kill the server, client and MySQL container; then exit
#   --skip-db        Reuse an already-running MySQL on localhost:3307
#   --skip-server    Reuse an already-running server on :8080
#   --skip-client    Reuse an already-running client on :9000
#   --ui             Open the Playwright UI
#   --headed         Run the browser headed
#   --help           Show this help message
# =============================================================================

cd "$(dirname "$0")"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log() { echo -e "${BLUE}[e2e]${NC} $*"; }
ok() { echo -e "${GREEN}[e2e]${NC} $*"; }
warn() { echo -e "${YELLOW}[e2e]${NC} $*"; }
err() { echo -e "${RED}[e2e]${NC} $*"; }

LOCAL_DIR=".e2e-local"
MYSQL_CONTAINER="benchmarking-e2e-mysql"
SERVER_PORT=8080
CLIENT_PORT=9000
export E2E_BASE_URL="http://localhost:${CLIENT_PORT}"

STOP=false
SKIP_DB=false
SKIP_SERVER=false
SKIP_CLIENT=false
PLAYWRIGHT_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop) STOP=true; shift ;;
    --skip-db) SKIP_DB=true; shift ;;
    --skip-server) SKIP_SERVER=true; shift ;;
    --skip-client) SKIP_CLIENT=true; shift ;;
    --ui) PLAYWRIGHT_ARGS+=(--ui); shift ;;
    --headed) PLAYWRIGHT_ARGS+=(--headed); shift ;;
    --help) head -33 "$0" | tail -28; exit 0 ;;
    --) shift; PLAYWRIGHT_ARGS+=("$@"); break ;;
    *) PLAYWRIGHT_ARGS+=("$1"); shift ;;
  esac
done

# Recursively kill a process and its children (portable: macOS + Linux).
kill_tree() {
  local pid=$1
  for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child"; done
  kill "$pid" 2>/dev/null || true
}

# Kill a previously started background process tracked via a PID file.
kill_pidfile() {
  local name=$1 file="$LOCAL_DIR/$1.pid"
  if [ -f "$file" ]; then
    local pid; pid=$(cat "$file")
    if kill -0 "$pid" 2>/dev/null; then log "Stopping $name (PID $pid)..."; kill_tree "$pid"; fi
    rm -f "$file"
  fi
}

# Free a TCP port by killing whatever listens on it (stale process from a
# previous run). Hands-free re-runs should not require manual cleanup.
free_port() {
  local port=$1 label=$2 pids
  pids=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null || true)
  if [ -n "$pids" ]; then
    warn "Port $port ($label) in use — killing $(echo "$pids" | tr '\n' ' ')..."
    for pid in $pids; do kill_tree "$pid"; done
    sleep 2
  fi
}

wait_for_url() {
  local url=$1 label=$2 timeout=${3:-300} elapsed=0
  log "Waiting for $label ..."
  until curl -sf "$url" >/dev/null 2>&1; do
    if [ "$elapsed" -ge "$timeout" ]; then err "$label not ready after ${timeout}s"; return 1; fi
    sleep 3; elapsed=$((elapsed + 3))
  done
  ok "$label ready (${elapsed}s)"
}

if [ "$STOP" = true ]; then
  log "Stopping all fast e2e services..."
  kill_pidfile "client"; free_port "$CLIENT_PORT" "client"
  kill_pidfile "server"; free_port "$SERVER_PORT" "server"
  docker rm -f "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
  rm -rf "$LOCAL_DIR"
  ok "All services stopped."
  exit 0
fi

command -v corepack >/dev/null 2>&1 && corepack enable >/dev/null 2>&1 || true
for cmd in docker java node pnpm curl; do
  command -v "$cmd" >/dev/null 2>&1 || { err "Missing required command: $cmd"; exit 1; }
done
mkdir -p "$LOCAL_DIR"

# --- MySQL (port 3307, matching the dev profile) -----------------------------
if [ "$SKIP_DB" = true ]; then
  warn "Skipping MySQL (--skip-db) — expecting one on localhost:3307."
elif lsof -nP -iTCP:3307 -sTCP:LISTEN >/dev/null 2>&1; then
  log "MySQL already listening on localhost:3307 — using the existing instance."
else
  log "Starting MySQL container ($MYSQL_CONTAINER) on localhost:3307..."
  docker rm -f "$MYSQL_CONTAINER" >/dev/null 2>&1 || true
  docker run -d --name "$MYSQL_CONTAINER" -p 127.0.0.1:3307:3306 \
    -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=artemis-benchmarking \
    mysql:9.7.1 mysqld --lower_case_table_names=1 --tls-version='' \
    --character_set_server=utf8mb4 --explicit_defaults_for_timestamp >/dev/null
  elapsed=0
  until docker exec "$MYSQL_CONTAINER" mysqladmin ping -h 127.0.0.1 --silent >/dev/null 2>&1; do
    if [ "$elapsed" -ge 120 ]; then err "MySQL not ready after 120s"; exit 1; fi
    sleep 3; elapsed=$((elapsed + 3))
  done
  ok "MySQL ready (${elapsed}s)"
fi

# --- Server (Spring Boot, dev profile -> MySQL on 3307) ----------------------
if [ "$SKIP_SERVER" = false ]; then
  kill_pidfile "server"; free_port "$SERVER_PORT" "server"
  log "Starting server (./gradlew bootRun); log: $LOCAL_DIR/server.log"
  ./gradlew bootRun >"$LOCAL_DIR/server.log" 2>&1 &
  echo $! >"$LOCAL_DIR/server.pid"
else
  warn "Skipping server (--skip-server)."
fi

# --- Client (Angular dev server on :9000, proxies to the server) -------------
if [ "$SKIP_CLIENT" = false ]; then
  kill_pidfile "client"; free_port "$CLIENT_PORT" "client"
  log "Starting client (pnpm start); log: $LOCAL_DIR/client.log"
  pnpm start >"$LOCAL_DIR/client.log" 2>&1 &
  echo $! >"$LOCAL_DIR/client.pid"
else
  warn "Skipping client (--skip-client)."
fi

[ "$SKIP_SERVER" = false ] && wait_for_url "http://localhost:${SERVER_PORT}/management/health" "server" 300
[ "$SKIP_CLIENT" = false ] && wait_for_url "$E2E_BASE_URL" "client" 180

# --- Playwright --------------------------------------------------------------
log "Installing the Playwright browser..."
pnpm exec playwright install chromium >/dev/null

echo ""
log "Running Playwright e2e tests against $E2E_BASE_URL ..."
echo ""
EXIT_CODE=0
pnpm exec playwright test "${PLAYWRIGHT_ARGS[@]+"${PLAYWRIGHT_ARGS[@]}"}" || EXIT_CODE=$?

echo ""
if [ "$EXIT_CODE" -eq 0 ]; then
  ok "All e2e tests passed."
else
  err "Some e2e tests failed (exit code $EXIT_CODE)."
  warn "View the report: pnpm exec playwright show-report"
fi
echo ""
warn "Services are still running. Quick re-run:"
echo "  ./run-e2e-tests-local-fast.sh --skip-db --skip-server --skip-client"
warn "To stop everything:"
echo "  ./run-e2e-tests-local-fast.sh --stop"
echo ""
log "Logs: $LOCAL_DIR/server.log, $LOCAL_DIR/client.log"
exit "$EXIT_CODE"
