#!/usr/bin/env bash
#
# Runs a command and, if it stops producing output for too long, writes a thread dump of every running JVM
# before the CI step is eventually killed by its timeout.
#
# Why: the integration tests have hung during Spring context startup on GitHub runners without a single log
# line, and GitHub only reveals the logs of a step after it has finished, so nothing explained the hang. A
# thread dump taken while the JVM is stuck shows which threads hold which locks.
#
# Usage: ci-run-with-watchdog.sh <name> <command...>
#   STALL_SECONDS   seconds without output that count as a hang (default 300)
#   DUMP_DIR        where the dumps and the log are written (default build/thread-dumps)
#
# The exit code is the exit code of the command; the watchdog never changes the outcome.
set -uo pipefail

if [[ $# -lt 2 ]]; then
    echo "Usage: $0 <name> <command...>" >&2
    exit 2
fi

NAME=$1
shift
STALL_SECONDS=${STALL_SECONDS:-300}
DUMP_DIR=${DUMP_DIR:-build/thread-dumps}
LOG="$DUMP_DIR/$NAME.log"

mkdir -p "$DUMP_DIR"
: > "$LOG"

mtime() {
    stat -c %Y "$1" 2>/dev/null || stat -f %m "$1"
}

dump_threads() {
    local round=$1 pid
    for pid in $(pgrep -x java || true); do
        {
            echo "=== $(date -u +%FT%TZ) pid=$pid round=$round"
            ps -o pid,etime,args -p "$pid" | cut -c1-300
            jcmd "$pid" Thread.print -l 2>&1 || true
        } >> "$DUMP_DIR/$NAME-threads.txt"
    done
}

watchdog() {
    while sleep 15; do
        if (( $(date +%s) - $(mtime "$LOG") >= STALL_SECONDS )); then
            echo "::warning::No output from '$NAME' for ${STALL_SECONDS}s; writing thread dumps to $DUMP_DIR"
            # Two dumps a little apart show whether threads are blocked or just slow.
            dump_threads 1
            sleep 30
            dump_threads 2
            return
        fi
    done
}

watchdog &
WATCHDOG=$!
trap 'kill "$WATCHDOG" 2>/dev/null || true' EXIT

"$@" 2>&1 | tee "$LOG"
exit "${PIPESTATUS[0]}"
