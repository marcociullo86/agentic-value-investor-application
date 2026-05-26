<!-- generated, do not edit — rigenerato da tpm ad ogni run -->
---
id: sprint
title: Sprint Plan — R1.0 MVP + R1.1 + R1.1.x + R2.0 + R2.1 + R3.0 Fintech Hardening
generated: 2026-05-26
tpm: tpm
release: R3.0 (EP-014..018 — fintech hardening, logging, notifications, design tokens, auth, security)
r10_closed: 2026-05-22
r11_closed: 2026-05-23
r11x_closed: 2026-05-26
r20_closed: 2026-05-26
r21_closed: 2026-05-26
r30_target: TBD
---
# Sprint Plan

> **R1.0 MVP chiuso:** Sprint 1–4 completati (49/49 TSK `done`, 20/20 US, 6/6 EP).
> **R1.1 chiuso:** Sprint 5 — 22/23 TSK `done`; unico residuo TSK-071 `todo` (blocked su gap `fmp-stable-rate-limiting`).
> **R1.1.x hotfix chiuso:** Sprint 5.5 — 12/12 TSK `done`. US-052, US-053, US-054 completate.
> **R2.0 chiuso:** Sprint 6–9 completati — 79/79 TSK `done`, 21/21 US, 3/3 EP (EP-010, EP-011, EP-012).
> **R2.1 chiuso:** Sprint 10 — EP-013 Mr. Market Context Flags — 6/6 TSK `done`, 2/2 US (US-056, US-057).
> **R3.0 pianificato:** Sprint 11–15 — 47 TSK `todo`, 21 `done`, 25 US, 5 EP (EP-014..018). ADR-021 e ADR-023 `accepted`; ADR-022, ADR-024, ADR-025 status: `proposed`.

---

## Sprint 1 — Fondamenta (infra, DB schema completo, FMP adapter + cache)

**Obiettivo:** Avere un backend avviabile con DB migrato, FMP adapter funzionante con
cache 24h e resilienza, più il bootstrap frontend.

**Stato:** COMPLETATO.

| TSK | Titolo | Layer | Consumer | Estimate | Status |
|-----|--------|-------|----------|----------|--------|
| TSK-031 | Setup Spring Boot + Gradle + Docker Compose | infra | agent | S | done |
| TSK-030 | Setup Next.js + Tailwind + Zustand + API client | fe | agent | S | done |
| TSK-001 | DB V001 (users/auth) + V002 (stocks) | db | agent | S | done |
| TSK-008 | DB V003 (fmp_cache) + V004 (rule_engine_result) | db | agent | S | done |
| TSK-009 | FmpAdapter: income/balance/cashflow/key-metrics | be | agent | M | done |
| TSK-010 | FmpCacheService: cache-aside TTL 24h | be | agent | S | done |
| TSK-011 | FmpResilienceConfig + FmpEventLogger | be | agent | S | done |
| TSK-032 | Dockerfile multi-stage + CI pipeline | infra | agent | S | done |

---

## Sprint 2 — Rule Engine + endpoint analisi (EP-003 + EP-004)

**Obiettivo:** Pipeline analisi completa (`GET /api/analysis/{ticker}`) con tutte e 7 le
regole, Graham Number, DCF Owner Earnings, Margin of Safety.

**Stato:** COMPLETATO.

| TSK | Titolo | Layer | Consumer | Estimate | Status |
|-----|--------|-------|----------|----------|--------|
| TSK-005 | ScreenerController GET /api/screener | be | agent | S | done |
| TSK-012 | RuleEngineService scaffold + RoeRule + RoicRule | be | agent | S | done |
| TSK-013 | GrossMarginRule + NetMarginRule | be | agent | S | done |
| TSK-014 | CurrentRatioRule + DebtToIncomeRule | be | agent | S | done |
| TSK-015 | CapexIntensityRule (completa 7 strategie) | be | agent | XS | done |
| TSK-016 | GrahamNumberCalculator | be | agent | XS | done |
| TSK-017 | DB V007 (dcf_method_override) | db | agent | XS | done |
| TSK-018 | DcfCalculator + GreenwaldCapexEstimator + FcfFallback | be | agent | L | done |
| TSK-019 | MarginOfSafetyEvaluator + AnalysisController | be | agent | S | done |
| TSK-020 | QA: integration test pipeline analisi E2E | qa | agent | S | done |
| TSK-037 | QA: contract test OpenAPI drift | qa | agent | XS | done |

---

## Sprint 3 — Frontend MVP + auth + watchlist + dashboard completa (EP-001→EP-006)

**Obiettivo:** SPA navigabile end-to-end: ricerca → analisi Traffic Light → grafici →
moat checklist → watchlist + auth.

**Stato:** COMPLETATO.

