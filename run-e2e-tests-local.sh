#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Local E2E Test Runner (Docker) — Artemis Benchmarking
# =============================================================================
# Builds the production image and brings up the full stack (Spring Boot server
# + MySQL) via docker compose, then runs the Playwright e2e suite against it.
# This mirrors the CI "e2e" job: slow (full production image build) but the
# most realistic — it exercises the exact artifact that gets deployed.
#
# For a fast, host-based loop instead, use ./run-e2e-tests-local-fast.sh
#
# Usage:
#   ./run-e2e-tests-local.sh [options] [-- <extra playwright args>]
#
# Options:
#   --skip-build   Reuse the existing ls1tum/artemis-benchmarking:latest image
#   --stop         Tear down the docker compose stack (and volumes); then exit
#   --ui           Open the Playwright UI
#   --headed       Run the browser headed
#   --help         Show this help message
# =============================================================================

cd "$(dirname "$0")"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log() { echo -e "${BLUE}[e2e]${NC} $*"; }
ok() { echo -e "${GREEN}[e2e]${NC} $*"; }
warn() { echo -e "${YELLOW}[e2e]${NC} $*"; }
err() { echo -e "${RED}[e2e]${NC} $*"; }

IMAGE="ls1tum/artemis-benchmarking:latest"
export E2E_BASE_URL="http://127.0.0.1:8080"

STOP=false
SKIP_BUILD=false
PLAYWRIGHT_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop) STOP=true; shift ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    --ui) PLAYWRIGHT_ARGS+=(--ui); shift ;;
    --headed) PLAYWRIGHT_ARGS+=(--headed); shift ;;
    --help) head -28 "$0" | tail -23; exit 0 ;;
    --) shift; PLAYWRIGHT_ARGS+=("$@"); break ;;
    *) PLAYWRIGHT_ARGS+=("$1"); shift ;;
  esac
done

if [ "$STOP" = true ]; then
  log "Stopping the docker compose stack..."
  docker compose down -v 2>/dev/null || true
  ok "All services stopped."
  exit 0
fi

# Ensure pnpm is available (Corepack ships with Node >= 24).
command -v corepack >/dev/null 2>&1 && corepack enable >/dev/null 2>&1 || true
for cmd in docker node pnpm; do
  command -v "$cmd" >/dev/null 2>&1 || { err "Missing required command: $cmd"; exit 1; }
done

if [ "$SKIP_BUILD" = false ]; then
  log "Building production image ($IMAGE) — this takes a few minutes..."
  docker build -t "$IMAGE" .
  ok "Image built."
else
  warn "Skipping image build (reusing $IMAGE)."
fi

log "Starting stack (server + MySQL) via docker compose..."
docker compose up -d --wait --wait-timeout 600
ok "Stack is healthy."

log "Installing dependencies and the Playwright browser..."
pnpm install --frozen-lockfile >/dev/null
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
warn "The docker stack is still running. To stop it:"
echo "  ./run-e2e-tests-local.sh --stop"
exit "$EXIT_CODE"
