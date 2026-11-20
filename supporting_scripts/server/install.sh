#!/usr/bin/env bash
#
# Installs the host-level configuration of the production server: the nightly database backup and the unattended
# security updates including the maintenance reboot. Idempotent; run it as root ON THE SERVER from this directory:
#
#   sudo ./install.sh
#
# or from a development machine:
#
#   rsync -a supporting_scripts/server/ benchmark:/tmp/artemis-server/ && ssh benchmark 'cd /tmp/artemis-server && sudo ./install.sh'
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
    echo "Run as root" >&2
    exit 1
fi

HERE=$(cd "$(dirname "$0")" && pwd)
APP_DIR=/opt/artemis-benchmarking

echo "== Database backup"
install -d -m 755 "$APP_DIR/server"
install -m 755 "$HERE/pg-backup.sh" "$APP_DIR/server/pg-backup.sh"
install -m 644 "$HERE/artemis-benchmarking-backup.service" /etc/systemd/system/artemis-benchmarking-backup.service
install -m 644 "$HERE/artemis-benchmarking-backup.timer" /etc/systemd/system/artemis-benchmarking-backup.timer
if [[ ! -f /etc/artemis-benchmarking-backup.env ]]; then
    cat > /etc/artemis-benchmarking-backup.env <<'ENV'
# Settings of pg-backup.sh. Uncomment BACKUP_REMOTE to also copy every dump to another machine (rsync over SSH).
#RETENTION_DAYS=14
#BACKUP_REMOTE=user@backup-host:/srv/backups/artemis-benchmarking/
ENV
    chmod 600 /etc/artemis-benchmarking-backup.env
fi
systemctl daemon-reload
systemctl enable --now artemis-benchmarking-backup.timer

echo "== Unattended security updates"
install -m 644 "$HERE/52artemis-unattended-upgrades" /etc/apt/apt.conf.d/52artemis-unattended-upgrades
# Fail now, not at 04:30, if the configuration is broken.
unattended-upgrade --dry-run --debug 2>&1 | grep -E "Allowed origins|Docker" | head -5 || true

echo "== Unused LDAP name service daemon"
# pynslcd has no configuration and fails on every boot. /etc/nsswitch.conf resolves users from local files only,
# so nothing uses it; the pam_ldap entries ignore a missing daemon (default=ignore).
if systemctl list-unit-files pynslcd.service >/dev/null 2>&1 && ! grep -qE '^(passwd|group|shadow):.*ldap' /etc/nsswitch.conf; then
    systemctl disable pynslcd.service 2>/dev/null || true
    systemctl reset-failed pynslcd.service 2>/dev/null || true
fi

echo
systemctl list-timers artemis-benchmarking-backup.timer --no-pager
echo "Done."