| TSK | Titolo | Layer | Consumer | Estimate | Status |
|-----|--------|-------|----------|----------|--------|
| TSK-002 | BE SearchController + SearchService + FmpAdapter | be | agent | S | done |
| TSK-003 | FE SearchBar + landing page | fe | agent | S | done |
| TSK-004 | QA integration test SearchController | qa | agent | XS | done |
| TSK-006 | FE ScreenerForm + page /screener | fe | agent | S | done |
| TSK-007 | FE ResultsList + Ag-Grid | fe | agent | S | done |
| TSK-021 | FE TrafficLightPanel + RuleSignalCard + ValuationSummary | fe | agent | M | done |
| TSK-038 | FE StaleDataBadge | fe | agent | XS | done |
| TSK-022 | QA E2E Playwright: ricerca → Traffic Light | qa | agent | S | done |
| TSK-023 | BE HistoricalController GET /api/historical/{ticker} | be | agent | S | done |
| TSK-024 | FE HistoricalChart Recharts | fe | agent | S | done |
| TSK-025 | DB V006 (moat_checklist_entry) | db | agent | XS | done |
| TSK-028 | DB V005 (watchlists) + V008 (fmp_event_log) | db | agent | XS | done |
| TSK-033 | BE AuthController + SecurityConfig + JwtService | be | agent | M | done |
| TSK-026 | BE MoatChecklistController | be | agent | S | done |
| TSK-027 | FE MoatChecklist component | fe | agent | S | done |
| TSK-034 | FE LoginPage + RegisterPage + useAuthStore | fe | agent | S | done |
| TSK-029 | BE WatchlistController | be | agent | S | done |
| TSK-035 | FE WatchlistPage + WatchlistTable + AddToWatchlistButton | fe | agent | S | done |
| TSK-036 | QA E2E Playwright: auth + watchlist | qa | agent | XS | done |

---

## Sprint 4 — Delta ADR-010 + ADR-011: auth consolidation + DCF override completo

**Obiettivo:** Colmare i gap formali di US-018/019 (ADR-010: sliding refresh, 409 RFC 9457,
contract-test generic error, banner FE sessione scaduta) e implementare US-020 (ADR-011:
GET override, feasibility 422, dcfMethodSource, Vary header, FE panel, OpenAPI).

**Stato:** COMPLETATO.

| TSK | Titolo | Layer | Consumer | Estimate | Status |
|-----|--------|-------|----------|----------|--------|
| TSK-040 | DB V009: add first_issued_at to refresh_tokens | db | agent | XS | done |
| TSK-039 | BE GlobalExceptionHandler: 409 email-already-registered | be | agent | S | done |
| TSK-041 | BE AuthService: sliding refresh TTL 7d + cap 30d | be | agent | M | done |
| TSK-044 | BE DcfOverrideController: GET /api/dcf-overrides/{ticker} | be | agent | S | done |
| TSK-045 | BE DcfFeasibilityCheck + DcfMethodUnfeasibleException + 422 | be | agent | M | done |
| TSK-046 | BE AnalyzeTickerService: auth-aware + dcfMethodSource + Vary | be | agent | M | done |
| TSK-042 | QA Contract-test: generic-error login + 409 register OpenAPI | qa | agent | S | done |
| TSK-047 | QA Contract-test: 4 path US-020 (USER_OVERRIDE, DEFAULT_POLICY x2, Vary, 422) | qa | agent | S | done |
| TSK-049 | BE OpenAPI: GET dcf-overrides, 422, dcfMethodSource, Vary | be | agent | XS | done |
| TSK-043 | FE useAuthStore: gestione 401 + banner sessione scaduta | fe | agent | S | done |
| TSK-048 | FE DcfOverridePanel: badge source + form override + 422 inline | fe | agent | M | done |

---

## Sprint 5 — R1.1 Consolidamento produzione (EP-007, EP-008, EP-009)

**Obiettivo:** Hardening contratti e routing FE, deploy prod Compose+nginx, backup/retention,
checklist cutover, throttling FMP (default ADR-016); wiki FMP in parallelo (human).

**Stato:** 22/23 TSK `done` — unico residuo TSK-071 `todo` (blocked su gap `fmp-stable-rate-limiting`).

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|-----|--------|
| TSK-050 | BE FlatteningProblemDetailHttpMessageConverter | be | agent | S | US-021 | done |
| TSK-051 | QA Assert ProblemDetail top-level (IT + contract) | qa | agent | S | US-021 | done |
| TSK-052 | BE OpenAPI ProblemDetail extension top-level | be | agent | XS | US-021 | done |
| TSK-053 | FE Bump swr peer-compatible React 19 | fe | agent | S | US-022 | done |
| TSK-054 | Infra Rimuovi --legacy-peer-deps CI/Dockerfile | infra | agent | XS | US-022 | done |
| TSK-055 | FE Route /analysis?ticker= static export | fe | agent | S | US-023 | done |
| TSK-056 | FE Link interni /analysis?ticker= | fe | agent | S | US-023 | done |
| TSK-057 | QA E2E ticker fuori whitelist demo | qa | agent | S | US-023 | done |
| TSK-058 | BE Property fmp.cache.profile-ttl-hours | be | agent | XS | US-024 | done |
| TSK-059 | QA Test scadenza cache profilo TTL | qa | agent | S | US-024 | done |
| TSK-060 | QA Verifica ADR stack allineati (doc-only) | qa | agent | XS | US-025 | done |
| TSK-061 | Infra docker-compose.prod.yml + nginx TLS | infra | agent | M | US-026 | done |
| TSK-062 | Infra .env.prod.example variabili deploy | infra | agent | XS | US-026 | done |
| TSK-063 | Infra Script backup pg_dump + retention 14d | infra | agent | S | US-027 | done |
| TSK-064 | BE Scheduled purge fmp_api_event_log 90d | be | agent | S | US-027 | done |
| TSK-065 | QA Drill restore PostgreSQL staging | qa | agent | S | US-027 | done |
| TSK-066 | QA Playwright smoke cutover R1.1 staging | qa | agent | M | US-028 | done |
| TSK-067 | QA Esecuzione checklist cutover registro | qa | agent | S | US-028 | done |
| TSK-068 | Human Ingest wiki FMP rate/URL/errori | infra | human | M | US-029 | done |
| TSK-069 | BE Env FMP_RATE_LIMIT_PER_MINUTE | be | agent | S | US-030 | done |
| TSK-070 | QA WireMock 429 retry + event log | qa | agent | S | US-030 | done |
| TSK-071 | BE Ricalibra rate limit da wiki post US-029 | be | agent | XS | US-031 | todo |
| TSK-072 | BE Migrazione FmpAdapterRestClient + DTO + fixture da v3 a /stable | be | agent | L | US-031 | done |

