#!/usr/bin/env bash
#
# Fully automatic local load test: a multi-node Artemis cluster + the benchmark, driving a
# CREATE_COURSE_AND_EXAM simulation with N users, then reporting per-endpoint performance.
#
# No interactive input required. Brings everything up from a clean state, runs the simulation,
# writes a timestamped report, and tears everything down again (so it is safe to run regularly).
#
# Usage:
#   ./run-load-test.sh                 # 500 users (default)
#   NUM_USERS=50 ./run-load-test.sh    # quick smaller run
#   KEEP_RUNNING=1 ./run-load-test.sh  # leave the stack up afterwards for inspection
#
# Requirements: Docker (Desktop) with >= ~16 GB allocated, a local Artemis checkout (ARTEMIS_DIR),
# and the benchmark's own build prerequisites (JDK / gradlew) since the benchmark runs from source.

set -euo pipefail

# --------------------------------------------------------------------------------------------------
# Configuration (override via environment variables)
# --------------------------------------------------------------------------------------------------
NUM_USERS="${NUM_USERS:-500}"

# --- Work mix (two independent dimensions) ---------------------------------------------------------
# 1) IDE: what share of ALL students use the online code editor (REST commits) vs. the offline IDE
#    (real git clone + git push). ONLINE_IDE_PCT is the online share; the rest are offline.
ONLINE_IDE_PCT="${ONLINE_IDE_PCT:-50}"       # 50% online code editor, 50% offline IDE (git)
# 2) Git auth: how the OFFLINE (git) students split across auth mechanisms. Must sum to 100.
OFFLINE_TOKEN_PCT="${OFFLINE_TOKEN_PCT:-70}"      # 70% participation token
OFFLINE_PASSWORD_PCT="${OFFLINE_PASSWORD_PCT:-20}" # 20% password
OFFLINE_SSH_PCT="${OFFLINE_SSH_PCT:-10}"          # 10% SSH (requires host port 7921 to be free)

# Random commits/pushes per programming student in the half-open range [from, to).
# Default [2, 5) => 2, 3 or 4 commits per student.
COMMITS_FROM="${COMMITS_FROM:-2}"
COMMITS_TO="${COMMITS_TO:-5}"

ARTEMIS_DIR="${ARTEMIS_DIR:-/Users/krusche/Projects/Artemis}"
ARTEMIS_BUILD_ARCH="${ARTEMIS_BUILD_ARCH:-arm64}"   # arm64 (Apple Silicon) or amd64 (Intel)
ARTEMIS_NODE_XMX="${ARTEMIS_NODE_XMX:-2560m}"       # heap per Artemis node

LB_PORT="${LB_PORT:-18080}"                  # host port of the Artemis load balancer
BENCH_PORT="${BENCH_PORT:-8090}"             # host port of the benchmark app
BENCH_DB_PORT="${BENCH_DB_PORT:-15432}"      # host port of the benchmark's own Postgres
KEEP_RUNNING="${KEEP_RUNNING:-0}"

ARTEMIS_ADMIN_USER="artemis_admin"           # internal admin baked into the Artemis test config
ARTEMIS_ADMIN_PASS="artemis_admin"
BENCH_LOGIN_USER="admin"                     # default benchmark webapp login
BENCH_LOGIN_PASS="admin"

# --------------------------------------------------------------------------------------------------
# Derived paths / constants
# --------------------------------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
TS="$(date +%Y%m%d-%H%M%S)"
REPORT="$RESULTS_DIR/loadtest-${NUM_USERS}users-${TS}.txt"
BENCH_LOG="$RESULTS_DIR/benchmark-${TS}.log"
BENCH_PG="artemis-loadtest-bench-pg"
ARTEMIS_URL="http://localhost:${LB_PORT}"
BENCH_URL="http://localhost:${BENCH_PORT}"
COMPOSE=(docker compose -f "$SCRIPT_DIR/multi-node-artemis.yml")
CURL=(curl -fsS)
BOOTRUN_PID=""

export ARTEMIS_DIR ARTEMIS_BUILD_ARCH ARTEMIS_NODE_XMX

