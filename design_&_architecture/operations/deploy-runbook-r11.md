---
id: deploy-runbook-r11
title: Runbook deploy e cutover R1.1
status: accepted
created: 2026-05-22
deciders: [lead-architect, marco.ciullo]
---
# Runbook deploy e cutover R1.1

> Operazioni per US-026, US-027, US-028. Architettura: [ADR-015](../decisions/ADR-015-deployment-target-r11.md), baseline [ADR-009](../decisions/ADR-009-deployment-target.md).

## Prerequisiti

- Host VM ≥ 2 vCPU, 4 GiB RAM, 40 GiB disco ([ADR-015](../decisions/ADR-015-deployment-target-r11.md)).
- Docker Engine 24+ e Docker Compose v2.
- DNS `A`/`AAAA` verso host; porte 80/443 aperte.
- Segreti preparati (mai in repository):

| Variabile | Obbligatoria | Note |
|---|---|---|
| `FMP_API_KEY` | sì | Provider dati |
| `JWT_SIGNING_SECRET` | sì prod | ≥ 256 bit |
| `DB_PASSWORD` | sì | PostgreSQL |
| `SPRING_PROFILES_ACTIVE` | sì | `prod` |
| `CORS_ALLOWED_ORIGINS` | sì prod | Origine HTTPS UI |
| `FMP_RATE_LIMIT_PER_MINUTE` | no | Default 30 ([ADR-016](../decisions/ADR-016-fmp-operations-throttling.md)) |
| `FMP_CACHE_PROFILE_TTL_HOURS` | no | Default 1 ([ADR-014](../decisions/ADR-014-fmp-profile-snapshot-ttl.md)) |

## Deploy staging (US-026 AC)

1. **Build CI:** `gradle bootJar` + `npm ci && npm run build` (senza `--legacy-peer-deps` post US-022) + `docker build -t valueinvesting:staging`.
2. **Push** immagine su registry accessibile dall'host staging.
3. **Sul host:** copiare `docker-compose.prod.yml`, `nginx.conf`, `.env` (permessi `600`).
4. **Avvio:** `docker compose -f docker-compose.prod.yml up -d`.
5. **Migrazioni:** Flyway esegue al boot `app` (`spring.flyway.enabled=true`, profilo `prod`).
6. **Smoke:** `curl -fsS https://<staging>/actuator/health` → `{"status":"UP"}`.
7. **FE:** verificare asset statici (`https://<staging>/`) e API (`/api/search?query=AAPL`).

## Backup PostgreSQL (US-027)

### Policy

| Parametro | Target R1.1 |
|---|---|
| Frequenza | Giornaliero 02:00 UTC |
| Retention backup | 14 giorni |
| Metodo | `pg_dump -Fc` da container `postgres` |
| Destinazione | Volume host `/var/backups/valueinvesting/` (fuori container DB) |
| RPO | 24 h |
| RTO | 4 h (ripristino manuale documentato) |

### Procedura backup (cron host)

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U "$DB_USERNAME" -Fc valueinvesting > \
  "/var/backups/valueinvesting/pg_$(date +%Y%m%d_%H%M).dump"