---

## Sprint 5.5 — Hotfix R1.1.x bug rule engine (EP-007 fase 2)

**Obiettivo:** Correggere tre bug produzione: (1) DCF intrinsic value totale aziendale invece
di per-share; (2) ROE/ROIC NOT_CALCULABLE per mismatch @JsonProperty; (3) "Dati al" epoch ms
grezzo nel FE.

**Stato:** COMPLETATO — 12/12 TSK `done`. US-052, US-053, US-054 chiuse.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|-----|--------|
| TSK-143 | BE DcfCalculator: divide per sharesOutstanding; AnalyzeTickerService wiring | be | agent | S | US-052 | done |
| TSK-144 | BE MarginOfSafetyEvaluator: rationale messaggi per-share | be | agent | XS | US-052 | done |
| TSK-145 | BE DcfResult: campo sharesUsed (audit) + OpenAPI description | be | agent | S | US-052 | done |
| TSK-146 | QA DcfCalculatorTest + MarginOfSafetyEvaluatorTest + AnalysisControllerIT | qa | agent | M | US-052 | done |
| TSK-147 | QA Contract test OpenAPI drift: dcfIntrinsicValue description per-share | qa | agent | XS | US-052 | done |
| TSK-148 | BE KeyMetricsDto: @JsonProperty("returnOnEquity") + @JsonProperty("returnOnInvestedCapital") | be | agent | XS | US-053 | done |
| TSK-149 | QA KeyMetricsDtoTest: deserializzazione fixture /stable | qa | agent | S | US-053 | done |
| TSK-150 | QA Integration test rule engine: ROE/ROIC non NOT_CALCULABLE su AAPL/MSFT/TTD | qa | agent | S | US-053 | done |
| TSK-151 | FE Crea lib/format-date.ts con formatSnapshotDate | fe | agent | XS | US-054 | done |
| TSK-152 | FE Sostituire raw display dataSnapshotAt con formatSnapshotDate | fe | agent | S | US-054 | done |
| TSK-153 | QA Vitest unit test formatSnapshotDate | qa | agent | XS | US-054 | done |
| TSK-154 | QA Playwright E2E: /analysis/AAPL verifica "Dati al" formato leggibile | qa | agent | XS | US-054 | done |

**Totale Sprint 5.5:** 12 TSK (3 be, 2 fe, 7 qa)

---

## Sprint 6 — Graham Defensive Completeness (EP-010)

**Obiettivo:** Completare i 6 criteri Graham difensivi mancanti nel Rule Engine, aggiornare
TrafficLight FE a 13 ruleId, aggiornare contratto OpenAPI.

**Stato:** COMPLETATO — 18/18 TSK `done`. EP-010 chiusa.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-073 | BE Implementa SizeRule (SIZE_LATEST) | be | agent | S | US-032 | done |
| TSK-074 | QA Test integrazione SizeRule — 3 scenari | qa | agent | S | US-032 | done |
| TSK-075 | BE Implementa EarningsStabilityRule (EARNINGS_STABILITY_10Y) | be | agent | S | US-033 | done |
| TSK-076 | QA Test integrazione EarningsStabilityRule — 4 scenari | qa | agent | S | US-033 | done |
| TSK-077 | BE Implementa EpsGrowthRule (EPS_GROWTH_10Y) | be | agent | S | US-034 | done |
| TSK-078 | QA Test integrazione EpsGrowthRule — 5 scenari | qa | agent | S | US-034 | done |
| TSK-079 | BE Implementa Pe3yAvgRule (PE_3Y_AVG) | be | agent | S | US-035 | done |
| TSK-080 | QA Test integrazione Pe3yAvgRule — 4 scenari | qa | agent | S | US-035 | done |
| TSK-081 | BE Implementa PbLatestRule (PB_LATEST) | be | agent | S | US-036 | done |
| TSK-082 | QA Test integrazione PbLatestRule — 4 scenari | qa | agent | S | US-036 | done |
| TSK-083 | BE Estendi FmpAdapter con getDividendHistory | be | agent | S | US-037 | done |
| TSK-084 | DB Migration V010__fmp_dividend_history_snapshot | db | agent | XS | US-037 | done |
| TSK-085 | BE Implementa DividendContinuityRule (DIVIDEND_CONTINUITY_20Y) | be | agent | S | US-037 | done |
| TSK-086 | QA Test DividendContinuityRule + contratto adapter | qa | agent | S | US-037 | done |
| TSK-087 | BE Estendi OpenAPI con 6 nuovi ruleId Graham | be | agent | S | cross-EP010 | done |
| TSK-088 | FE Aggiorna TrafficLight component a 13 ruleId | fe | agent | S | cross-EP010 | done |
| TSK-089 | QA Contract test OpenAPI drift — 13 ruleId | qa | agent | XS | cross-EP010 | done |
| TSK-090 | QA Integration test E2E EP-010 — AAPL/MSFT/KO fixture | qa | agent | M | cross-EP010 | done |

