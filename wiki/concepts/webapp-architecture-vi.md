---
type: concept
sources: ["raw/06_Documento_Funzionale_WebApp_Value_Investing.md"]
status: draft
created: 2026-05-20
updated: 2026-05-30
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
[[fmp-financial-statements-stable]]
[[fmp-key-metrics-ratios]]

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

## Aggiornamenti (v2026-05-27)

**EP-017 Protezione Rotte e Sessione completata (Sprint 14, 14/14 TSK done).**

Il layer di autenticazione e protezione rotte e ora implementato end-to-end:

- **Frontend:** AuthGuard middleware Next.js, route map dichiarativa (11 rotte), rehydration F5, idle/absolute timeout con prompt accessibile, logout completo con back-button blocking.
- **Backend:** migrazione refresh token a cookie `httpOnly Secure SameSite=Strict` (ADR-024), `RefreshTokenCookieHelper.kt`, token rotation, OpenAPI aggiornata.
- **Token refresh:** pre-expiry 60s con mutex singleton, 401 interceptor.

Dettaglio completo: [[auth-guard-frontend]] §Aggiornamenti (v2026-05-27).

## Aggiornamenti (v2026-05-28)

**Fix runtime statico — SpaRoutingConfig + same-origin API base URL + tooling locale.**

### SpaRoutingConfig — route forward estese

`SpaRoutingConfig.kt` esteso con 5 nuove route forward (fix `NoResourceFoundException` su runtime statico single-container):

| Route aggiunta | Forward target |
|---|---|
| `/analysis/deep`, `/analysis/deep/` | `/analysis/deep/index.html` |
| `/top-picks`, `/top-picks/` | `/top-picks/index.html` |
| `/profile/mfa`, `/profile/mfa/` | `/profile/mfa/index.html` |
| `/admin`, `/admin/` | `/admin/index.html` |
| `/403`, `/403/` | `/403/index.html` |

Queste route erano raggiungibili in `next dev` (webpack dev server gestisce il fallback) ma causavano `NoResourceFoundException` nel deploy statico Spring Boot R1.1 quando il browser navigava direttamente all'URL (hard navigation / refresh F5). [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/config/SpaRoutingConfig.kt]

### NEXT_PUBLIC_API_BASE_URL — default same-origin

`next.config.js` e `src/frontend/lib/api/client.ts` ora usano `NEXT_PUBLIC_API_BASE_URL || ''` (stringa vuota = same-origin) come default, evitando bundle hardcoded su `localhost` nel static export. Override opzionale via env var a build time per ambienti staging/produzione con domini separati. [^src: src/frontend/next.config.js] [^src: src/frontend/lib/api/client.ts]

### E2E smoke e top-picks — allineamento selector/route

- `cutover-smoke.spec.ts`: route analysis aggiornata a `/analysis?ticker=AAPL` e deep a `/analysis/deep?ticker=AAPL` (query-param ADR-013).
- `top-picks.spec.ts`: selector aggiornati a `top-pick-row-{ticker}` e link di navigazione a `/analysis/deep?ticker=`.
- `accessibility-keyboard.spec.ts`: allineato (fix TSK-239 già documentato).

### Tooling locale Podman

| File | Scopo |
|---|---|
| `src/docker/start-agentic-value-investor.sh` | Bootstrap helper (build + compose up + Portainer) per macOS/Linux Podman |
| `src/docker/docker-compose.local-no-embeddings.yml` | Override compose locale: stub embeddings-sidecar (`python:3.12-alpine http.server 8001`) per tour app senza build sidecar pesante; compose canonico invariato |

Avvio rapido senza embeddings:

```bash
podman compose -f src/docker/docker-compose.yml \
               -f src/docker/docker-compose.local-no-embeddings.yml \
               up -d postgres adminer app
```

**Gap `fe-middleware-static-export-conflict` chiuso** con ADR-026 + US-087 (2026-05-28): `ClientAuthGuard` client-side in produzione, `output: 'export'` invariato, middleware dev-only con warning esplicito, E2E suite 11 test. [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md]

## Aggiornamenti (v2026-05-28) — US-087 ClientAuthGuard static export

**Gap `fe-middleware-static-export-conflict` chiuso. ADR-026 accepted. US-087 done (4/4 TSK).**

ADR-026 formalizza la soluzione al conflitto tra `output: 'export'` e Next.js middleware: il layer di protezione rotte in produzione è `ClientAuthGuard` (Opzione B), mantenendo invariato il deployment model ADR-009 (static SPA servita dal backend Spring Boot).

### Modello AuthGuard in produzione (post US-087)

| Ambiente | Meccanismo attivo | Note |
|---|---|---|
| `next dev` | `middleware.ts` + `ClientAuthGuard` | Doppia protezione; middleware Edge runtime disponibile |
| `next build` (static export) | `ClientAuthGuard` only | Middleware ignorato da Next.js con `output: 'export'`; warning esplicito a build time (TSK-268) |
| Backend sempre | Spring Security + JWT filter | Trust boundary effettivo; indipendente dal client |

