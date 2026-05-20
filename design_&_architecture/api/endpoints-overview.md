---
id: endpoints-overview
title: API endpoints overview — mappa US -> path
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# API endpoints overview

> Mappa endpoint REST -> user story. Contratto completo + schemas: [openapi.yaml](openapi.yaml). Convenzioni generali (versioning, error format RFC 9457, header `X-Data-Snapshot-At`): vedi [ADR-007](../decisions/ADR-007-api-contract.md).

## Endpoint per epica

### EP-001 — Ricerca e Screening

| Method | Path | US | Auth | Note |
|---|---|---|---|---|
| GET | `/api/search?query={q}` | US-001 | no | Lookup ticker/company; min 1 char, max 6 char per ticker; FMP `/api/v3/search`. |
| GET | `/api/screener` | US-002 | no | Query params: `marketCap` (enum `MICRO|SMALL|MID|LARGE|MEGA`), `sector` (multi GICS), `excludeHardToPredict` (bool). FMP `/api/v3/stock-screener`. |
| GET | `/api/search/{ticker}` | US-001 | no | Validazione esistenza singolo ticker; risponde 404 strutturato se non esiste. |

US-003 (visualizzazione lista risultati) e' una pura UX-concern lato FE che consuma i payload di `/api/search` e `/api/screener`.

### EP-002 — Integrazione FMP Data Provider

| Method | Path | US | Auth | Note |
|---|---|---|---|---|
| GET | `/api/financials/{ticker}` | US-004 | no | Diagnostico: ritorna i 4 set bilancio tipizzati. Espone `dataSnapshotAt` + `isStale`. Usato principalmente come dipendenza interna del Rule Engine. |

US-005 (cache 24h) e US-006 (resilienza) sono **cross-cutting**: nessun endpoint dedicato. Comportamento visibile via:
- header `X-Data-Snapshot-At`, `X-Data-Stale` in tutte le response che dipendono da FMP.
- endpoint `/actuator/health` (FmpHealthIndicator, vedi [ADR-008](../decisions/ADR-008-observability-logging.md)).

### EP-003 + EP-004 — Rule Engine + Valore Intrinseco

| Method | Path | US | Auth | Note |
|---|---|---|---|---|
| GET | `/api/analysis/{ticker}` | US-007..013 + US-014 consumption | no | **Endpoint principale**. Ritorna `RuleEngineResult` aggregato: signals[] + grahamNumber + dcfIntrinsicValue + dcfMethod + mosSignal + currentPriceAtEval + dataSnapshotAt. Internamente: invoca `RuleEngineService.evaluateAll(ticker)`. |
| GET | `/api/analysis/{ticker}/history` | (futuro) | no | Storia evaluate per il titolo (out of MVP, predisposto). |
| POST | `/api/dcf-overrides` | US-012 | si' | Body: `{ticker, forcedMethod: GREENWALD|FCF_FALLBACK}`. Persiste override per utente. |
| DELETE | `/api/dcf-overrides/{ticker}` | US-012 | si' | Rimuove override (torna a default Greenwald). |

### EP-005 — Dashboard, Traffic Light, Moat

| Method | Path | US | Auth | Note |
|---|---|---|---|---|
| GET | `/api/historical/{ticker}` | US-015 | no | Serie temporali (10 anni) di ricavi e utile netto. Output: `[{year, revenue, netIncome, isMissing}]`. |
| GET | `/api/moat-checklist/{ticker}` | US-016 | si' | Ritorna le 4 voci Moat con stato + nota dell'utente; voci mancanti = vuote (lo crea la POST). |
| POST | `/api/moat-checklist/{ticker}` | US-016 | si' | Upsert di una entry: `{moatType, status, note}`. |

US-014 (pannello Traffic Light) consuma `/api/analysis/{ticker}` — non ha endpoint dedicato.

### EP-006 — Watchlist e profilo utente

| Method | Path | US | Auth | Note |
|---|---|---|---|---|
| POST | `/api/auth/register` | - | no | Body: `{email, password, displayName?}`. 201 Created. |
| POST | `/api/auth/login` | - | no | Body: `{email, password}`. Ritorna `{accessToken, refreshToken, expiresInSeconds}`. |
| POST | `/api/auth/refresh` | - | no | Body: `{refreshToken}`. |
| POST | `/api/auth/logout` | - | si' | Revoca il refresh token corrente. |
| GET | `/api/watchlist` | US-017 | si' | Ritorna watchlist default con items[] + ultima evaluate per ognuno. |
| POST | `/api/watchlist/items` | US-017 | si' | Body: `{ticker}`. Idempotente (ritorna 200 se gia' presente). |
| DELETE | `/api/watchlist/items/{ticker}` | US-017 | si' | 204 No Content. |

## Endpoint trasversali

| Method | Path | Scopo |
|---|---|---|
| GET | `/actuator/health` | Liveness + readiness + FmpHealthIndicator |
| GET | `/actuator/prometheus` | Metriche Micrometer (vedi [ADR-008](../decisions/ADR-008-observability-logging.md)) |
| GET | `/api/openapi.json` (springdoc) | Contratto OpenAPI runtime |
| GET | `/swagger-ui` | Swagger UI in profilo `dev` |

## Comportamenti comuni (tutte le response)

- Header `X-Request-Id` echo del client o generato server-side.
- Header `X-Data-Snapshot-At` se la response include dati FMP cached.
- Header `X-Data-Stale: true` se servito fallback su cache scaduta (US-006).
- Body errori: RFC 9457 Problem Details (`application/problem+json`).

## Pagine collegate

- [openapi.yaml](openapi.yaml) — contratto completo
- [ADR-007](../decisions/ADR-007-api-contract.md) — motivazione REST + OpenAPI
- [overview.md](../overview.md)
- [[webapp-architecture-vi]]
