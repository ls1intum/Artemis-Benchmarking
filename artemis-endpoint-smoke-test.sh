#!/usr/bin/env bash
#
# Artemis endpoint smoke test.
#
# Authenticates against a real Artemis server with a benchmark test account and probes the
# REST endpoints the benchmark relies on. Fails (exit 1) if any endpoint returns 404 or 5xx —
# the signal that Artemis moved/renamed an endpoint and the benchmark is now out of date.
#
# This guards against the MySQL->Postgres-era endpoint drift we hit (Artemis API modularization):
# the unit tests mostly mock the HTTP layer, so only a live call catches a wrong path.
#
# Usage:
#   ARTEMIS_URL=https://artemis-test3.artemis.cit.tum.de \
#   SMOKE_USER=artemis_test_user_1 SMOKE_PASSWORD=... \
#   [SMOKE_ADMIN_USER=benchmark-admin SMOKE_ADMIN_PASSWORD=...] \
#   [SMOKE_COURSE_ID=123] \
#   ./artemis-endpoint-smoke-test.sh
set -uo pipefail

: "${ARTEMIS_URL:?set ARTEMIS_URL}"
: "${SMOKE_USER:?set SMOKE_USER}"
: "${SMOKE_PASSWORD:?set SMOKE_PASSWORD}"
ARTEMIS_URL="${ARTEMIS_URL%/}"

JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT
fail=0
declare -a FAILURES=()

# login <user> <password> <cookie-jar> -> sets global LOGIN_CODE
login() {
  curl -s -m 30 -o /dev/null -w "%{http_code}" -c "$3" \
    -H 'Content-Type: application/json' -H 'Accept: application/json' \
    -X POST "$ARTEMIS_URL/api/core/public/authenticate" \
    --data "{\"username\":\"$1\",\"password\":\"$2\",\"rememberMe\":true}"
}

# probe <label> <path> ; classifies the HTTP status of an authenticated GET
probe() {
  local label="$1" path="$2"
  local code
  code=$(curl -s -m 30 -o /dev/null -w "%{http_code}" -b "$JAR" \
    -H 'Accept: application/json' "$ARTEMIS_URL/$path")
  local verdict
  case "$code" in
    2*) verdict="PASS" ;;
    404|5*) verdict="FAIL"; fail=1; FAILURES+=("$label ($path) -> $code") ;;
    401|403) verdict="WARN(auth/forbidden)" ;;
    *) verdict="WARN($code)" ;;
  esac
  printf "  %-42s %-55s %s\n" "$label" "$path" "$code $verdict"
}

echo "== Artemis endpoint smoke test against $ARTEMIS_URL =="

echo "-- student authentication --"
code=$(login "$SMOKE_USER" "$SMOKE_PASSWORD" "$JAR")
if [[ ! "$code" =~ ^2 ]]; then
  echo "  AUTH FAILED for $SMOKE_USER -> $code"; exit 1
fi
echo "  authenticated as $SMOKE_USER ($code)"

echo "-- context-free endpoints --"
probe "account"                 "api/core/public/account"
probe "system-notifications"    "api/notification/public/system-notifications/active"
probe "global-notif-settings"   "api/notification/global-notification-settings"
probe "notification-info"       "api/notification/courses/info"
probe "courses/for-dashboard"   "api/course/courses/for-dashboard"
probe "courses/for-dropdown"    "api/course/courses/for-dropdown"
probe "science-settings"        "api/atlas/science-settings"
probe "ide-settings"            "api/programming/ide-settings"
probe "ssh-public-keys"         "api/programming/ssh-settings/public-keys"
probe "management/info"         "management/info"

# Derive a course id (for course-scoped endpoints) unless one was provided.
COURSE_ID="${SMOKE_COURSE_ID:-}"
if [[ -z "$COURSE_ID" ]]; then
  COURSE_ID=$(curl -s -m 30 -b "$JAR" -H 'Accept: application/json' \
    "$ARTEMIS_URL/api/course/courses/for-dropdown" | python3 - <<'PY' 2>/dev/null
import sys, json
def find_id(o):
    if isinstance(o, dict):
        if isinstance(o.get("id"), int): return o["id"]
        for v in o.values():
            r = find_id(v)
            if r is not None: return r
    if isinstance(o, list):
        for v in o:
            r = find_id(v)
            if r is not None: return r
    return None
try:
    print(find_id(json.load(sys.stdin)) or "")
except Exception:
    print("")
PY
)
fi

if [[ -n "$COURSE_ID" ]]; then
  echo "-- course-scoped endpoints (courseId=$COURSE_ID) --"
  probe "course/for-dashboard"     "api/course/courses/$COURSE_ID/for-dashboard"
  probe "unread-messages"          "api/communication/courses/$COURSE_ID/unread-messages"
  probe "notification-settings"    "api/notification/courses/$COURSE_ID/settings"
  probe "iris-status"              "api/iris/courses/$COURSE_ID/status"
  probe "iris-chat-sessions"       "api/iris/chat/courses/$COURSE_ID/sessions/overview"
else
  echo "-- course-scoped endpoints skipped: no course visible to $SMOKE_USER (set SMOKE_COURSE_ID) --"
fi

# Optional: verify the admin account can authenticate (admin endpoints are mutating, so we
# only smoke-test that auth works, not the create/cancel calls themselves).
if [[ -n "${SMOKE_ADMIN_USER:-}" && -n "${SMOKE_ADMIN_PASSWORD:-}" ]]; then
  echo "-- admin authentication --"
  ADMIN_JAR="$(mktemp)"; trap 'rm -f "$JAR" "$ADMIN_JAR"' EXIT
  acode=$(login "$SMOKE_ADMIN_USER" "$SMOKE_ADMIN_PASSWORD" "$ADMIN_JAR")
  if [[ "$acode" =~ ^2 ]]; then echo "  admin auth OK ($acode)"; else echo "  admin AUTH FAILED -> $acode"; fail=1; FAILURES+=("admin auth -> $acode"); fi
fi

echo
if [[ "$fail" -ne 0 ]]; then
  echo "SMOKE TEST FAILED — ${#FAILURES[@]} broken endpoint(s):"
  printf '  - %s\n' "${FAILURES[@]}"
  exit 1
fi
echo "SMOKE TEST PASSED — no 404/5xx from any probed endpoint."