**Totale Sprint 6:** 18 TSK (8 be, 1 fe, 1 db, 8 qa)

---

## Sprint 7 — Deep Analysis backend (EP-011 — BE/DB/Infra) + LLM telemetry + ROE dual lookback

**Obiettivo:** Infrastruttura RAG completa (SEC EDGAR adapter + filing blob cache + pgvector +
EmbeddingService sidecar), pipeline analisi qualitativa (MungerInversion LLM + NewsSentiment +
PriceAction), cascade verdetto, endpoint `/api/analysis/{ticker}/deep`. LLM telemetry + budget
config (ADR-019/ADR-020). ROE_5Y_AVG + prompt Munger dual lookback.

**Stato:** COMPLETATO — 37/37 TSK `done`. US-038..US-045 + US-055 chiuse.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-091 | BE SecEdgarAdapter interface + SecEdgarRestClient | be | agent | M | US-038 | done |
| TSK-092 | BE Cache CIK→ticker TTL 30gg | be | agent | S | US-038 | done |
| TSK-093 | QA WireMock SecEdgarRestClient — rate-limit, 429, cache | qa | agent | S | US-038 | done |
| TSK-094 | BE Estendi FmpAdapter con getSecFilings | be | agent | S | US-039 | done |
| TSK-095 | DB Migration V011__filing_blob | db | agent | XS | US-039 | done |
| TSK-096 | BE Filing10KQDownloaderService — download, HTML strip, persist | be | agent | M | US-039 | done |
| TSK-097 | QA Test Filing10KQDownloaderService — cache TTL, limit 50MB | qa | agent | S | US-039 | done |
| TSK-098 | DB Migration V012__pgvector_enable + filing_chunks + HNSW | db | agent | S | US-040 | done |
| TSK-099 | Infra Sidecar Python FastAPI embeddings Snowflake Arctic Embed L v2.0 | infra | agent | M | US-040 | done |
| TSK-100 | BE EmbeddingService Kotlin HTTP client verso sidecar | be | agent | S | US-040 | done |
| TSK-101 | BE FilingChunkingService — split testo RecursiveCharSplitter | be | agent | S | US-040 | done |
| TSK-102 | BE FilingRagService — persist chunks + embedding + similarity search | be | agent | M | US-040 | done |
| TSK-103 | QA Test integrazione pgvector — chunking, embedding, retrieval, idempotenza | qa | agent | M | US-040 | done |
| TSK-104 | BE AnthropicClient config + LlmResilienceConfig circuit breaker | be | agent | S | US-041 | done |
| TSK-105 | BE MungerInversionAnalyzer — 10 query inversione + prompt template | be | agent | L | US-041 | done |
| TSK-106 | DB Migration V013__filing_analysis (deep_analysis_report) | db | agent | XS | US-041 | done |
| TSK-107 | QA Test MungerInversionAnalyzer — mock Anthropic + golden response | qa | agent | S | US-041 | done |
| TSK-108 | BE Estendi FmpAdapter con getStockNews | be | agent | S | US-042 | done |
| TSK-109 | BE NewsSentimentService — classificatore Claude Opus + cache | be | agent | M | US-042 | done |
| TSK-110 | DB Migration V014__news_sentiment_analysis | db | agent | XS | US-042 | done |
| TSK-111 | QA Test NewsSentimentService — golden dataset, cache, limite 50 LLM | qa | agent | S | US-042 | done |
| TSK-112 | BE Estendi FmpAdapter con getHistoricalEod | be | agent | S | US-043 | done |
| TSK-113 | BE PriceActionAnalyzer — drawdown 52w + panic/deterioration flags | be | agent | S | US-043 | done |
| TSK-114 | QA Test PriceActionAnalyzer — boundary flags + migration V015 | qa | agent | S | US-043 | done |
| TSK-115 | BE MungerDecisionService — cascade 6 verdetti | be | agent | M | US-044 | done |
| TSK-116 | BE PositionSizeCalculator — port da agent.py | be | agent | S | US-044 | done |
| TSK-117 | QA Test MungerDecisionService — 6 combinazioni cascade + determinismo | qa | agent | S | US-044 | done |
| TSK-118 | BE DeepAnalysisController + DeepAnalysisService orchestrator | be | agent | M | US-045 | done |
| TSK-119 | BE DTO DeepAnalysisResultDto + OpenAPI schema /deep + migration V016 | be | agent | S | US-045 | done |
| TSK-120 | QA Integration test E2E /deep — tutti i mock provider | qa | agent | M | US-045 | done |
| TSK-121 | QA Contract test OpenAPI /deep — drift guard | qa | agent | S | US-045 | done |
| TSK-155 | DB Migration V0XX__llm_cost_tracking | db | agent | S | US-055 | done |
| TSK-156 | BE LlmCostCounterService + LlmCallLogger AOP + LlmBudgetGuard + endpoint admin | be | agent | L | US-055 | done |
| TSK-160 | BE RoeCalculator.fiveYearAverage — porting ROE_5Y_AVG da agent.py | be | agent | S | US-045 | done |
| TSK-161 | BE Estendi DeepAnalysisResponse con blocco roe (fiveYearAvg + tenYearAvg) | be | agent | S | US-045 | done |
| TSK-162 | BE Aggiorna prompt Munger LLM con dual lookback ROE + nota divergenza | be | agent | S | US-041 | done |
| TSK-163 | QA Contract + unit test ROE dual lookback — payload /deep + edge case | qa | agent | S | US-045 | done |