Il `ClientAuthGuard.tsx` (TSK-266) replica tutti e 4 i comportamenti del middleware: redirect a `/login?returnUrl=` (non autenticato), `/403` (ruolo insufficiente), `/login?expired=true` (sessione scaduta), `/` (autenticato su `/login`). È rehydration-aware: non valuta auth fino al completamento del bootstrap `AuthProvider`. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-087-authguard-client-side-static-export/US-087.md]

**E2E coverage:** `playwright.config.static.ts` (TSK-269) — 11 test che verificano i redirect client-side su server statico, non solo in `next dev`. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-087-authguard-client-side-static-export/TSK-269.md]

Dettaglio completo: [[auth-guard-frontend]] §Aggiornamenti (v2026-05-28).

## Aggiornamenti (v2026-05-30) — Deep analysis async + sidecar embeddings GPU

**Deep analysis asincrona e split INGEST/ANALYSIS.** Gli endpoint `/api/analysis/{ticker}/deep/*` sono ora asincroni (POST → 202 + polling su `latest`) con execution persistita nella tabella `deep_analysis_run` (migration V027). La pipeline è separata in due operazioni (colonna `kind`, V028): **INGEST** (download filing + embedding, idempotente) e **ANALYSIS** (verdetto deterministico, opzionalmente +LLM Munger che riusa gli embedding). La UI espone 3 azioni: Indicizza filing / Analizza / Analizza + LLM. Dettaglio: [[analysis-api-pipeline]] §Aggiornamenti (v2026-05-30). [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/DeepAnalysisController.kt]

**Sidecar embeddings — trasporto + GPU.** Il client `EmbeddingService` usa `SimpleClientHttpRequestFactory` (fix del 422 da body vuoto con `JdkClientHttpRequestFactory`). Il sidecar supporta GPU NVIDIA via Podman + CDI (`docker-compose.gpu.yml`, override sopra al compose base che resta CPU-safe); su VRAM piccola (es. T600 4GB) si usa fp16 + batch-size basso + `expandable_segments`. `torch` è pinnato al canale CUDA cu126. Gli script di avvio (`start-agentic-value-investor.{ps1,sh}`) chiedono prima se ribuildare il sidecar e poi CPU/GPU, selezionando il compose adeguato. Dettaglio: [[arctic-embed-l-v2]] §Aggiornamenti (v2026-05-30). [^src: src/docker/docker-compose.gpu.yml]

**`.env` come unico entry point di configurazione.** Il servizio `app` in `docker-compose.yml` carica `src/docker/.env` via `env_file`: ogni variabile definita lì raggiunge Spring Boot, eliminando il rischio (pre-2026-05-30) che var presenti in `.env` ma non elencate in `environment:` venissero silenziosamente ignorate (es. `ANTHROPIC_API_KEY` → fallback `AnthropicClientStub`; `FMP_RATE_LIMIT_PER_MINUTE`, `EMBEDDINGS_MODEL_NAME`, `SPRING_TASK_SCHEDULING_ENABLED`). In `environment:` restano solo gli override legati alla topologia compose (`DB_URL`, `EMBEDDINGS_SIDECAR_URL`, pinati sugli hostname dei service). `src/docker/.env.example` è il template canonico completo e documentato di tutte le var configurabili (FMP, Anthropic, LLM budget, SEC EDGAR, MFA, Turnstile, HIBP, brute-force, retention log). [^src: src/docker/docker-compose.yml] [^src: src/docker/.env.example]

**News deep analysis — feed FMP.** `NewsSentimentService` consuma `/stable/news/stock` (l'endpoint `/news/press-releases` non è nel piano Starter FMP → non integrato). Fix: parametro `symbols` (non `tickers`), `@JsonFormat` su `publishedDate` (`"yyyy-MM-dd HH:mm:ss"`), e degrado a lista vuota su errori di decode/trasporto (le news non sono segnale critico). Persistenza `deep_analysis_report.report_json` come `jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`. Dettaglio: [[fmp-news-media]] §Uso nel progetto. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/service/NewsSentimentService.kt]

**News classification — persistenza robusta.** FMP `/news/stock` non ha un `newsId` stabile: `NewsSentimentService` usa l'URL come chiave di dedup/cache, ma gli URL sforavano `news_classification.news_id VARCHAR(200)` → migration **V029** allarga a `VARCHAR(512)` (con troncamento difensivo alla stessa chiave usata sia in lookup sia in insert). La chiamata LLM di classificazione usa `maxTokens=512` (a 200 il JSON veniva troncato) + estrazione tollerante dell'oggetto JSON dalla risposta. [^src: src/backend/src/main/resources/db/migration/V029__news_classification_widen_news_id.sql]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- **R1.0 done:** EP-002 (US-004…006), EP-004 (US-020), EP-005 (US-014…016), EP-006 (US-017…019)
- **R1.1:** EP-007 (US-021 errori RFC 9457, US-022 dipendenze UI, US-023 ticker deploy statico, US-024 TTL profilo, US-025 ADR stack), EP-008 (US-026…028 deploy/ops), EP-009 (US-029…030 throttling FMP)
