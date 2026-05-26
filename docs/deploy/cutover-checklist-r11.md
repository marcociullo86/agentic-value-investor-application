---
id: cutover-checklist-r11
title: "Cutover Checklist R1.1 — Production Go-Live"
status: draft
created: 2026-05-26
sources:
  - design_&_architecture/decisions/ADR-015-deployment-target-r11.md
  - management/kanban/EP-008-deploy-operativita-produzione/US-028-checklist-cutover-r11/TSK-067.md
  - docs/deploy/postgres-restore-drill-runbook.md
  - src/frontend/e2e/cutover-smoke.spec.ts
tags: [ops, cutover, go-live, r11]
---
# Cutover Checklist R1.1 — Production Go-Live

**Esecutore**: QA + DevOps + PM
**Data target**: 2026-MM-DD
**Window**: 02:00-04:00 UTC (low-traffic, post-batch notturno)

[^src: design_&_architecture/decisions/ADR-015-deployment-target-r11.md §R1.1 Model]
[^src: docs/deploy/postgres-restore-drill-runbook.md]
[^src: src/frontend/e2e/cutover-smoke.spec.ts]

---

## Pre-cutover (T-7 giorni)

- [ ] ADR-015 review: VM sizing 2vCPU/4GiB/40GiB confermato con provider
- [ ] DNS records preparati (A record + CNAME www) — TTL ridotto a 300s
- [ ] TLS certificato Let's Encrypt emesso e installato in `/etc/letsencrypt/live/`
- [ ] `.env.prod` configurato con secrets ROTAZIONE recente (JWT + Anthropic + FMP)
- [ ] Backup script cron testato (esecuzione manuale produce file in `/var/backups/postgres/`)
- [ ] Drill restore documentato in staging (TSK-065 — vedi `docs/deploy/postgres-restore-drill-runbook.md`)
- [ ] EP-007 hardening completato (TSK-050…060 tutti `done`)
- [ ] Playwright cutover-smoke suite verificata in staging (TSK-066 — tutti e 10 scenari PASS)

---

## Cutover (T-0)

- [ ] (00:00 UTC) Annuncio maintenance window su status page / email utenti
- [ ] (00:30 UTC) Snapshot DB pre-cutover:
  ```bash
  POSTGRES_PASSWORD=<prod-password> \
  BACKUP_FILE=/var/backups/postgres/pre-cutover-$(date -u +%Y-%m-%d).sql.gz \
  src/docker/scripts/backup-postgres.sh
  ```
- [ ] (00:45 UTC) Pull immagini Docker latest tagged release
  ```bash
  docker compose -f src/docker/docker-compose.prod.yml pull
  ```
- [ ] (01:00 UTC) `docker-compose -f src/docker/docker-compose.prod.yml down` (servizio offline)
- [ ] (01:05 UTC) Migration check (Flyway):
  ```bash
  # Verificare che V001..V023 siano applied e nessuna migration pending
  docker compose -f src/docker/docker-compose.prod.yml run --rm vi-app \
    java -jar app.jar --spring.flyway.validate-on-migrate=true
  # Oppure via Gradle su macchina locale con URL staging DB:
  # ./gradlew flywayInfo -Pflyway.url=jdbc:postgresql://<staging-db>:5432/value_investing_prod
  ```
- [ ] (01:10 UTC) Avvio stack:
  ```bash
  docker compose -f src/docker/docker-compose.prod.yml --env-file src/docker/.env.prod up -d
  ```
- [ ] (01:15 UTC) Healthchecks:
  - vi-postgres healthy: `docker compose ps vi-postgres` → `(healthy)`
  - vi-app healthy: `docker compose ps vi-app` → `(healthy)`
  - nginx HTTPS accessibile:
    ```bash
    curl -f https://${APP_DOMAIN}/actuator/health
    # Atteso: {"status":"UP"}
    ```
- [ ] (01:20 UTC) Playwright cutover smoke:
  ```bash
  STAGING_URL=https://${APP_DOMAIN} \
  STAGING_USER_EMAIL=qa@example.com \
  STAGING_USER_PASSWORD=*** \
  npx playwright test cutover-smoke
  ```
  Risultato: **PASS** / FAIL — se FAIL → avviare rollback (vedi sezione Rollback)
- [ ] (01:30 UTC) DNS swap (se applicabile): apex domain → nuova VM
- [ ] (01:45 UTC) Verifica utenti finali (browser di test, 3 ticker diversi: AAPL, MSFT, KO)
- [ ] (02:00 UTC) Job notturno TopValuePicks abilitato:
  ```bash
  # Impostare TOP_PICKS_ENABLED=true in .env.prod e riavviare vi-app
  docker compose -f src/docker/docker-compose.prod.yml restart vi-app
  ```

