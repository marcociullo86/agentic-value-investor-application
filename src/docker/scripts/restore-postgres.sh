#!/usr/bin/env bash
# restore-postgres.sh — manual point-in-time restore from pg_dump backup
# [^src: design_&_architecture/decisions/ADR-015-deployment-target-r11.md §Backup PostgreSQL]
# [^src: management/kanban/EP-008-deploy-operativita-produzione/US-027-backup-db-retention-log/TSK-063.md]
#
# WARNING: This script DROPS and RECREATES the target database.
#          Run only during a controlled maintenance window.
#          Stop vi-app before restoring to prevent writes during restore.
#
# Usage:
#   BACKUP_FILE=/var/backups/postgres/vi-postgres-20260101-030000.sql.gz \
#   POSTGRES_PASSWORD=<secret> \
#   ./restore-postgres.sh
#
# Environment variables:
#   BACKUP_FILE       — path to .sql.gz backup file         (REQUIRED)
#   POSTGRES_HOST     — postgres container hostname          (default: vi-postgres)
#   POSTGRES_PORT     — postgres port                        (default: 5432)
#   POSTGRES_DB       — target database name                 (default: value_investing_prod)
#   POSTGRES_USER     — database user                        (default: vi_prod_user)
#   POSTGRES_PASSWORD — database password                    (REQUIRED)

set -euo pipefail

BACKUP_FILE="${BACKUP_FILE:?env BACKUP_FILE required (path to .sql.gz)}"
POSTGRES_HOST="${POSTGRES_HOST:-vi-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-value_investing_prod}"
POSTGRES_USER="${POSTGRES_USER:-vi_prod_user}"
PGPASSWORD="${POSTGRES_PASSWORD:?env POSTGRES_PASSWORD required}"
export PGPASSWORD

# Validate backup file exists and is readable
if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "[ERROR] Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

echo "========================================================"
echo "  RESTORE WARNING"
echo "========================================================"
echo "  Host:     ${POSTGRES_HOST}:${POSTGRES_PORT}"
echo "  Database: ${POSTGRES_DB}"
echo "  User:     ${POSTGRES_USER}"
echo "  Backup:   ${BACKUP_FILE}"
echo ""
echo "  This will DROP and RECREATE database '${POSTGRES_DB}'."
echo "  All existing data will be PERMANENTLY LOST."
echo ""
echo "  Pre-restore checklist:"
echo "    [ ] vi-app container stopped"
echo "    [ ] Backup file integrity verified (size > 0)"
echo "    [ ] Maintenance window communicated"
echo ""
read -r -p "Type 'yes-restore' to confirm: " CONFIRM

if [[ "${CONFIRM}" != "yes-restore" ]]; then
  echo "[ABORT] Restore cancelled by operator."
  exit 0
fi

echo "[$(date -u +%FT%TZ)] restore start: ${BACKUP_FILE} → ${POSTGRES_DB}"

# Terminate active connections to the database
psql \
  --host="${POSTGRES_HOST}" \
  --port="${POSTGRES_PORT}" \
  --username="${POSTGRES_USER}" \
  --dbname="postgres" \
  --command="SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${POSTGRES_DB}' AND pid <> pg_backend_pid();" \
  2>/dev/null || true

# Drop and recreate the database
psql \
  --host="${POSTGRES_HOST}" \
  --port="${POSTGRES_PORT}" \
  --username="${POSTGRES_USER}" \
  --dbname="postgres" \
  --command="DROP DATABASE IF EXISTS \"${POSTGRES_DB}\";"

psql \
  --host="${POSTGRES_HOST}" \
  --port="${POSTGRES_PORT}" \
  --username="${POSTGRES_USER}" \
  --dbname="postgres" \
  --command="CREATE DATABASE \"${POSTGRES_DB}\" OWNER \"${POSTGRES_USER}\";"

# Restore from gzipped plain SQL dump
echo "[$(date -u +%FT%TZ)] decompressing and restoring..."
gunzip -c "${BACKUP_FILE}" | psql \
  --host="${POSTGRES_HOST}" \
  --port="${POSTGRES_PORT}" \
  --username="${POSTGRES_USER}" \
  --dbname="${POSTGRES_DB}" \
  --quiet

echo "[$(date -u +%FT%TZ)] restore complete — database ${POSTGRES_DB} restored from ${BACKUP_FILE}"
echo ""
echo "Next steps:"
echo "  1. Run Flyway migration check (Flyway on app startup will reconcile schema state)"
echo "  2. Restart vi-app: docker compose -f docker-compose.prod.yml start vi-app"
echo "  3. Verify: curl -f https://app.example.com/actuator/health"
