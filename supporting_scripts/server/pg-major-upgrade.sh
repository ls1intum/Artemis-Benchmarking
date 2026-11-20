#!/usr/bin/env bash
#
# Upgrades the production PostgreSQL to a new MAJOR version (for example 18 -> 19) with a dump and restore, and proves
# that nothing was lost before the application is started again.
#
# Why dump and restore into a NEW volume: it is simple and independent of the old binaries. The new major version gets
# its own volume (<name>-pg<major>) and the old volume is never touched, which makes the rollback trivial. A shared
# volume does not work: the official image refuses to start when it finds the data of another major version in
# /var/lib/postgresql ("there appears to be PostgreSQL data in /var/lib/postgresql/18/docker"). The rehearsal of this
# script on a copy of the production data found that.
#
# What it does:
#   1. counts the rows of every table (exact, not estimated)
#   2. stops the application, so nothing is written any more
#   3. takes a verified backup with pg-backup.sh
#   4. stops PostgreSQL, switches the image AND the volume in docker-compose.yml, starts the new major version on an
#      empty cluster in the new volume
#   5. restores the backup and compares the row counts with step 1; it aborts, WITHOUT starting the application, on any
#      difference
#   6. starts the application and waits until it is healthy
#
# Run as root on the production host:   sudo ./pg-major-upgrade.sh postgres:19.0-alpine
# Downtime is the time between 2 and 6, a few minutes for a database of this size.
#
# Rollback (also printed at the end): put docker-compose.yml.pre-upgrade back and start PostgreSQL and the application.
# The old volume is untouched, but it does not contain what was written after the upgrade. Remove it with
# `docker volume rm <old volume>` only when you are sure you will not go back.
set -euo pipefail

NEW_IMAGE=${1:-}
APP_DIR=${APP_DIR:-/opt/artemis-benchmarking}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/artemis-benchmarking}
HEALTH_TIMEOUT_SECONDS=${HEALTH_TIMEOUT_SECONDS:-900}

if [[ $EUID -ne 0 || -z $NEW_IMAGE || ! $NEW_IMAGE =~ ^postgres:[0-9]+ ]]; then
    echo "Usage (as root): $0 postgres:<new version>   e.g. postgres:19.0-alpine" >&2
    exit 2
fi
NEW_MAJOR=$(sed -E 's/^postgres:([0-9]+).*/\1/' <<< "$NEW_IMAGE")

