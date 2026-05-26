---
id: postgres-restore-drill-runbook
title: "PostgreSQL Restore Drill Runbook — Staging"
status: active
created: 2026-05-26
sources:
  - design_&_architecture/decisions/ADR-015-deployment-target-r11.md
  - management/kanban/EP-008-deploy-operativita-produzione/US-027-backup-db-retention-log/TSK-065.md
  - src/docker/scripts/restore-postgres.sh
  - src/docker/scripts/backup-postgres.sh
tags: [ops, backup, staging, drill, rpo-rto]
---
# PostgreSQL Restore Drill Runbook — Staging

> Runbook step-by-step per validare il restore PostgreSQL in ambiente staging.
> RPO target: 24h | RTO target: ≤30 min
>
> [^src: design_&_architecture/decisions/ADR-015-deployment-target-r11.md §Backup PostgreSQL]
> [^src: management/kanban/EP-008-deploy-operativita-produzione/US-027-backup-db-retention-log/US-027.md §Acceptance Criteria]

---

## Pre-condizioni

Prima di eseguire il drill verificare che tutte le condizioni seguenti siano soddisfatte:

- [ ] Staging VM operativa con podman + `docker-compose.prod.yml` deployato
- [ ] Backup giornaliero attivo da ≥3 giorni (almeno 3 file `.sql.gz` in `/var/backups/postgres/`)
- [ ] Dataset realistico presente nel DB:
  - `stocks` ≥ 1000 righe
  - `top_value_picks` ≥ 10 run completati (≥10 righe distinte per `run_date`)
- [ ] Finestra di manutenzione comunicata al team (staging — nessun utente attivo previsto)
- [ ] Accesso SSH alla VM staging disponibile per l'esecutore

---

## Procedura drill

### Step 1 — Snapshot pre-drill (baseline)

Eseguire un dump dedicato per avere un riferimento baseline prima del disaster simulato:

```bash
# Produce un dump pre-drill marcato con timestamp
BACKUP_FILE=/var/backups/postgres/vi-postgres-predrill-$(date -u +%Y%m%d-%H%M%S).sql.gz
POSTGRES_PASSWORD=<staging-password> \
docker compose -f src/docker/docker-compose.prod.yml \
  exec -T vi-postgres pg_dump \
    --username=vi_prod_user \
    --dbname=value_investing_prod \
    --format=plain --no-owner --no-privileges \
  | gzip --best > "${BACKUP_FILE}"

echo "Pre-drill dump: ${BACKUP_FILE}"
ls -lh "${BACKUP_FILE}"
```