```

Purge backup più vecchi di 14 giorni (`find … -mtime +14 -delete`).

### Drill restore (staging, obbligatorio pre-cutover)

1. Fermare `app` (`docker compose stop app`).
2. Creare DB vuoto `valueinvesting_restore_test`.
3. `pg_restore -d valueinvesting_restore_test < ultimo.dump`.
4. Verificare conteggio tabelle (`users`, `watchlists`, `fmp_financial_snapshot`).
5. Documentare esito (data, esecutore, PASS/FAIL) in tabella §Registro cutover.

## Retention `fmp_api_event_log` (US-027)

| Parametro | Valore |
|---|---|
| Retention | **90 giorni** |
| Purge | Job giornaliero SQL: `DELETE FROM fmp_api_event_log WHERE created_at < now() - interval '90 days'` |
| Implementazione | Spring `@Scheduled` o cron host via `psql` |

Allineato a [ADR-008](../decisions/ADR-008-observability-logging.md) (log line rotano; tabella conserva finestra audit breve).

## Checklist cutover R1.1 (US-028)

Eseguire in **staging** prima del go-live; ogni voce: owner, PASS/FAIL.

### Gate epiche

| # | Voce | Owner | Criterio PASS |
|---|---|---|---|
| G1 | EP-007 hardening completato | release | US-021..025 merged, test verdi |
| G2 | US-026 deploy baseline | ops | Staging raggiungibile HTTPS |
| G3 | US-027 backup drill | ops | Restore test PASS documentato |

### Infra e sicurezza

| # | Voce | Owner | Criterio PASS |
|---|---|---|---|
| I1 | TLS attivo (nginx) | ops | `curl -vI https://host` certificato valido |
| I2 | Segreti non in git | ops | `.env` solo su host |
| I3 | Actuator non esposto pubblicamente | ops | `/actuator/prometheus` non raggiungibile da Internet |
| I4 | CORS prod ristretto | ops | Solo origine UI prod |
| I5 | `spring.jpa.hibernate.ddl-auto=validate` | be-dev | Profilo `prod` verificato |
| I6 | Flyway migrazioni applicate | be-dev | Log boot senza errori Flyway |

### Applicazione e dati

| # | Voce | Owner | Criterio PASS |
|---|---|---|---|
| A1 | Health readiness | ops | `/actuator/health` readiness UP + DB |
| A2 | RFC 9457 flatten | be-dev | 404 ticker: `$.ticker` top-level ([ADR-012](../decisions/ADR-012-problemdetail-rfc9457-flatten.md)) |
| A3 | Analisi ticker arbitrario | fe-dev | `/analysis?ticker=NON_DEMO` carica dati ([ADR-013](../decisions/ADR-013-fe-analysis-routing-static-export.md)) |
| A4 | npm install senza legacy-peer-deps | fe-dev | CI verde ([ADR-001](../decisions/ADR-001-frontend-stack.md) appendice) |
| A5 | TTL profilo 1h | be-dev | Test cache profilo PASS ([ADR-014](../decisions/ADR-014-fmp-profile-snapshot-ttl.md)) |
| A6 | Throttling FMP configurato | be-dev | Env `FMP_RATE_LIMIT_PER_MINUTE` documentato ([ADR-016](../decisions/ADR-016-fmp-operations-throttling.md)) |
| A7 | Purge `fmp_api_event_log` | be-dev | Job schedulato attivo |

### Smoke funzionali E2E

| # | Voce | Owner | Criterio PASS |
|---|---|---|---|
| F1 | Ricerca → analisi | qa | Playwright/curl: ticker fuori ex-whitelist → 200 UI |
| F2 | Traffic light | qa | `/api/analysis/{ticker}` signals presenti |
| F3 | Watchlist autenticata | qa | login → add ticker → lista |
| F4 | Override DCF | qa | POST override → GET analysis riflette metodo |
| F5 | Moat checklist | qa | `/moat?ticker=AAPL` salva nota |

### Post go-live

| # | Voce | Owner | Criterio PASS |
|---|---|---|---|
| P1 | Monitoraggio log JSON | ops | Errori 5xx alertabili |
| P2 | Primo backup prod | ops | File dump presente < 24h |
| P3 | Registro cutover compilato | release | Tutti critici PASS |

## Registro cutover (template)

| Data | Ambiente | Esecutore | Esito globale | Note |
|---|---|---|---|---|
| YYYY-MM-DD | staging | | PASS / FAIL | |

## Riferimenti

- [ADR-015](../decisions/ADR-015-deployment-target-r11.md)
- [overview.md](../overview.md)
- [^src: management/kanban/EP-008-deploy-operativita-produzione/US-028-checklist-cutover-r11/US-028.md §Acceptance Criteria]
