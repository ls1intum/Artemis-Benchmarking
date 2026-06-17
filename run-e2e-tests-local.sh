#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Local E2E Test Runner (Docker) — Artemis Benchmarking
# =============================================================================
# Builds the production WAR, wraps it in a runtime image, and runs the full
# stack (Spring Boot server + MySQL) via docker compose, then runs the
# Playwright e2e suite against it. Slower than the fast runner but realistic:
# it exercises the production artifact in a container with a real database.
#
# The WAR is built on the host (gradlew bootWar) and wrapped in a thin image,
# rather than via `docker build .`. A clean in-container build drops the bundled
# Angular client from the WAR — in production the client is served by nginx
# (see docker-compose.prod.yml), but the local docker-compose.yml has no nginx,
# so the app itself must serve the client. The host build packages it reliably.
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
    --help) head -34 "$0" | tail -29; exit 0 ;;
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
for cmd in docker java node pnpm; do
  command -v "$cmd" >/dev/null 2>&1 || { err "Missing required command: $cmd"; exit 1; }
done
docker info >/dev/null 2>&1 || { err "Docker does not appear to be running. Start Docker and retry."; exit 1; }

if [ "$SKIP_BUILD" = false ]; then
  log "Building the production WAR on the host (gradlew -Pprod -Pwar bootWar)..."
  ./gradlew -Pprod -Pwar clean bootWar
  WAR_FILE=$(ls build/libs/*.war 2>/dev/null | head -1)
  [ -n "$WAR_FILE" ] || { err "No WAR produced in build/libs/."; exit 1; }

  # Guard: the WAR must contain the bundled client, otherwise the app returns
  # 404 for the SPA and every test fails. (A clean in-container build drops it.)
  if ! { jar tf "$WAR_FILE" 2>/dev/null || unzip -l "$WAR_FILE" 2>/dev/null; } | grep -q 'static/index.html'; then
    err "The built WAR does not contain the Angular client (static/index.html) — aborting."
    exit 1
  fi
  ok "WAR built with bundled client."

  log "Wrapping the WAR in a runtime image ($IMAGE)..."
  BUILD_CTX=$(mktemp -d)
  cp "$WAR_FILE" "$BUILD_CTX/app.war"
  cat >"$BUILD_CTX/Dockerfile" <<'DOCKERFILE'
FROM azul/zulu-openjdk:25.0.3-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
RUN mkdir /app
COPY app.war /app/app.war
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.war"]
DOCKERFILE
  docker build -t "$IMAGE" "$BUILD_CTX"
  rm -rf "$BUILD_CTX"
  ok "Runtime image built."
else
  warn "Skipping build (reusing $IMAGE)."
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