**Totale Sprint 7:** 37 TSK (17 be, 6 db, 1 infra, 13 qa)

---

## Sprint 8 — Deep Analysis frontend (EP-011 — FE) + LLM budget FE

**Obiettivo:** Tab "Deep Analysis" sul frontend con tutti i componenti UI, SWR hook, test E2E.
Budget bar scheda dettaglio + LlmBudgetAdminPanel (ADR-019).

**Stato:** COMPLETATO — 7/7 TSK `done`. US-046 + US-055 FE chiuse.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-122 | FE Route /analysis/{ticker}/deep + page component Next.js | fe | agent | S | US-046 | done |
| TSK-123 | FE Componenti UI Deep Analysis (5 componenti) | fe | agent | M | US-046 | done |
| TSK-124 | FE API client estensione + SWR hook useDeepAnalysis | fe | agent | S | US-046 | done |
| TSK-125 | QA Test E2E Playwright Deep Analysis — happy path + value-trap + invalido | qa | agent | M | US-046 | done |
| TSK-157 | FE Budget bar + cache-hit signal sul pulsante Avvia analisi LLM | fe | agent | S | US-046 | done |
| TSK-158 | FE LlmBudgetAdminPanel — campo cap + modal conferma + PUT budget | fe | agent | M | US-055 | done |
| TSK-159 | QA Integration tests LLM budget config — PUT + cache + audit + 400 + 403 | qa | agent | M | US-055 | done |

**Totale Sprint 8:** 7 TSK (5 fe, 2 qa)

---

## Sprint 9 — Top Value Picks batch (EP-012)

**Obiettivo:** UniverseScreenerService (FMP screener + 13-F + news scout), job notturno cron
02:00 UTC, persistenza top_value_picks, endpoint GET /api/top-picks paginato, pagina FE
/top-picks con filtri e deep-link.

**Stato:** COMPLETATO — 17/17 TSK `done`. US-047..US-051 chiuse. EP-012 `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-126 | BE UniverseScreenerService — orchestratore FMP NASDAQ+NYSE | be | agent | M | US-047 | done |
| TSK-127 | BE InstitutionalHoldingsService — overlay 13-F SEC EDGAR | be | agent | M | US-047 | done |
| TSK-128 | BE NewsScoutService — Claude Opus scout top-200 | be | agent | S | US-047 | done |
| TSK-129 | BE SectorBlacklist + SETTORI_BUFFETT_OK + FmpAdapter.companyScreener | be | agent | S | US-047 | done |
| TSK-130 | QA Test UniverseScreenerService — mock FMP + SEC, dedup, cap 500 | qa | agent | S | US-047 | done |
| TSK-131 | BE TopValuePicksJob @Scheduled cron 02:00 UTC + pipeline batch | be | agent | M | US-048 | done |
| TSK-132 | BE BatchResilienceConfig — RateLimiter fmp-batch separato | be | agent | S | US-048 | done |
| TSK-133 | Infra Migration V017__top_picks_run_log + scheduler enable yaml | infra | agent | XS | US-048 | done |
| TSK-134 | QA Test rate-limit batch + idempotenza job | qa | agent | S | US-048 | done |
| TSK-135 | DB Migration V016__top_value_picks — PK composta + indice | db | agent | XS | US-049 | done |
| TSK-136 | BE TopValuePickEntity + TopValuePickRepository JPA | be | agent | S | US-049 | done |
| TSK-137 | QA Test persistenza top_value_picks — PK, retention, indice | qa | agent | S | US-049 | done |
| TSK-138 | BE TopPicksController GET /api/top-picks paginazione + filtri | be | agent | M | US-050 | done |
| TSK-139 | QA OpenAPI schema /top-picks + contract test + integration test | qa | agent | S | US-050 | done |
| TSK-140 | FE Route /top-picks page + tabella ordinabile Next.js | fe | agent | M | US-051 | done |
| TSK-141 | FE Filtri sidebar + useTopPicks SWR hook | fe | agent | S | US-051 | done |
| TSK-142 | QA Test E2E Playwright /top-picks — filtro, datepicker, navigazione /deep | qa | agent | S | US-051 | done |

**Totale Sprint 9:** 17 TSK (7 be, 2 fe, 1 db, 1 infra, 6 qa)

---

## Sprint 10 — Mr. Market Context Flags (EP-013)

**Obiettivo:** Integrare 2 indicatori tecnici FMP `/stable` come advisory context flags
complementari al verdetto del Rule Engine: RSI 14-day (Mr. Market sentiment) e SMA200 (trend
lungo periodo). Esposti su `/api/analysis/{ticker}` in sezione dedicata `contextFlags`.

**Stato:** COMPLETATO — 6/6 TSK `done`. US-056, US-057 chiuse. EP-013 `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-164 | FmpAdapter.getTechnicalIndicator generico (riusato da US-056 + US-057) | be | agent | S | US-056 | done |
| TSK-165 | RsiContextEvaluator + contextFlags nel RuleEngineResultResponse | be | agent | S | US-056 | done |
| TSK-166 | LongTermTrendEvaluator (SMA200) + estensione contextFlags | be | agent | S | US-057 | done |
| TSK-167 | QA FmpAdapter.getTechnicalIndicator + RsiContextEvaluator + LongTermTrendEvaluator | qa | agent | M | US-056 | done |
| TSK-168 | FE badge "Mr. Market Sentiment" (RSI flag) | fe | agent | S | US-056 | done |
| TSK-169 | FE badge "Long-Term Trend" (SMA200 flag) | fe | agent | S | US-057 | done |

