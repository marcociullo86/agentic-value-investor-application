---
type: concept
sources: ["raw/06_Documento_Funzionale_WebApp_Value_Investing.md"]
status: draft
created: 2026-05-20
updated: 2026-05-21
tags: [product-spec, architecture, kotlin, spring-boot, spa, postgresql, fmp, caching, rest]
---
# Architettura WebApp Value Investing

> Architettura a tre livelli (SPA frontend, Kotlin/Spring Boot backend, PostgreSQL) con FMP API come data provider esterno e caching 24h per ottimizzare i costi di chiamata.

## Contesto

La specifica funzionale FSD raccomanda un'architettura a tre livelli distinti per garantire scalabilita', manutenibilita' e prestazioni nell'elaborazione dei dati finanziari. Il backend Kotlin agisce da orchestratore tra il frontend e il data provider esterno FMP. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §2. Architettura di Sistema Raccomandata]

## Dettaglio

### Livello 1: Frontend (Client)

- **Tipo:** Single Page Application (SPA) con **React + Next.js** (SSR/SSG dove utile). [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002]
- **Stato L5:** scaffold contract-only in `src/frontend/` (`generate:api`, `typecheck:api`); app Next.js completa prevista da **TSK-030** (P0 kanban).
- **Responsabilita':** Rendering delle dashboard, visualizzazione Traffic Light, grafici storici, checklist qualitativa Moat.

### Livello 2: Backend (Server)

- **Stack:** Kotlin + Spring Framework (Spring Boot, Spring Data). [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §2. Architettura di Sistema Raccomandata]
- **Responsabilita' principali:**
  - Routing delle richieste dal frontend.
  - Caching delle chiamate API FMP (TTL 24h) per ottimizzazione costi.
  - Implementazione del [[value-investing-rule-engine]] (logica di validazione finanziaria).
  - Esposizione di endpoint REST/GraphQL al frontend.
  - Throttling verso FMP (rate limiting) per rispettare i limiti di licenza.
  - Gestione Retry + fallback su cache in caso di fallimento FMP.
- **Tipizzazione:** Data classes Kotlin per mappatura sicura dei JSON FMP; prevenzione Null Pointer Exception su dati contabili mancanti. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §5. Requisiti Non Funzionali]

### Livello 3: Data Provider (External API)

- **Provider:** Financial Modeling Prep (`financialmodelingprep.com`) — vedi [[fmp-api]].
- **Endpoint usati:** Income Statement, Balance Sheet, Cash Flow Statement, Key Metrics (tutti con `limit=10` per storico decennale).
- **Modalita':** REST JSON; il backend agisce da proxy con cache.

### Database (Storage)

- **Tipo:** Relazionale — PostgreSQL via Spring Data JPA.
- **Dati persistiti:** Configurazioni utente, watchlist, cache dei dati di bilancio giornalieri. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §2. Architettura di Sistema Raccomandata]

## Flusso di Interazione

```
[Utente / Browser]
      |
      v
[Frontend SPA]
      |  (REST/GraphQL)
      v
[Backend Kotlin/Spring Boot]
      |-- Cache hit? --> [PostgreSQL]
      |-- Cache miss --> [FMP API]
      |
      v
[Value Investing Rule Engine]
      |
      v
[Risultato strutturato → Frontend]
```

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §3. Flusso dei Dati (Data Flow)]

## Requisiti Non Funzionali Rilevanti

- **Rate Limiting:** throttling backend verso FMP per non superare la quota di licenza (vedi gap aperto `fmp-rate-limiting` in `wiki/gaps.md`).
- **Resilienza:** meccanismi di Retry e fallback su cache in caso di errori 5xx/timeout FMP.
- **Null Safety:** sfruttamento del type system Kotlin per evitare errori su campi contabili opzionali o mancanti.

## Concetti correlati
[[value-investing-rule-engine]]
[[fmp-api]]
[[fmp-financial-statements]]
[[fmp-metrics-ratios]]

## Pagine collegate
[[vi-06-webapp-value-investing-fsd]]
[[webapp-value-investing-spec]]
[[fmp-api-overview]]

## Aggiornamenti (v2026-05-20)

**Gap chiuso:** `vi-webapp-spa-framework-decision` — framework SPA selezionato con ADR formale.

Il framework frontend e' ora definitivo: **React con Next.js in modalita' SPA/SSG**. La decisione e' documentata nel raw `07_Risoluzione_Q002_Q003.md` (Q_002 ADR). [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend]

La sezione "Livello 1: Frontend (Client)" va letta con questa integrazione:
- Stack: **React + Next.js** (SPA/SSG).
- State management: Zustand o Redux Toolkit (da definire con il team).
- Librerie raccomandate: Recharts o Ag-Grid per tabelle dati e grafici decennali.

Storie EP-005 (US-014, US-015, US-016) sono ora sbloccate. [^src: raw/07_Risoluzione_Q002_Q003.md §Risoluzione Q_002: Architectural Decision Record (ADR) - Scelta Framework Frontend]

Vedi [[vi-07-risoluzione-q002-q003]] per i dettagli dell'ADR.

## Aggiornamenti (v2026-05-21)

**Allineamento `master` post Sprint 2 + contract-check (16/38 TSK `done`):**

| Layer | Stato |
|-------|--------|
| Backend | Kotlin 2.2 + Spring Boot 3.5 in `src/backend/` |
| DB | Flyway V001–V007 (users, stocks, cache FMP, rule_engine_result, event log, dcf_overrides) |
| Frontend | Contract types only; **TSK-030** bootstrap Next.js = prossimo P0 |
| CI/Deploy | **TSK-032** Docker/CI full = prossimo P0; `contract-check` già green (TSK-037) |

**Package backend:** `fmp/` (adapter, cache, resilienza), `ruleengine/` (7 `ValuationRule` + calculators Graham/DCF/MoS), `service/`, `api/`, `persistence/`.

**Endpoint REST implementati (allowlist contract):**

- `GET /api/financials/{ticker}` — dataset FMP cache-aside
- `GET /api/analysis/{ticker}` — pipeline completa ([[analysis-api-pipeline]])
- `POST /api/dcf-overrides`, `DELETE /api/dcf-overrides/{ticker}` — override metodo DCF (stub `X-User-Id`)
- `GET /api/openapi.json` — schema springdoc (viewer UI disabilitato in-app)

Header `X-Data-Snapshot-At` / `X-Data-Stale` su financials e analysis. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/FinancialsController.kt] [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/AnalysisController.kt]

**OpenAPI / springdoc:** versione **2.8.16**, dependency `springdoc-openapi-starter-webmvc-api` (no swagger-ui starter), `swagger-ui.enabled: false`. Dettaglio gate: [[openapi-contract-check]], runbook [[runbook-openapi-contract-check]].

**Kanban:** 22 TSK ancora `todo`; track imminente FE bootstrap + CI Docker.

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-002 (Integrazione FMP): US-004 recupero dati, US-005 cache 24h, US-006 resilienza
- EP-005 (Dashboard): US-014 pannello traffic light, US-015 grafici storici, US-016 checklist Moat
- EP-006 (Watchlist): US-017 gestione watchlist personale
