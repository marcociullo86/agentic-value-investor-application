---
id: ADR-003
title: Database — PostgreSQL 16 + JPA + Flyway
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-003 — Database: PostgreSQL 16 + Spring Data JPA + Flyway

## Contesto

La FSD prescrive un database relazionale PostgreSQL accessibile via Spring Data JPA, con dati persistenti: configurazioni utente, watchlist e cache giornaliera dei dati di bilancio [^src: wiki/concepts/webapp-architecture-vi.md §Database (Storage)].

## Decisione

- **DBMS**: **PostgreSQL 16** (LTS, JSONB nativo per payload FMP cache, FTS opzionale).
- **ORM**: **Spring Data JPA** + Hibernate 6.
- **Schema migrations**: **Flyway** (versioned `V<NNN>__<descrizione>.sql` in `src/main/resources/db/migration`).
- **Connection pool**: HikariCP (default Spring Boot).
- **Test isolation**: **Testcontainers** PostgreSQL per integration test (no H2 — divergenza di dialect dannosa per JSONB).

## Schema canonico

Dettaglio entita' + DDL: vedi [data/er-diagram.md](../data/er-diagram.md) e [data/schema.sql](../data/schema.sql).

Entita' principali:

| Tabella | Scopo | US |
|---|---|---|
| `users` | Profilo utente | US-017 (auth EP-006) |
| `watchlists` | Watchlist personale (1 default per utente, espandibile) | US-017 |
| `watchlist_items` | Ticker in watchlist | US-017 |
| `fmp_financial_snapshot` | Cache JSONB per ticker + endpoint + data | US-004, US-005, US-006 |
| `fmp_profile_snapshot` | Cache profilo (prezzo, market cap, settore) | US-001, US-013 |
| `rule_engine_result` | Verdetto Rule Engine per ticker + data analisi | US-007..013, US-014 |
| `moat_checklist_entry` | Annotazioni qualitative Moat per (user, ticker) | US-016 |
| `dcf_method_override` | Flag per forzare metodo DCF alternativo (Greenwald vs FCF) | US-012 |
| `fmp_api_event_log` | Log eventi 429 / errori FMP (osservabilita') | US-006 |

## Motivazioni

1. **Vincolo verbatim FSD**: PostgreSQL prescritto esplicitamente.
2. **JSONB per cache FMP**: il payload FMP e' un JSON denso (decine di campi per record, 10 record per endpoint); persistere come JSONB nativo conserva tutti i campi senza modellare colonne per ognuno, ed espone query via path (`->>`) per estrazioni mirate. Riduce churn schema su evoluzioni FMP.
3. **Cache 24h gestita applicativamente**: TTL non delegato al DB (no `pg_cron`) ma gestito dal `FmpCacheService` con campo `fetched_at TIMESTAMPTZ` su `fmp_financial_snapshot` (US-005, AC: "una seconda analisi entro 24h non genera chiamata").
4. **Multi-utente persistente**: watchlist e checklist Moat richiedono persistenza relazionale stabile (FK + integrita' referenziale).

## Alternative considerate

- **MongoDB**: non prescritto dalla FSD; perdiamo integrita' referenziale per watchlist/utenti.
- **Redis come cache + PostgreSQL transazionale**: doppia source-of-truth; complica deploy R1.0. Spostabile a R1.1+ se serve TTL nativo o latenze sub-ms (oggi non richiesto: TTL 24h e' compatibile con query PostgreSQL indicizzate).
- **H2 in-memory**: ammissibile solo per unit test isolati, mai per integration test.

## Conseguenze

- Tutte le US persistenti (US-005, US-016, US-017) hanno schema definitivo (vedi [data/](../data/)).
- Flyway baseline al primo deploy; ogni migration tracciata.
- Backup: out of scope per il MVP; documentato in `gaps.md` come gap operativo (vedi `arch-deployment-target`).

## Appendice — Allineamento stack v2026 (US-025, 2026-05-22)

| Componente | Versione documentata R1.0 | Stack canonico 2026 |
|---|---|---|
| PostgreSQL | 16 | **17.x** |
| Flyway | (generico) | **10.x** + `flyway-database-postgresql` |
| Dev/test | Testcontainers | **postgres:17** image |

`docker-compose` dev e prod target: immagine **`postgres:17`** ([ADR-015](ADR-015-deployment-target-r11.md)). Backup policy: [operations/deploy-runbook-r11.md](../operations/deploy-runbook-r11.md).

## Pagine collegate

- [[webapp-architecture-vi]]
- [overview.md](../overview.md)
- [data/er-diagram.md](../data/er-diagram.md)
- [ADR-014](ADR-014-fmp-profile-snapshot-ttl.md)
- [ADR-015](ADR-015-deployment-target-r11.md)