cd "$APP_DIR"
compose() { docker compose -f docker-compose.yml -f docker-compose.prod.yml "$@"; }
psql_in_container() { compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 "$@"' sh "$@"; }

say() { printf '\n== %s\n' "$*"; }
fail() { printf '\n!! %s\n' "$*" >&2; exit 1; }

# Exact row count of every table, one "table|count" line each.
row_counts() {
    printf "%s\n\\\\gexec\n" "select format('select %L, count(*) from %I.%I', schemaname || '.' || tablename, schemaname, tablename) from pg_tables where schemaname not in ('pg_catalog', 'information_schema') order by 1" \
        | compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -F"|" -v ON_ERROR_STOP=1'
}

say "Checking the current state"
CURRENT_IMAGE=$(compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["postgres"]["image"])')
CURRENT_MAJOR=$(compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "show server_version_num"' | cut -c1-2)
echo "Running: $CURRENT_IMAGE (major $CURRENT_MAJOR), target: $NEW_IMAGE (major $NEW_MAJOR)"
[[ $CURRENT_MAJOR != "$NEW_MAJOR" ]] || fail "The database already runs major version $NEW_MAJOR"
[[ $NEW_MAJOR -gt $CURRENT_MAJOR ]] || fail "This script only upgrades; $NEW_MAJOR is older than $CURRENT_MAJOR"
# The volume that holds the data now, found in the compose files rather than assumed.
OLD_VOLUME=$(compose config --format json | python3 -c '
import json, sys
config = json.load(sys.stdin)
mounts = [v for v in config["services"]["postgres"].get("volumes", []) if v.get("target") == "/var/lib/postgresql"]
if len(mounts) != 1:
    sys.exit("expected exactly one volume mounted at /var/lib/postgresql")
key = mounts[0]["source"]
name = config.get("volumes", {}).get(key, {}).get("name", key)
if name != key:
    sys.exit("the volume key and its name differ; edit the compose file by hand")
print(name)
')
NEW_VOLUME="$(sed -E 's/-pg[0-9]+$//' <<< "$OLD_VOLUME")-pg$NEW_MAJOR"
OLD_VOLUME_DIR=$(docker volume inspect "$OLD_VOLUME" --format '{{.Mountpoint}}')
[[ -d $OLD_VOLUME_DIR/$CURRENT_MAJOR/docker ]] || fail "Expected the data in $OLD_VOLUME_DIR/$CURRENT_MAJOR/docker; this script relies on the per-major directory layout of the official image"
if docker volume inspect "$NEW_VOLUME" > /dev/null 2>&1; then
    fail "The volume $NEW_VOLUME already exists; refusing to reuse it. Remove it first if it is a leftover: docker volume rm $NEW_VOLUME"
fi
NEEDED_KB=$(( $(du -sk "$OLD_VOLUME_DIR" | cut -f1) * 4 + 1048576 ))
AVAILABLE_KB=$(df -k --output=avail "$OLD_VOLUME_DIR" | tail -n 1)
[[ $AVAILABLE_KB -gt $NEEDED_KB ]] || fail "Not enough free disk space (need about $((NEEDED_KB / 1024)) MB)"
echo "Data volume: $OLD_VOLUME -> new volume: $NEW_VOLUME"
docker pull "$NEW_IMAGE" > /dev/null
echo "Checks passed."

say "Counting the rows of every table"
WORK=$(mktemp -d)
row_counts > "$WORK/before.txt"
echo "$(wc -l < "$WORK/before.txt") tables, $(awk -F'|' '{s+=$2} END {print s}' "$WORK/before.txt") rows"

say "Stopping the application"
compose stop benchmarking

say "Taking the backup"
BACKUP_FILE_BEFORE=$(ls -t "$BACKUP_DIR"/benchmarking-*.dump 2>/dev/null | head -n 1 || true)
"$APP_DIR/server/pg-backup.sh"
BACKUP_FILE=$(ls -t "$BACKUP_DIR"/benchmarking-*.dump | head -n 1)
[[ $BACKUP_FILE != "$BACKUP_FILE_BEFORE" ]] || fail "No new backup file was written"
cp "$BACKUP_FILE" "$WORK/upgrade.dump"
echo "Backup: $BACKUP_FILE"

say "Switching to $NEW_IMAGE on the volume $NEW_VOLUME"
compose stop postgres
cp docker-compose.yml docker-compose.yml.pre-upgrade
sed -i -E "s#^(\s*image:\s*)postgres:[^ ]+#\1$NEW_IMAGE#; s#$OLD_VOLUME#$NEW_VOLUME#g" docker-compose.yml
grep -nE "image: postgres|$NEW_VOLUME" docker-compose.yml
compose up -d postgres
for _ in $(seq 1 120); do
    [[ $(docker inspect --format '{{.State.Health.Status}}' "$(compose ps -q postgres)") == healthy ]] && break
    sleep 5
done
[[ $(docker inspect --format '{{.State.Health.Status}}' "$(compose ps -q postgres)") == healthy ]] || fail "The new PostgreSQL did not become healthy; see: docker compose logs postgres"
RUNNING_MAJOR=$(compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "show server_version_num"' | cut -c1-2)
[[ $RUNNING_MAJOR == "$NEW_MAJOR" ]] || fail "The new container runs major $RUNNING_MAJOR"
NEW_VOLUME_DIR=$(docker volume inspect "$NEW_VOLUME" --format '{{.Mountpoint}}')
[[ -d $NEW_VOLUME_DIR/$NEW_MAJOR/docker ]] || fail "Expected the new cluster in $NEW_VOLUME_DIR/$NEW_MAJOR/docker"
TABLES_IN_NEW=$(row_counts | wc -l)
[[ $TABLES_IN_NEW -eq 0 ]] || fail "The new database is not empty ($TABLES_IN_NEW tables); refusing to restore into it"

say "Restoring"
compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < "$WORK/upgrade.dump"

say "Comparing row counts"
row_counts > "$WORK/after.txt"
if ! diff "$WORK/before.txt" "$WORK/after.txt"; then
    fail "The row counts differ (see above). The application was NOT started. Rollback: cp docker-compose.yml.pre-upgrade docker-compose.yml && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres benchmarking"
fi
echo "All $(wc -l < "$WORK/after.txt") tables have exactly the same number of rows."

say "Starting the application"
compose up -d benchmarking
for _ in $(seq 1 $((HEALTH_TIMEOUT_SECONDS / 5))); do
    [[ $(docker inspect --format '{{.State.Health.Status}}' "$(compose ps -q benchmarking)") == healthy ]] && break
    sleep 5
done
STATUS=$(docker inspect --format '{{.State.Health.Status}}' "$(compose ps -q benchmarking)")
[[ $STATUS == healthy ]] || fail "The application is $STATUS after ${HEALTH_TIMEOUT_SECONDS}s; see: docker compose logs benchmarking"

say "Done: PostgreSQL major $CURRENT_MAJOR -> $NEW_MAJOR"
echo "Backup taken before the upgrade: $BACKUP_FILE"
echo "Old data volume (untouched): $OLD_VOLUME. Remove it once you are sure: docker volume rm $OLD_VOLUME"
echo "Rollback: cp $APP_DIR/docker-compose.yml.pre-upgrade $APP_DIR/docker-compose.yml && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres benchmarking"
rm -rf "$WORK"