log()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[warn] %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31m[error] %s\033[0m\n' "$*" >&2; exit 1; }

# --------------------------------------------------------------------------------------------------
# Teardown (always runs unless KEEP_RUNNING=1)
# --------------------------------------------------------------------------------------------------
cleanup() {
  local code=$?
  if [[ "$KEEP_RUNNING" == "1" ]]; then
    log "KEEP_RUNNING=1 — leaving the stack up. Tear down later with:"
    echo "    (cd '$SCRIPT_DIR' && docker compose -f multi-node-artemis.yml down -v) && docker rm -f $BENCH_PG; pkill -f ArtemisBenchmarkingApp"
    return
  fi
  log "Tearing down"
  [[ -n "$BOOTRUN_PID" ]] && kill "$BOOTRUN_PID" 2>/dev/null || true
  pkill -f "ArtemisBenchmarkingApp" 2>/dev/null || true
  pkill -f "bootRun" 2>/dev/null || true
  docker rm -f "$BENCH_PG" >/dev/null 2>&1 || true
  (cd "$SCRIPT_DIR" && "${COMPOSE[@]}" down -v >/dev/null 2>&1) || true
  exit $code
}
trap cleanup EXIT INT TERM

# --------------------------------------------------------------------------------------------------
# Translate the two-dimension work mix into the four absolute mechanism percentages the simulation
# expects (onlineIde + token + password + ssh = 100). In the benchmark, "online IDE" is itself one
# of the four mechanisms, so the offline auth split is applied to the (100 - online) offline share.
# Results are exposed as ONLINE_ABS / TOKEN_ABS / PASSWORD_ABS / SSH_ABS.
# --------------------------------------------------------------------------------------------------
port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") >/dev/null 2>&1; }

# SSH git needs host port 7921 (the benchmark hardcodes it). Enable the SSH overlay only if SSH
# students are requested AND 7921 is free; otherwise fold the SSH share into token so the run still
# completes the requested number of users.
decide_ssh() {
  local offline=$(( 100 - ONLINE_IDE_PCT ))
  if (( OFFLINE_SSH_PCT > 0 && offline > 0 )); then
    if port_in_use 7921; then
      warn "Host port 7921 is already in use (a local Artemis instance?); SSH git cannot be exposed."
      warn "Folding the ${OFFLINE_SSH_PCT}% SSH share into token auth for this run. Free 7921 to test SSH."
      OFFLINE_TOKEN_PCT=$(( OFFLINE_TOKEN_PCT + OFFLINE_SSH_PCT ))
      OFFLINE_SSH_PCT=0
    else
      COMPOSE+=(-f "$SCRIPT_DIR/multi-node-ssh.yml")
      log "SSH git enabled (host port 7921 is free)"
    fi
  fi
}

compute_mix() {
  (( ONLINE_IDE_PCT >= 0 && ONLINE_IDE_PCT <= 100 )) || die "ONLINE_IDE_PCT must be between 0 and 100 (is $ONLINE_IDE_PCT)"
  local offline=$(( 100 - ONLINE_IDE_PCT ))
  if (( offline > 0 )); then
    (( OFFLINE_TOKEN_PCT + OFFLINE_PASSWORD_PCT + OFFLINE_SSH_PCT == 100 )) ||
      die "OFFLINE_TOKEN_PCT + OFFLINE_PASSWORD_PCT + OFFLINE_SSH_PCT must sum to 100 (is $((OFFLINE_TOKEN_PCT+OFFLINE_PASSWORD_PCT+OFFLINE_SSH_PCT)))"
  fi
  ONLINE_ABS=$ONLINE_IDE_PCT
  TOKEN_ABS=$(( offline * OFFLINE_TOKEN_PCT / 100 ))
  PASSWORD_ABS=$(( offline * OFFLINE_PASSWORD_PCT / 100 ))
  SSH_ABS=$(( offline - TOKEN_ABS - PASSWORD_ABS ))   # remainder, so the four always sum to exactly 100
  (( ONLINE_ABS + TOKEN_ABS + PASSWORD_ABS + SSH_ABS == 100 )) || die "internal error: computed mix does not sum to 100"
  log "Work mix: ${NUM_USERS} users | online-IDE ${ONLINE_ABS}% | token ${TOKEN_ABS}% | password ${PASSWORD_ABS}% | ssh ${SSH_ABS}%"
}

# --------------------------------------------------------------------------------------------------
# Preflight
# --------------------------------------------------------------------------------------------------
preflight() {
  log "Preflight checks"
  command -v docker >/dev/null || die "docker not found"
  docker info >/dev/null 2>&1 || die "docker daemon not reachable"
  command -v python3 >/dev/null || die "python3 not found"
  [[ -d "$ARTEMIS_DIR/docker/artemis/config" ]] || die "ARTEMIS_DIR invalid: $ARTEMIS_DIR (expected Artemis checkout with docker/artemis/config)"
  for f in prod-multinode.env node1.env node2.env node3.env postgres.env; do
    [[ -f "$ARTEMIS_DIR/docker/artemis/config/$f" ]] || die "missing Artemis env file: $f"
  done
  local mem_bytes mem_gb
  mem_bytes="$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0)"
  mem_gb=$(( mem_bytes / 1024 / 1024 / 1024 ))
  if (( mem_gb > 0 && mem_gb < 14 )); then
    warn "Docker has only ${mem_gb} GB allocated. A 3-node Artemis cluster needs ~14-16 GB; raise Docker Desktop memory if nodes crash."
  else
    log "Docker memory: ${mem_gb} GB"
  fi
  mkdir -p "$RESULTS_DIR"
  decide_ssh
  compute_mix
}

# --------------------------------------------------------------------------------------------------
# Bring up the multi-node Artemis cluster
# --------------------------------------------------------------------------------------------------
start_artemis() {
  log "Starting multi-node Artemis cluster (clean state)"
  cd "$SCRIPT_DIR"
  "${COMPOSE[@]}" down -v >/dev/null 2>&1 || true
  "${COMPOSE[@]}" up -d

  log "Waiting for core nodes node-1 and node-2 to become healthy (cold start can take several minutes)"
  local start now cid1 cid2 h1 h2
  start=$(date +%s)
  while true; do
    cid1="$("${COMPOSE[@]}" ps -q artemis-app-node-1 2>/dev/null || true)"
    cid2="$("${COMPOSE[@]}" ps -q artemis-app-node-2 2>/dev/null || true)"
    h1="$([[ -n "$cid1" ]] && docker inspect -f '{{.State.Health.Status}}' "$cid1" 2>/dev/null || echo starting)"
    h2="$([[ -n "$cid2" ]] && docker inspect -f '{{.State.Health.Status}}' "$cid2" 2>/dev/null || echo starting)"
    now=$(date +%s)
    printf '\r    [%4ds] node-1=%-10s node-2=%-10s' "$((now-start))" "$h1" "$h2"
    [[ "$h1" == "healthy" && "$h2" == "healthy" ]] && { echo; break; }
    (( now-start > 1200 )) && { echo; die "Artemis nodes did not become healthy within 20 min"; }
    sleep 5
  done

  log "Waiting for load balancer readiness at $ARTEMIS_URL"
  start=$(date +%s)
  until "${CURL[@]}" -m 5 "$ARTEMIS_URL/management/health/readiness" 2>/dev/null | grep -q '"status":"UP"'; do
    (( $(date +%s)-start > 180 )) && die "Load balancer not ready"
    sleep 3
  done
  log "Artemis cluster is up and load-balanced on $ARTEMIS_URL"
}

# --------------------------------------------------------------------------------------------------
# Bring up the benchmark (own Postgres + the app from source)
# --------------------------------------------------------------------------------------------------
start_benchmark() {
  log "Starting benchmark Postgres"
  docker rm -f "$BENCH_PG" >/dev/null 2>&1 || true
  docker run -d --name "$BENCH_PG" \
    -e POSTGRES_DB=benchmarking -e POSTGRES_USER=benchmarking -e POSTGRES_PASSWORD=benchmarking \
    -p "${BENCH_DB_PORT}:5432" postgres:18-alpine >/dev/null
  local i
  for i in $(seq 1 30); do
    docker exec "$BENCH_PG" pg_isready -U benchmarking -d benchmarking >/dev/null 2>&1 && break
    sleep 2
  done

  log "Starting benchmark app from source (logs: $BENCH_LOG)"
  pkill -f "ArtemisBenchmarkingApp" 2>/dev/null || true
  pkill -f "bootRun" 2>/dev/null || true
  sleep 2
  ( cd "$REPO_ROOT" && nohup env \
      SERVER_PORT="$BENCH_PORT" \
      SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${BENCH_DB_PORT}/benchmarking" \
      SPRING_DATASOURCE_USERNAME=benchmarking SPRING_DATASOURCE_PASSWORD=benchmarking \
      SPRING_LIQUIBASE_URL="jdbc:postgresql://localhost:${BENCH_DB_PORT}/benchmarking" \
      SPRING_LIQUIBASE_USER=benchmarking SPRING_LIQUIBASE_PASSWORD=benchmarking \
      ARTEMIS_LOCAL_URL="${ARTEMIS_URL}/" \
      ./gradlew bootRun -x webapp --console=plain > "$BENCH_LOG" 2>&1 & echo $! > "$RESULTS_DIR/.bootrun.pid" )
  BOOTRUN_PID="$(cat "$RESULTS_DIR/.bootrun.pid")"

  local start
  start=$(date +%s)
  until [[ "$("${CURL[@]}" -m 5 -o /dev/null -w '%{http_code}' "$BENCH_URL/management/health" 2>/dev/null || echo 000)" == "200" ]]; do
    (( $(date +%s)-start > 300 )) && { tail -30 "$BENCH_LOG"; die "Benchmark app did not start"; }
    sleep 5
  done
  log "Benchmark app is up on $BENCH_URL"
}

bench_token() {
  "${CURL[@]}" -m 15 -H 'Content-Type: application/json' -X POST "$BENCH_URL/api/authenticate" \
    --data "{\"username\":\"$BENCH_LOGIN_USER\",\"password\":\"$BENCH_LOGIN_PASS\",\"rememberMe\":true}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["id_token"])'
}

# --------------------------------------------------------------------------------------------------
# Seed admin + create N benchmark users on Artemis
# --------------------------------------------------------------------------------------------------
provision_users() {
  log "Seeding admin user and creating $NUM_USERS Artemis users"
  docker exec "$BENCH_PG" psql -U benchmarking -d benchmarking -c \
    "INSERT INTO artemis_user (server_wide_id, username, password, server) VALUES (0,'$ARTEMIS_ADMIN_USER','$ARTEMIS_ADMIN_PASS','LOCAL') ON CONFLICT DO NOTHING;" >/dev/null
  local tok; tok="$(bench_token)"
  "${CURL[@]}" -m 600 -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
    -X POST "$BENCH_URL/api/artemis-users/LOCAL/create-by-pattern" --data "$(cat <<JSON
{"usernamePattern":"load_student_{i}","passwordPattern":"Artemis.Test.{i}",
 "firstNamePattern":"Load{i}","lastNamePattern":"Student{i}","emailPattern":"load_student_{i}@example.com",
 "from":1,"to":$((NUM_USERS+1)),"createOnArtemis":true}
JSON
)" >/dev/null
  local count
  count="$(docker exec "$BENCH_PG" psql -U benchmarking -d benchmarking -tAc "SELECT count(*) FROM artemis_user WHERE server='LOCAL'")"
  log "Artemis users in benchmark DB: $count"
}

