# ops scripts — src/docker/scripts/

Operational scripts for the Value Investing production deployment (R1.1).
[^src: design_&_architecture/decisions/ADR-015-deployment-target-r11.md §Backup PostgreSQL]

## Scripts

| Script | Purpose |
|---|---|
| `backup-postgres.sh` | Daily pg_dump with 14-day retention |
| `restore-postgres.sh` | Manual point-in-time restore (maintenance window) |

---

## backup-postgres.sh

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `BACKUP_DIR` | `/var/backups/postgres` | Destination directory on host |
| `BACKUP_RETENTION_DAYS` | `14` | Days to keep backup files |
| `POSTGRES_HOST` | `vi-postgres` | Hostname of postgres container |
| `POSTGRES_PORT` | `5432` | Port |
| `POSTGRES_DB` | `value_investing_prod` | Database name |
| `POSTGRES_USER` | `vi_prod_user` | Database user |
| `POSTGRES_PASSWORD` | **(required)** | Database password — read from `.env.prod` |

### Cron setup (recommended)

Install on the host that runs Docker Compose. The cron entry runs daily at 03:00 UTC:

```cron
0 3 * * * POSTGRES_PASSWORD=$(grep POSTGRES_PASSWORD /opt/vi/src/docker/.env.prod | cut -d= -f2) BACKUP_DIR=/var/backups/postgres /opt/vi/src/docker/scripts/backup-postgres.sh >> /var/log/vi-backup.log 2>&1
```

Alternatively, source the `.env.prod` file first:

```bash
# /opt/vi/run-backup.sh  (wrapper sourcing secrets)
#!/usr/bin/env bash
set -a
source /opt/vi/src/docker/.env.prod
set +a
exec /opt/vi/src/docker/scripts/backup-postgres.sh
```

Then cron entry:

```cron
0 3 * * * /opt/vi/run-backup.sh >> /var/log/vi-backup.log 2>&1
```

### Backup directory setup

```bash
mkdir -p /var/backups/postgres
chmod 750 /var/backups/postgres
# postgres container uid is 999 by default:
chown 999:999 /var/backups/postgres
```

The backup volume is mounted in `docker-compose.prod.yml`:

```yaml
volumes:
  - ${BACKUP_DIR:-/var/backups/postgres}:/backups
```

### Smoke test

```bash
# Manual run (dry run to verify connectivity)
POSTGRES_PASSWORD=<secret> \
POSTGRES_HOST=localhost \
POSTGRES_PORT=5432 \
BACKUP_DIR=/tmp/vi-backup-test \
./src/docker/scripts/backup-postgres.sh

# Verify output
ls -lh /tmp/vi-backup-test/
# Expected: vi-postgres-YYYYMMDD-HHMMSS.sql.gz  > 1 KB

# Verify gzip integrity
gunzip -t /tmp/vi-backup-test/vi-postgres-*.sql.gz && echo "OK"
```

---

## restore-postgres.sh

Manual restore — requires operator confirmation. Always stop vi-app before restoring.

```bash
# Stop app to prevent writes during restore
docker compose -f src/docker/docker-compose.prod.yml stop vi-app

# Run restore
BACKUP_FILE=/var/backups/postgres/vi-postgres-20260101-030000.sql.gz \
POSTGRES_PASSWORD=<secret> \
./src/docker/scripts/restore-postgres.sh

# Restart app (Flyway will reconcile schema on startup)
docker compose -f src/docker/docker-compose.prod.yml start vi-app
```

---

## Log monitoring

Backup log file: `/var/log/vi-backup.log`

```bash
# Check last backup result
tail -20 /var/log/vi-backup.log

# Check backup files + sizes
ls -lh /var/backups/postgres/

# Count backups (should not exceed ~14 + today)
ls /var/backups/postgres/vi-postgres-*.sql.gz | wc -l
```

---

## Reference

- ADR-015: `design_&_architecture/decisions/ADR-015-deployment-target-r11.md`
- Runbook: `design_&_architecture/operations/deploy-runbook-r11.md`
- Secrets checklist: `docs/deploy/production-secrets-checklist.md`
- Compose prod: `src/docker/docker-compose.prod.yml`