Raccogliere i conteggi delle tabelle critiche e annotarli nel [Registro drill](#registro-drill):

```sql
-- Eseguire via: docker compose exec vi-postgres psql -U vi_prod_user -d value_investing_prod
SELECT 'stocks'                AS tabella, COUNT(*) AS righe FROM stocks
UNION ALL
SELECT 'fmp_financial_snapshot',           COUNT(*)          FROM fmp_financial_snapshot
UNION ALL
SELECT 'top_value_picks',                  COUNT(*)          FROM top_value_picks
UNION ALL
SELECT 'rule_engine_result',               COUNT(*)          FROM rule_engine_result
UNION ALL
SELECT 'deep_analysis_report',             COUNT(*)          FROM deep_analysis_report;
```

Annotare i valori come `pre_drill_counts` nel registro.

---

### Step 2 — Simulazione disaster

Fermare l'applicazione per prevenire scritture durante il drill:

```bash
docker compose -f src/docker/docker-compose.prod.yml stop vi-app
echo "vi-app stopped at $(date -u +%FT%TZ)"
```

Simulare un disaster controllato (DROP TABLE su tabella non-critica per lo staging):

```bash
docker compose -f src/docker/docker-compose.prod.yml \
  exec vi-postgres psql -U vi_prod_user -d value_investing_prod \
  -c "DROP TABLE top_value_picks CASCADE;"

echo "Disaster simulato: DROP TABLE top_value_picks CASCADE — $(date -u +%FT%TZ)"
```

Verificare che la tabella sia effettivamente assente:

```bash
docker compose -f src/docker/docker-compose.prod.yml \
  exec vi-postgres psql -U vi_prod_user -d value_investing_prod \
  -c "\dt top_value_picks"
# Atteso: "Did not find any relation named 'top_value_picks'"
```

---

### Step 3 — Eseguire restore-postgres.sh

Identificare il backup più recente (quello ordinario, non il pre-drill):

```bash
ls -lt /var/backups/postgres/vi-postgres-[0-9]*.sql.gz | head -5
# Scegliere il backup più recente con timestamp del giorno precedente (overnight cron)
LATEST_BACKUP=$(ls -t /var/backups/postgres/vi-postgres-[0-9]*.sql.gz | head -1)
echo "Backup selezionato: ${LATEST_BACKUP}"
```

Annotare l'orario di inizio restore:

```bash
echo "Restore start: $(date -u +%FT%TZ)"
```

Eseguire il restore:

```bash
BACKUP_FILE="${LATEST_BACKUP}" \
POSTGRES_PASSWORD=<staging-password> \
src/docker/scripts/restore-postgres.sh
# Digitare 'yes-restore' alla richiesta di conferma
```

Annotare l'orario di fine restore:

```bash
echo "Restore end: $(date -u +%FT%TZ)"
```

---

### Step 4 — Verifica ripristino

Raccogliere i conteggi post-restore e confrontare con il pre-drill:

```sql
SELECT 'stocks'                AS tabella, COUNT(*) AS righe FROM stocks
UNION ALL
SELECT 'fmp_financial_snapshot',           COUNT(*)          FROM fmp_financial_snapshot
UNION ALL
SELECT 'top_value_picks',                  COUNT(*)          FROM top_value_picks
UNION ALL
SELECT 'rule_engine_result',               COUNT(*)          FROM rule_engine_result
UNION ALL
SELECT 'deep_analysis_report',             COUNT(*)          FROM deep_analysis_report;
```

I conteggi post-restore devono corrispondere a quelli del backup utilizzato (non necessariamente identici al pre-drill se il backup era di ieri sera, ma la tabella `top_value_picks` deve esistere e avere righe).

Eseguire la query smoke per `top_value_picks`:

```sql
SELECT COUNT(*) AS last_run_picks
FROM top_value_picks
WHERE run_date = (SELECT MAX(run_date) FROM top_value_picks);
-- Atteso: COUNT(*) > 0
```

---

### Step 5 — App smoke test

Riavviare l'applicazione:

```bash
docker compose -f src/docker/docker-compose.prod.yml start vi-app
echo "vi-app started at $(date -u +%FT%TZ)"
```

Attendere il boot completo (Flyway validate + healthcheck):

```bash
# Poll healthcheck ogni 5s, timeout 120s
for i in $(seq 1 24); do
  STATUS=$(curl -sf http://localhost:8080/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null || echo "DOWN")
  echo "$(date -u +%FT%TZ) — vi-app health: ${STATUS}"
  [[ "${STATUS}" == "UP" ]] && break
  sleep 5
done
```

Verificare l'endpoint di analisi:

```bash
# Richiede token JWT valido per staging; usare credenziali test
TOKEN=$(curl -sf -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"qa@staging.example.com","password":"<staging-qa-password>"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -sf -H "Authorization: Bearer ${TOKEN}" \
  "http://localhost:8080/api/analysis/AAPL" | python3 -m json.tool | head -20
# Atteso: HTTP 200 + JSON con signals array length=13
```

---

### Step 6 — Documentare durata e dimensioni

```bash
# Dimensione del backup utilizzato
du -h "${LATEST_BACKUP}"

# RTO actual = (Restore end) - (Restore start) in minuti
# Calcolare manualmente dai timestamp annotati negli step 3
```

Compilare il template [Registro drill](#registro-drill) con tutti i dati raccolti e salvarlo come `drill-report-YYYY-MM-DD.md` nella stessa directory di questo runbook.

---

## Acceptance criteria drill

- [ ] Restore completato senza errori SQL (exit code 0 da `restore-postgres.sh`)
- [ ] Count tabelle critiche post-restore == conteggi del backup utilizzato (zero data loss dal backup)
- [ ] App boot OK post-restore (Flyway validate green, nessun `FlywayException` nei log)
- [ ] Smoke endpoint `GET /api/analysis/AAPL` → HTTP 200
- [ ] RTO actual ≤ 30 minuti (da inizio `restore-postgres.sh` a vi-app UP)
- [ ] Esito documentato in `drill-report-YYYY-MM-DD.md` con timing per step

---

## Registro drill

Template da copiare e compilare nel file `drill-report-YYYY-MM-DD.md`:

```markdown
## Esecuzione drill {DATA} (esito atteso post-staging-deploy)

- Backup utilizzato: vi-postgres-YYYYMMDD-HHMMSS.sql.gz ({size})
- Pre-drill counts: stocks={N}, top_value_picks={N}, deep_analysis_report={N}
- Disaster simulato: DROP TABLE top_value_picks CASCADE
- Restore start: {timestamp UTC}
- Restore end: {timestamp UTC}
- RTO actual: {duration in minuti}
- Post-restore counts: stocks={N}, top_value_picks={N}, deep_analysis_report={N}
- Flyway validate: PASS / FAIL
- Smoke test GET /api/analysis/AAPL: PASS (HTTP 200) / FAIL
- Status: GO / NO-GO
- Esecutore: {nome + ruolo}
- Note: {eventuali anomalie o deviazioni dalla procedura}
```

---

## Sezione "Risultato attuale"

> Placeholder — da compilare quando staging operativo con backup ≥3 giorni attivi.

```markdown
## Esecuzione drill 2026-MM-DD (esito atteso post-staging-deploy)

- Backup utilizzato: vi-postgres-YYYYMMDD-HHMMSS.sql.gz ({size})
- Pre-drill counts: stocks={N}, top_value_picks={N}, deep_analysis_report={N}
- Disaster simulato: DROP TABLE top_value_picks CASCADE
- Restore start: {timestamp}
- Restore end: {timestamp}
- RTO actual: {duration}
- Post-restore counts: stocks={N}, top_value_picks={N}, deep_analysis_report={N}
- Smoke test: PASS/FAIL
- Status: DOCUMENTATO / GO / NO-GO
```

---

## Frequenza drill

| Cadenza | Trigger |
|---|---|
| Trimestrale (Q1/Q2/Q3/Q4) | Calendario fisso — entro il 15 del primo mese del trimestre |
| Ad-hoc | Post-cambio schema major (migration V0XX con ALTER TABLE o DROP COLUMN) |
| Ad-hoc | Post-aggiornamento PostgreSQL major version |
| Ad-hoc | Post-migrazione VM / cambio storage backend |

---

## Dipendenze script

- `src/docker/scripts/backup-postgres.sh` — script backup giornaliero (TSK-063)
- `src/docker/scripts/restore-postgres.sh` — script restore point-in-time (TSK-063)
- `src/docker/docker-compose.prod.yml` — stack compose produzione (TSK-061)

[^src: management/kanban/EP-008-deploy-operativita-produzione/US-027-backup-db-retention-log/TSK-065.md]
[^src: management/kanban/EP-008-deploy-operativita-produzione/US-027-backup-db-retention-log/US-027.md §Acceptance Criteria]
[^src: design_&_architecture/decisions/ADR-015-deployment-target-r11.md §Backup PostgreSQL]