---

## Post-cutover (T+1h..T+24h)

- [ ] (T+1h) Audit log: nessun ERROR in log vi-app dell'ultima ora
  ```bash
  docker compose -f src/docker/docker-compose.prod.yml logs --since=1h vi-app | grep -c ERROR
  # Atteso: 0
  ```
- [ ] (T+1h) Metriche: latency p95 `<3s` su `/api/analysis/{ticker}`, error rate `<0.5%`
- [ ] (T+2h) Annuncio maintenance window closed
- [ ] (T+12h) Verifica job batch:
  ```sql
  SELECT status, tickers_processed, top30_count
  FROM top_picks_run_log
  WHERE run_date = CURRENT_DATE
  ORDER BY started_at DESC LIMIT 1;
  -- Atteso: status = 'COMPLETED'
  ```
- [ ] (T+24h) DNS TTL ripristinato a valore normale (3600s)
- [ ] (T+24h) Backup notturno verificato:
  ```bash
  ls -lth /var/backups/postgres/ | head -3
  # File con timestamp di oggi presente
  ```
- [ ] (T+24h) Drill restore pianificato (prossimo trimestre: Q3 2026)

---

## Rollback procedure (se cutover fallisce)

Eseguire immediatamente se Playwright smoke FAIL o healthcheck non raggiunge UP:

- [ ] `docker compose -f src/docker/docker-compose.prod.yml down`
- [ ] Ripristino DB da snapshot pre-cutover:
  ```bash
  BACKUP_FILE=/var/backups/postgres/pre-cutover-$(date -u +%Y-%m-%d).sql.gz \
  POSTGRES_PASSWORD=<prod-password> \
  src/docker/scripts/restore-postgres.sh
  ```
- [ ] DNS revert al pre-cutover endpoint
- [ ] Comunicazione utenti: rollback completato + ETA next attempt
- [ ] Post-mortem entro 48h con root cause + plan correttivo

---

## Registro PASS/FAIL

Compilare durante l'esecuzione del cutover. Ogni operatore annota il proprio step.

| Step | Atteso | Actual | Status | Operatore | Timestamp UTC |
|---|---|---|---|---|---|
| Snapshot DB pre-cutover | file > 0B, `ls` output | _ | _ | _ | _ |
| Flyway migration check | V001..V023 applied, 0 pending | _ | _ | _ | _ |
| vi-postgres healthcheck | `(healthy)` | _ | _ | _ | _ |
| vi-app healthcheck | `(healthy)` | _ | _ | _ | _ |
| nginx HTTPS `/actuator/health` | `{"status":"UP"}` | _ | _ | _ | _ |
| Playwright cutover smoke | 10/10 PASS | _ | _ | _ | _ |
| DNS swap | apex → nuova VM | _ | _ | _ | _ |
| Verifica 3 ticker browser | AAPL/MSFT/KO: 200 OK | _ | _ | _ | _ |
| TOP_PICKS_ENABLED attivato | restart OK, log no ERROR | _ | _ | _ | _ |
| T+1h audit log | 0 ERROR | _ | _ | _ | _ |
| T+12h job batch | COMPLETED | _ | _ | _ | _ |
| T+24h backup notturno | file con timestamp odierno | _ | _ | _ | _ |

---

## Gate finale

- [ ] **GO-LIVE APPROVAL**: PM + DevOps + QA tutti firmano sotto

  - PM: ________________ Data: __________
  - DevOps: ____________ Data: __________
  - QA: ________________ Data: __________

---

## Riferimenti

- ADR-015: `design_&_architecture/decisions/ADR-015-deployment-target-r11.md`
- Drill restore runbook: `docs/deploy/postgres-restore-drill-runbook.md`
- Playwright smoke spec: `src/frontend/e2e/cutover-smoke.spec.ts`
- Secrets checklist: `docs/deploy/production-secrets-checklist.md`
- Backup script: `src/docker/scripts/backup-postgres.sh`
- Restore script: `src/docker/scripts/restore-postgres.sh`

[^src: management/kanban/EP-008-deploy-operativita-produzione/US-028-checklist-cutover-r11/TSK-067.md]
[^src: management/kanban/EP-008-deploy-operativita-produzione/US-028-checklist-cutover-r11/US-028.md §Acceptance Criteria]