**Totale Sprint 10:** 6 TSK (3 be, 2 fe, 1 qa)

---

## Sprint 11 — Logging Strutturato + PII Redaction + Security Events (EP-014)

**Obiettivo:** Sistema di logging strutturato con formato per ambiente (JSON prod, pretty dev),
Correlation ID end-to-end, redazione automatica PII, leak detection CI, logging eventi di
sicurezza e retention GDPR differenziata. ADR-021.

**Stato:** COMPLETATO — 14/14 TSK `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-170 | Logback profili prod/dev + logstash-logback-encoder + AsyncAppender | be | agent | M | US-058 | done |
| TSK-171 | QA Test formato log JSON/pretty + switch env var + benchmark p99 | qa | agent | S | US-058 | done |
| TSK-172 | CorrelationIdFilter servlet filter: UUID v4, MDC, header response | be | agent | S | US-059 | done |
| TSK-173 | Estendi GlobalExceptionHandler: correlationId in ProblemDetail | be | agent | XS | US-059 | done |
| TSK-174 | QA Test CorrelationIdFilter: propagazione, concorrenza, ProblemDetail | qa | agent | S | US-059 | done |
| TSK-175 | PiiRedactionEncoder wrapper + config esternalizzata + ricorsività | be | agent | L | US-060 | done |
| TSK-176 | QA Test PiiRedactionEncoder: 6 categorie PII + nested + benchmark | qa | agent | M | US-060 | done |
| TSK-177 | Gradle task piiLeakDetection post-test CI: regex 6+ categorie | infra | agent | M | US-061 | done |
| TSK-178 | QA Test scenari leak detection: PAN non redatto → fail; redazione → pass | qa | agent | S | US-061 | done |
| TSK-179 | SecurityEventLogger @Component: 6+ categorie + marker SECURITY_EVENT | be | agent | M | US-062 | done |
| TSK-180 | QA Test SecurityEventLogger: login/password/403 + formato + correlationId | qa | agent | S | US-062 | done |
| TSK-181 | Logback retention operativi 30d + SiftingAppender security 365d | be | agent | S | US-063 | done |
| TSK-182 | Script pseudonimizzazione log per userId (diritto all'oblio) | be | agent | S | US-063 | done |
| TSK-183 | QA Test retention rotazione + pseudonimizzazione | qa | agent | S | US-063 | done |

**Totale Sprint 11:** 14 TSK (7 be, 1 infra, 6 qa)

---

## Sprint 12 — Design Token System + UI Accessibility (EP-016)

**Obiettivo:** Sistema di design token semantici M3-aligned (colori OKLCH, tipografia, shape,
motion) sopra shadcn/ui + Tailwind, switch light/dark persistente, audit e fix WCAG 2.2 AA
su tutte le viste. ADR-023.

**Stato:** PARZIALE — 7/10 TSK `done` (US-072 non ancora avviata).

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-184 | Token CSS semantici: colors OKLCH + typography + shape + tailwind.config.ts | fe | agent | M | US-069 | done |
| TSK-185 | Migrazione 5 componenti principali da classi hardcoded a token semantici | fe | agent | M | US-069 | done |
| TSK-186 | QA Vitest test token system: seed change, tipografia, shape | qa | agent | S | US-069 | done |
| TSK-187 | ThemeProvider + useTheme + colors-dark.css + anti-FOUC script | fe | agent | M | US-070 | done |
| TSK-188 | QA Test ThemeProvider: prefers-color-scheme, persistenza, FOUC, contrasto | qa | agent | S | US-070 | done |
| TSK-189 | Token motion.css + state layer opacità + prefers-reduced-motion | fe | agent | S | US-071 | done |
| TSK-190 | QA Test motion: reduced-motion, state layers, focus visibile | qa | agent | S | US-071 | done |
| TSK-191 | Audit fix WCAG 2.2 AA: h1, focus, label, alt, zoom 200% su tutte le viste | fe | agent | L | US-072 | todo |
| TSK-192 | QA Lighthouse CI + axe-core: target >= 95, zero serious/critical | qa | agent | M | US-072 | todo |
| TSK-193 | QA Test E2E a11y tastiera: flussi critici senza mouse + screen reader | qa | agent | M | US-072 | todo |

**Totale Sprint 12:** 10 TSK (5 fe, 5 qa)

**Nota parallelismo:** Sprint 11 (BE-heavy) e Sprint 12 (FE-heavy) possono essere sviluppati
in parallelo su layer diversi. La dipendenza cross-sprint è minima: solo EP-015 (Sprint 13)
richiede output di entrambi.

---

## Sprint 13 — Notifiche Errori Frontend (EP-015)

**Obiettivo:** NotificationService centralizzato con toast accessibili WCAG 2.2 AA, mappatura
codici errore i18n, gestione categorizzata errori di rete, validazione form inline accessibile,
Correlation ID copiabile. ADR-022.

**Stato:** TODO — 0/11 TSK `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-194 | NotificationProvider React Context + useNotification hook API 4 livelli | fe | agent | M | US-064 | todo |
| TSK-195 | NotificationToast wrapper shadcn/ui: WCAG roles, auto-dismiss, Esc, correlationId | fe | agent | M | US-064 | todo |
| TSK-196 | QA Test NotificationProvider + Toast: 4 livelli, correlationId, a11y | qa | agent | S | US-064 | todo |
| TSK-197 | errorCodeMap + locales/it.json: mapping 8+ type URI ProblemDetail + fallback | fe | agent | S | US-065 | todo |
| TSK-198 | QA Test errorCodeMap: mapping + fallback + nessun HTTP raw | qa | agent | S | US-065 | todo |
| TSK-199 | networkErrorInterceptor: offline/timeout/5xx/4xx + X-Correlation-Id | fe | agent | M | US-066 | todo |
| TSK-200 | QA Test networkErrorInterceptor: 4 scenari rete | qa | agent | S | US-066 | todo |
| TSK-201 | FormErrorSummary + aggiornamento form login/register/watchlist | fe | agent | M | US-067 | todo |
| TSK-202 | QA Test FormErrorSummary: inline, summary, aria-describedby | qa | agent | S | US-067 | todo |
| TSK-203 | Hardening a11y NotificationToast: contrasto, icone, multi-canale | fe | agent | S | US-068 | todo |
| TSK-204 | QA axe-core audit notifiche: zero serious/critical, screen reader | qa | agent | S | US-068 | todo |

