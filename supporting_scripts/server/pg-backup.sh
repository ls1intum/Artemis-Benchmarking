#!/usr/bin/env bash
#
# Dumps the production database, verifies the dump, keeps the last RETENTION_DAYS days and optionally copies the
# new dump to another machine.
#
# Runs as root on the production host, started by artemis-benchmarking-backup.timer (see install.sh).
#
# The dump contains the credentials the simulation uses to log in to the Artemis test servers, so it is only readable
# by root and must never be uploaded anywhere public (for example as a GitHub Actions artifact).
#
# Settings (read from /etc/artemis-benchmarking-backup.env if that file exists):
#   APP_DIR          directory with the compose files           (default /opt/artemis-benchmarking)
#   BACKUP_DIR       where the dumps are kept                   (default /var/backups/artemis-benchmarking)
#   RETENTION_DAYS   how long dumps are kept                    (default 14)
#   BACKUP_REMOTE    optional rsync destination for an off-host copy, e.g. user@backup-host:/srv/backups/benchmarking/
#                    A backup that lives only on the machine it protects does not survive losing that machine.
set -euo pipefail

[[ -f /etc/artemis-benchmarking-backup.env ]] && source /etc/artemis-benchmarking-backup.env

APP_DIR=${APP_DIR:-/opt/artemis-benchmarking}
BACKUP_DIR=${BACKUP_DIR:-/var/backups/artemis-benchmarking}
RETENTION_DAYS=${RETENTION_DAYS:-14}
BACKUP_REMOTE=${BACKUP_REMOTE:-}

compose() {
    docker compose -f "$APP_DIR/docker-compose.yml" -f "$APP_DIR/docker-compose.prod.yml" "$@"
}

umask 077
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
FINAL="$BACKUP_DIR/benchmarking-$TIMESTAMP.dump"
PARTIAL="$FINAL.partial"
trap 'rm -f "$PARTIAL"' EXIT

echo "Dumping the database to $FINAL"
# The credentials only exist inside the container, so pg_dump runs there and streams the custom-format dump out.
compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$PARTIAL"

# A dump that cannot be listed cannot be restored either: never keep one, and never let it replace a good one.
TABLES=$(compose exec -T postgres pg_restore --list < "$PARTIAL" | grep -c 'TABLE DATA' || true)
if [[ $TABLES -lt 1 ]]; then
    echo "The new dump contains no table data; keeping the previous backups untouched" >&2
    exit 1
fi

mv "$PARTIAL" "$FINAL"
echo "Backup written: $(du -h "$FINAL" | cut -f1), $TABLES tables with data"

# Only delete old dumps after a new one has been verified.
find "$BACKUP_DIR" -maxdepth 1 -name 'benchmarking-*.dump' -mtime "+$RETENTION_DAYS" -print -delete

if [[ -n $BACKUP_REMOTE ]]; then
    echo "Copying to $BACKUP_REMOTE"
    rsync -a --chmod=F600 "$FINAL" "$BACKUP_REMOTE"
else
    echo "BACKUP_REMOTE is not set: the backup exists only on this host" >&2
fi
