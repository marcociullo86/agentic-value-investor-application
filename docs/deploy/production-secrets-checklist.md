---
id: production-secrets-checklist
title: "Production Secrets Checklist — R1.1 Cutover"
status: draft
created: 2026-05-26
sources:
  - design_&_architecture/decisions/ADR-015-deployment-target-r11.md
  - management/kanban/EP-008-deploy-operativita-produzione/US-026-baseline-target-deploy/TSK-062.md
tags: [ops, security, deploy]
---
# Production Secrets Checklist — R1.1 Cutover

Reference: [ADR-015](../../design_%26_architecture/decisions/ADR-015-deployment-target-r11.md)

## Pre-flight: variables to set before `docker compose up`

All variables are in `src/docker/.env.prod` (copy from `.env.prod.example`, `chmod 600`).

| Variable | Required | Notes |
|---|---|---|
| `POSTGRES_PASSWORD` | YES | Strong random password; never reuse dev default |
| `POSTGRES_USER` | YES | `vi_prod_user` (non-root DB user) |
| `POSTGRES_DB` | YES | `value_investing_prod` |
| `JWT_SIGNING_SECRET` | YES | >= 256-bit; see generation command below |
| `FMP_API_KEY` | YES | From FMP dashboard — keep out of VCS |
| `FMP_BASE_URL` | YES | `https://financialmodelingprep.com/stable` |
| `SEC_EDGAR_USER_AGENT_EMAIL` | YES | Valid reachable email (SEC fair-access policy) |
| `ANTHROPIC_API_KEY` | YES | From Anthropic console; covers Munger + News Scout |
| `CORS_ALLOWED_ORIGINS` | YES | Comma-separated HTTPS origins only in prod |
| `APP_DOMAIN` | YES | FQDN matching TLS certificate CN |
| `BACKUP_DIR` | YES | Host path; ensure directory exists and is owned by the postgres uid |
| `LLM_BUDGET_MONTHLY_USD` | YES | Runtime guard — adjust to plan limits |
| `TOP_PICKS_ENABLED` | YES | `true` to activate batch job |

---

## Generating secrets

### JWT_SIGNING_SECRET (>= 256 bits)

```bash
openssl rand -base64 32
```

Produces a 44-character base64 string (~264 bits). Paste verbatim into `.env.prod`.

### POSTGRES_PASSWORD

```bash
openssl rand -base64 24
```

### TLS certificate (Let's Encrypt)

```bash
# Stop nginx if running on port 80, or use --webroot if nginx already serves /.well-known
certbot certonly --standalone -d app.example.com
# Certificates written to: /etc/letsencrypt/live/app.example.com/
```

After renewal (auto-renewal via certbot timer), reload nginx:

```bash
docker compose -f src/docker/docker-compose.prod.yml exec nginx nginx -s reload
```

---

## Key rotation procedures

### Anthropic API key

1. Generate new key in Anthropic console.
2. Update `ANTHROPIC_API_KEY` in `.env.prod` on the VM.
3. Restart vi-app:
   ```bash
   docker compose -f src/docker/docker-compose.prod.yml restart vi-app
   ```
4. Revoke old key in console only after confirming new key is active (check logs).

### FMP API key

1. Generate new key in FMP dashboard.
2. Update `FMP_API_KEY` in `.env.prod`.
3. Restart vi-app (same as above).
4. Revoke old key in FMP dashboard.

### JWT_SIGNING_SECRET

WARNING: rotating JWT_SIGNING_SECRET invalidates all active user sessions.
1. Schedule a maintenance window.
2. Generate new secret: `openssl rand -base64 32`
3. Update `.env.prod`.
4. Restart vi-app — all tokens issued with old secret become invalid; users must re-login.

---

## File permissions

```bash
chmod 600 src/docker/.env.prod
chown root:root src/docker/.env.prod   # or the deploy user owning the process
```

The `.env.prod` file must never be world-readable. Verify:

```bash
ls -la src/docker/.env.prod
# Expected: -rw------- 1 root root ...
```

---

## Backup directory setup

```bash
mkdir -p /var/backups/postgres
chmod 750 /var/backups/postgres
# The postgres container runs as uid 999 (postgres default); adjust if needed:
# chown 999:999 /var/backups/postgres
```

---

## Smoke test after cutover

```bash
# HTTPS health check
curl -f https://app.example.com/actuator/health

# HTTP → HTTPS redirect
curl -v http://app.example.com/  # expect HTTP 301

# API endpoint
curl -f https://app.example.com/api/v1/search?q=AAPL
```

---

## Reference

- ADR-015: `design_&_architecture/decisions/ADR-015-deployment-target-r11.md`
- Runbook: `design_&_architecture/operations/deploy-runbook-r11.md`
- Backup script: `src/docker/scripts/backup-postgres.sh`
- Scripts README: `src/docker/scripts/README.md`