**Totale Sprint 13:** 11 TSK (6 fe, 5 qa)

**Dipendenze cross-sprint:**
- TSK-195 dipende da Sprint 11 TSK-172 (Correlation ID header disponibile)
- TSK-203 dipende da Sprint 12 TSK-184 (token colori per contrasto WCAG)

---

## Sprint 14 — Protezione Rotte e Sessione (EP-017)

**Obiettivo:** AuthGuard centralizzato Next.js middleware, mappa rotte dichiarativa, migrazione
refresh token a cookie httpOnly Secure SameSite=Strict, refresh automatico con coda richieste,
idle/absolute timeout con prompt, logout completo con blocco history. ADR-024.

**Stato:** TODO — 0/14 TSK `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-205 | route-config.ts mappa rotte dichiarativa tipizzata | fe | agent | S | US-074 | todo |
| TSK-206 | AuthGuard middleware.ts Next.js: redirect login/returnUrl/403 | fe | agent | M | US-073 | todo |
| TSK-207 | Pagina /403 + messaggio sessione scaduta su /login | fe | agent | S | US-073 | todo |
| TSK-208 | QA Test E2E AuthGuard + route map: 6+ scenari | qa | agent | M | US-073/074 | todo |
| TSK-209 | BE AuthController migrazione cookie httpOnly: login/refresh/logout | be | agent | L | US-075 | todo |
| TSK-210 | BE OpenAPI: rimuovi refreshToken da response body | be | agent | XS | US-075 | todo |
| TSK-211 | FE useAuthStore aggiornato: rehydration via cookie refresh | fe | agent | M | US-075 | todo |
| TSK-212 | QA Test storage: no localStorage, httpOnly, rehydration F5, revoca | qa | agent | M | US-075 | todo |
| TSK-213 | useTokenRefresh hook: timer pre-expiry 60s, mutex coda | fe | agent | M | US-076 | todo |
| TSK-214 | QA Test refresh: no doppio refresh, sessione 20+ min, fallback | qa | agent | S | US-076 | todo |
| TSK-215 | IdleTimeoutProvider: idle 15min + prompt 60s + absolute 8h | fe | agent | M | US-077 | todo |
| TSK-216 | QA Test timeout: idle, extend, no-interaction, absolute, a11y | qa | agent | S | US-077 | todo |
| TSK-217 | useLogout hook: revoca + pulizia store/cache + blocco history | fe | agent | M | US-078 | todo |
| TSK-218 | QA Test E2E logout: revoca, cookie, store, back button, resilienza | qa | agent | S | US-078 | todo |

**Totale Sprint 14:** 14 TSK (2 be, 7 fe, 5 qa)

---

## Sprint 15 — Hardening Sicurezza e Compliance (EP-018)

**Obiettivo:** Defense-in-depth enforcement, CSP nonce + CSRF per cookie auth, MFA TOTP con
recovery codes, rate limiting + progressive lockout + CAPTCHA threshold, HIBP password check,
dichiarazione formale PCI-DSS non applicabile. ADR-025. DB: mfa_secrets + login_attempts.

**Stato:** TODO — 0/19 TSK `done`. `pending_clarification: [Q_005]` su TSK-237 (ADR-025 proposed).

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-219 | BE Audit + enforcement defense-in-depth: @Valid, @PreAuthorize, filtro userId | be | agent | M | US-079 | todo |
| TSK-220 | QA Test defense-in-depth: 401, 403, filtro userId, payload invalido | qa | agent | M | US-079 | todo |
| TSK-221 | BE SecurityHeadersConfig CSP header: script-src no unsafe-inline | be | agent | S | US-080 | todo |
| TSK-222 | FE Next.js middleware CSP nonce per inline script | fe | agent | S | US-080 | todo |
| TSK-223 | BE CsrfTokenConfig: CSRF per /api/auth/refresh e /api/auth/logout | be | agent | S | US-080 | todo |
| TSK-224 | QA Test CSP + CSRF: header, XSS bloccato, 403 no CSRF, SameSite, E2E | qa | agent | M | US-080 | todo |
| TSK-225 | DB Migration V018__create_mfa_secrets | db | agent | XS | US-081 | todo |
| TSK-226 | DB Migration V019__create_login_attempts + indici | db | agent | XS | US-081 | todo |
| TSK-227 | BE TotpService: secret TOTP, verifica codice, recovery codes BCrypt | be | agent | M | US-081 | todo |
| TSK-228 | BE MfaController: endpoint enroll, verify, challenge, recovery, delete | be | agent | M | US-081 | todo |
| TSK-229 | BE RateLimitingFilter: limiti IP + account su login/register/password-reset | be | agent | M | US-081 | todo |
| TSK-230 | BE BruteForceProtectionService: lockout progressivo + CAPTCHA + cleanup | be | agent | M | US-081 | todo |
| TSK-231 | BE HibpClient: verifica password compromesse k-anonymity SHA-1 | be | agent | S | US-081 | todo |
| TSK-232 | FE MfaEnrollmentPage: QR code + verifica TOTP + recovery codes | fe | agent | M | US-081 | todo |
| TSK-233 | FE MfaChallengeForm: form TOTP durante login MFA | fe | agent | S | US-081 | todo |
| TSK-234 | QA Test MFA: enrollment, login TOTP, recovery, disabilitazione | qa | agent | M | US-081 | todo |
| TSK-235 | QA Test rate limiting + brute force: delay, CAPTCHA, lockout 30min | qa | agent | M | US-081 | todo |
| TSK-236 | QA Test HIBP: password compromessa rifiutata, sicura accettata | qa | agent | S | US-081 | todo |
| TSK-237 | QA Verifica codebase no dati carta + validazione ADR PCI-DSS | qa | agent | S | US-082 | todo |

**Totale Sprint 15:** 19 TSK (8 be, 2 fe, 2 db, 7 qa)

---

## Riepilogo TSK per layer

| Release | Sprint | infra | db | be | fe | qa | Totale | Stato |
|---------|--------|-------|-----|-----|-----|-----|--------|-------|
| R1.0 | 1–4 | 2 | 7 | 23 | 13 | 7 | **49** | done |
| R1.1 | 5 | 5 | 0 | 7 | 3 | 8 | **23** | 22 done, 1 todo (TSK-071) |
| R1.1.x | 5.5 | 0 | 0 | 3 | 2 | 7 | **12** | done |
| R2.0 | 6 | 0 | 1 | 8 | 1 | 8 | **18** | done |
| R2.0 | 7 | 1 | 6 | 17 | 0 | 13 | **37** | done |
| R2.0 | 8 | 0 | 0 | 0 | 5 | 2 | **7** | done |
| R2.0 | 9 | 1 | 1 | 7 | 2 | 6 | **17** | done |
| R2.1 | 10 | 0 | 0 | 3 | 2 | 1 | **6** | done |
| R3.0 | 11 | 1 | 0 | 7 | 0 | 6 | **14** | done |
| R3.0 | 12 | 0 | 0 | 0 | 5 | 5 | **10** | 7 done, 3 todo (US-072) |
| R3.0 | 13 | 0 | 0 | 0 | 6 | 5 | **11** | todo |
| R3.0 | 14 | 0 | 0 | 2 | 7 | 5 | **14** | todo |
| R3.0 | 15 | 0 | 2 | 8 | 2 | 7 | **19** | todo |
| | **TOTALE** | **10** | **17** | **85** | **48** | **80** | **237** | 189 done, 1 todo (R1.1), 47 todo (R3.0) |

---

## Ordinamento raccomandato e parallelismo

```
Sprint 11 (EP-014 Logging, BE-heavy)  ═══╗
                                          ╠══► Sprint 13 (EP-015 Notifications, FE)
