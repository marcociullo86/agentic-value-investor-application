#!/usr/bin/env bash
# backup-postgres.sh — pg_dump daily backup with 14-day retention
# [^src: design_&_architecture/decisions/ADR-015-deployment-target-r11.md §Backup PostgreSQL]
# [^src: management/kanban/EP-008-deploy-operativita-produzione/US-027-backup-db-retention-log/TSK-063.md]
#
# Execution modes:
#   a) Host cron:     Requires pg_dump (postgresql-client) installed on host.
#                     Example cron entry:
#                       0 3 * * * /opt/vi/scripts/backup-postgres.sh >> /var/log/vi-backup.log 2>&1
#
#   b) Via docker exec (recommended — no host pg client needed):
#                       0 3 * * * docker compose -f /opt/vi/src/docker/docker-compose.prod.yml \
#                         exec -T vi-postgres /opt/scripts/backup-postgres.sh \
#                         >> /var/log/vi-backup.log 2>&1
#
# Dependencies: pg_dump, gzip, find, stat
#
# Environment variables (all have defaults except POSTGRES_PASSWORD which is mandatory):
#   BACKUP_DIR                — destination directory          (default: /var/backups/postgres)
#   BACKUP_RETENTION_DAYS     — days to retain backups         (default: 14)
#   POSTGRES_HOST             — postgres container hostname    (default: vi-postgres)
#   POSTGRES_PORT             — postgres port                  (default: 5432)
#   POSTGRES_DB               — database name                  (default: value_investing_prod)
#   POSTGRES_USER             — database user                  (default: vi_prod_user)
#   POSTGRES_PASSWORD         — database password              (REQUIRED — no default)

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/var/backups/postgres}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
POSTGRES_HOST="${POSTGRES_HOST:-vi-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-value_investing_prod}"
POSTGRES_USER="${POSTGRES_USER:-vi_prod_user}"
PGPASSWORD="${POSTGRES_PASSWORD:?env POSTGRES_PASSWORD required}"
export PGPASSWORD

TIMESTAMP=$(date -u +"%Y%m%d-%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/vi-postgres-${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "[$(date -u +%FT%TZ)] backup start: ${BACKUP_FILE}"

pg_dump \
  --host="${POSTGRES_HOST}" \
  --port="${POSTGRES_PORT}" \
  --username="${POSTGRES_USER}" \
  --dbname="${POSTGRES_DB}" \
  --format=plain \
  --no-owner \
  --no-privileges \
  --verbose \
  2>/dev/null \
  | gzip --best > "${BACKUP_FILE}"

# Sanity check: file must be > 1 KB (non-trivial dump)
FILE_SIZE=$(stat -c%s "${BACKUP_FILE}" 2>/dev/null || stat -f%z "${BACKUP_FILE}")
if [[ "${FILE_SIZE}" -lt 1024 ]]; then
  echo "[ERROR] Backup file < 1KB, possible failure: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "[$(date -u +%FT%TZ)] backup OK: $(du -h "${BACKUP_FILE}" | cut -f1)"

# Retention: delete backups older than RETENTION_DAYS
echo "[$(date -u +%FT%TZ)] applying retention ${RETENTION_DAYS}d"
find "${BACKUP_DIR}" -name "vi-postgres-*.sql.gz" -type f -mtime "+${RETENTION_DAYS}" -delete -print

echo "[$(date -u +%FT%TZ)] backup process complete"