# --------------------------------------------------------------------------------------------------
# Create + run the simulation, wait for completion
# --------------------------------------------------------------------------------------------------
run_simulation() {
  log "Creating and running the $NUM_USERS-user simulation"
  local tok sid; tok="$(bench_token)"
  sid="$("${CURL[@]}" -m 20 -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
    -X POST "$BENCH_URL/api/simulations" --data "$(cat <<JSON
{"name":"local-loadtest-${TS}","numberOfUsers":$NUM_USERS,"examId":0,"courseId":0,"server":"LOCAL",
 "mode":"CREATE_COURSE_AND_EXAM","customizeUserRange":false,
 "ideType":"OFFLINE","onlineIdePercentage":$ONLINE_ABS,"passwordPercentage":$PASSWORD_ABS,
 "tokenPercentage":$TOKEN_ABS,"sshPercentage":$SSH_ABS,
 "numberOfCommitsAndPushesFrom":$COMMITS_FROM,"numberOfCommitsAndPushesTo":$COMMITS_TO}
JSON
)" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')"
  log "Simulation id=$sid — launching"
  "${CURL[@]}" -m 30 -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
    -X POST "$BENCH_URL/api/simulations/$sid/run" \
    --data "{\"username\":\"$ARTEMIS_ADMIN_USER\",\"password\":\"$ARTEMIS_ADMIN_PASS\"}" >/dev/null

  log "Waiting for the simulation run to finish"
  local start status
  start=$(date +%s)
  while true; do
    status="$(docker exec "$BENCH_PG" psql -U benchmarking -d benchmarking -tAc \
      "SELECT status FROM simulation_run ORDER BY id DESC LIMIT 1" 2>/dev/null | tr -d ' ')"
    printf '\r    [%4ds] status=%-10s' "$(( $(date +%s)-start ))" "${status:-?}"
    [[ "$status" == "FINISHED" || "$status" == "FAILED" ]] && { echo; break; }
    (( $(date +%s)-start > 2400 )) && { echo; die "Simulation did not finish within 40 min"; }
    sleep 5
  done
  if [[ "$status" == "FAILED" ]]; then
    warn "Simulation reported FAILED — see report and $BENCH_LOG"
  fi
}