Sprint 12 (EP-016 Design Tokens, FE-heavy)╝        │
                                                    ▼
                                            Sprint 14 (EP-017 Session/Routes, FE+BE)
                                                    │
                                                    ▼
                                            Sprint 15 (EP-018 Security, BE+FE+DB)
```

**Sprint 11 e 12** possono essere sviluppati **in parallelo** (layer diversi: BE vs FE).
**Sprint 13** richiede output di entrambi (Correlation ID + design tokens).
**Sprint 14** è indipendente ma logicamente segue il consolidamento auth.
**Sprint 15** dipende da Sprint 14 per la CSRF protection sugli endpoint cookie-based.

---

## Dipendenze cross-sprint (R3.0)

```
Sprint 11 → Sprint 13:
  TSK-172 (CorrelationIdFilter) ──→ TSK-195 (NotificationToast correlationId)

Sprint 12 → Sprint 13:
  TSK-184 (design tokens) ──→ TSK-203 (hardening a11y contrasto)

Sprint 14 → Sprint 15:
  TSK-209 (cookie httpOnly auth) ──→ TSK-223 (CSRF per cookie endpoints)
```

---

## Prossimo /dev suggerito

**Sprint corrente:** Sprint 12 (completamento US-072) + Sprint 13 (EP-015 Notifiche Errori Frontend).

**Parallelismo possibile:**
- fe-dev: TSK-191 (audit WCAG AA, Sprint 12 US-072) + TSK-194 (NotificationProvider, Sprint 13 US-064)
- qa-dev: TSK-192, TSK-193 (Sprint 12 US-072) dopo TSK-191

**Residuo R1.1:** TSK-071 resta `todo` (blocked su gap `fmp-stable-rate-limiting`).

**ADR accepted:** ADR-021 (structured logging), ADR-023 (design token system).
**ADR pending:** ADR-022, ADR-024, ADR-025 `proposed`. TSK-237 → Q_005.