# --------------------------------------------------------------------------------------------------
# Collect and report results
# --------------------------------------------------------------------------------------------------
collect_results() {
  log "Collecting results -> $REPORT"
  local errors run_secs peak
  errors="$(grep -c 'Artemis request failed' "$BENCH_LOG" 2>/dev/null || true)"
  errors="${errors:-0}"
  run_secs="$(docker exec "$BENCH_PG" psql -U benchmarking -d benchmarking -tAc \
    "SELECT ROUND(EXTRACT(EPOCH FROM (end_date_time-start_date_time))) FROM simulation_run ORDER BY id DESC LIMIT 1" 2>/dev/null | tr -d ' ')"
  peak="$(docker exec "$BENCH_PG" psql -U benchmarking -d benchmarking -tAc \
    "SELECT max(b.number_of_requests) FROM stats_by_second b JOIN simulation_stats s ON b.simulation_stats_id=s.id WHERE s.request_type='TOTAL'" 2>/dev/null | tr -d ' ')"

  {
    echo "Artemis Benchmark — local multi-node load test"
    echo "=============================================="
    echo "Timestamp        : $TS"
    echo "Users            : $NUM_USERS"
    echo "IDE split        : online code editor ${ONLINE_IDE_PCT}% | offline IDE / git $((100 - ONLINE_IDE_PCT))%"
    echo "Offline git auth : token ${OFFLINE_TOKEN_PCT}% | password ${OFFLINE_PASSWORD_PCT}% | ssh ${OFFLINE_SSH_PCT}% (of the offline share)"
    echo "Mechanism mix    : online-IDE ${ONLINE_ABS}% | token ${TOKEN_ABS}% | password ${PASSWORD_ABS}% | ssh ${SSH_ABS}% (of all students)"
    echo "Commits/student  : [$COMMITS_FROM, $COMMITS_TO)"
    echo "Artemis topology : 2 core nodes + 1 build agent, nginx LB, shared Postgres + ActiveMQ broker + registry"
    echo "Wall clock (run) : ${run_secs:-?} s"
    echo "Peak throughput  : ${peak:-?} requests/s"
    echo "Failed requests  : $errors"
    echo
    echo "Per request type (avg response time, ascending by type):"
    docker exec "$BENCH_PG" psql -U benchmarking -d benchmarking -c \
      "SELECT request_type AS \"Request type\",
              number_of_requests AS \"Count\",
              ROUND(avg_response_time/1e6) AS \"Avg ms\",
              (SELECT ROUND(max(b.avg_response_time)/1e6)
                 FROM stats_by_second b WHERE b.simulation_stats_id = s.id) AS \"Worst-sec avg ms\",
              (SELECT max(b.number_of_requests)
                 FROM stats_by_second b WHERE b.simulation_stats_id = s.id) AS \"Peak req/s\"
         FROM simulation_stats s
        WHERE simulation_run_id = (SELECT max(id) FROM simulation_run)
        ORDER BY avg_response_time DESC" 2>/dev/null
  } | tee "$REPORT"

  echo
  log "Report written to: $REPORT"
  if [[ "$errors" != "0" ]]; then
    warn "$errors failed requests were logged — inspect $BENCH_LOG"
  fi
}

# --------------------------------------------------------------------------------------------------
main() {
  preflight
  start_artemis
  start_benchmark
  provision_users
  run_simulation
  collect_results
  log "Done."
}
main "$@"
