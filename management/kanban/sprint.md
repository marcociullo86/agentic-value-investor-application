<!-- generated, do not edit — rigenerato da tpm ad ogni run -->
---
id: sprint
title: Sprint Plan — R1.0 MVP + R1.1 + R1.1.x hotfix + R2.0
generated: 2026-05-23
tpm: tpm
release: R2.0 (R1.1.x hotfix in corso)
r10_closed: 2026-05-22
r11_closed: TBD
---
# Sprint Plan

> **R1.0 MVP chiuso:** Sprint 1–4 completati (49/49 TSK `done`, 20/20 US, 6/6 EP).
> **R1.1 attivo:** Sprint 5 — 22 TSK nuovi (TSK-050…072); Sprint 6 lookahead (ex) incluso.
> **R1.1.x hotfix:** Sprint 5.5 — 12 TSK nuovi (TSK-143…154); EP-007 fase 2 riaperto; fix bug rule engine produzione (DCF per-share, ROE/ROIC mapping, FE date display).
> **R2.0 pianificato:** Sprint 6–9 — 70 TSK nuovi (TSK-073…142); 20 US nuove (US-032…051), 3 EP (EP-010…012).
> Ordine suggerito PM: **Sprint 5.5 (EP-007 fase 2 hotfix)** → **EP-010 (Sprint 6)** → **EP-011 BE (Sprint 7)** → **EP-011 FE (Sprint 8)** → **EP-012 (Sprint 9)**.

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

**Nota riconciliazione Sprint 3:** TSK-033 implementa register/login/refresh/logout e il mapping 409 nella DoD. Tuttavia i gap ADR-010 (GlobalExceptionHandler RFC 9457, sliding refresh, property configurabili) non erano stati completamente coperti → delta in Sprint 4 (TSK-039, TSK-040, TSK-041, TSK-042). TSK-018 implementa DcfOverride con auth reale (stub X-User-Id rimosso da TSK-033/034/035) → stato `done`, confermato. I gap ADR-011 (GET endpoint, feasibility, dcfMethodSource, Vary) coperti da Sprint 4.

---

## Sprint 4 — Delta ADR-010 + ADR-011: auth consolidation + DCF override completo

**Obiettivo:** Colmare i gap formali di US-018/019 (ADR-010: sliding refresh, 409 RFC 9457,
contract-test generic error, banner FE sessione scaduta) e implementare US-020 (ADR-011:
GET override, feasibility 422, dcfMethodSource, Vary header, FE panel, OpenAPI).

**Stato:** COMPLETATO (merge `sprint4/dcf-overrides` PR #2 + `sprint4/auth-consolidation` PR #3).

| TSK | Titolo | Layer | Consumer | Estimate | Depends on | Status |
|-----|--------|-------|----------|----------|------------|--------|
| TSK-040 | DB V009: add first_issued_at to refresh_tokens | db | agent | XS | TSK-033 | done |
| TSK-039 | BE GlobalExceptionHandler: 409 email-already-registered | be | agent | S | TSK-033 | done |
| TSK-041 | BE AuthService: sliding refresh TTL 7d + cap 30d | be | agent | M | TSK-033, TSK-040 | done |
| TSK-044 | BE DcfOverrideController: GET /api/dcf-overrides/{ticker} | be | agent | S | TSK-018, TSK-033 | done |
| TSK-045 | BE DcfFeasibilityCheck + DcfMethodUnfeasibleException + 422 | be | agent | M | TSK-018 | done |
| TSK-046 | BE AnalyzeTickerService: auth-aware + dcfMethodSource + Vary | be | agent | M | TSK-018, TSK-033, TSK-044 | done |
| TSK-042 | QA Contract-test: generic-error login + 409 register OpenAPI | qa | agent | S | TSK-039, TSK-041 | done |
| TSK-047 | QA Contract-test: 4 path US-020 (USER_OVERRIDE, DEFAULT_POLICY x2, Vary, 422) | qa | agent | S | TSK-044, TSK-045, TSK-046 | done |
| TSK-049 | BE OpenAPI: GET dcf-overrides, 422, dcfMethodSource, Vary | be | agent | XS | TSK-044, TSK-045, TSK-046 | done |
| TSK-043 | FE useAuthStore: gestione 401 + banner sessione scaduta | fe | agent | S | TSK-034, TSK-041 | done |
| TSK-048 | FE DcfOverridePanel: badge source + form override + 422 inline | fe | agent | M | TSK-044, TSK-046, TSK-034 | done |

---

## Sprint 5 — R1.1 Consolidamento produzione (EP-007, EP-008, EP-009)

**Obiettivo:** Hardening contratti e routing FE, deploy prod Compose+nginx, backup/retention,
checklist cutover, throttling FMP (default ADR-016); wiki FMP in parallelo (human).

**Stato:** IN CORSO — **Wave 1 COMPLETATA** (14/14 TSK `done`); **TSK-068 done** (US-029 wiki); **Wave 2** 6 TSK `todo` (TSK-061…067).

**Wave 1 (parallelo, done):** TSK-050…060, TSK-064, TSK-069, TSK-070 — commit `1e15c20` + fix CI `7844c67`…`1882767`.

**Wave 2 (deploy + wiki human):** TSK-061 → TSK-063 → TSK-065 → TSK-066 → TSK-067; TSK-068 ∥

| TSK | Titolo | Layer | Consumer | Est. | Depends on | US | Status |
|-----|--------|-------|----------|------|------------|-----|--------|
| TSK-050 | BE FlatteningProblemDetailHttpMessageConverter | be | agent | S | — | US-021 | done |
| TSK-051 | QA Assert ProblemDetail top-level (IT + contract) | qa | agent | S | TSK-050 | US-021 | done |
| TSK-052 | BE OpenAPI ProblemDetail extension top-level | be | agent | XS | TSK-050 | US-021 | done |
| TSK-053 | FE Bump swr peer-compatible React 19 | fe | agent | S | — | US-022 | done |
| TSK-054 | Infra Rimuovi --legacy-peer-deps CI/Dockerfile | infra | agent | XS | TSK-053 | US-022 | done |
| TSK-055 | FE Route /analysis?ticker= static export | fe | agent | S | — | US-023 | done |
| TSK-056 | FE Link interni /analysis?ticker= | fe | agent | S | TSK-055 | US-023 | done |
| TSK-057 | QA E2E ticker fuori whitelist demo | qa | agent | S | TSK-055, TSK-056 | US-023 | done |
| TSK-058 | BE Property fmp.cache.profile-ttl-hours | be | agent | XS | — | US-024 | done |
| TSK-059 | QA Test scadenza cache profilo TTL | qa | agent | S | TSK-058 | US-024 | done |
| TSK-060 | QA Verifica ADR stack allineati (doc-only) | qa | agent | XS | — | US-025 | done |
| TSK-061 | Infra docker-compose.prod.yml + nginx TLS | infra | agent | M | TSK-054 | US-026 | todo |
| TSK-062 | Infra .env.prod.example variabili deploy | infra | agent | XS | TSK-061 | US-026 | todo |
| TSK-063 | Infra Script backup pg_dump + retention 14d | infra | agent | S | TSK-061 | US-027 | todo |
| TSK-064 | BE Scheduled purge fmp_api_event_log 90d | be | agent | S | — | US-027 | done |
| TSK-065 | QA Drill restore PostgreSQL staging | qa | agent | S | TSK-063 | US-027 | todo |
| TSK-066 | QA Playwright smoke cutover R1.1 staging | qa | agent | M | TSK-050, TSK-057, TSK-061 | US-028 | todo |
| TSK-067 | QA Esecuzione checklist cutover registro | qa | agent | S | TSK-066, TSK-065, TSK-064 | US-028 | todo |
| TSK-068 | Human Ingest wiki FMP rate/URL/errori | infra | human | M | — | US-029 | done |
| TSK-069 | BE Env FMP_RATE_LIMIT_PER_MINUTE | be | agent | S | — | US-030 | done |
| TSK-070 | QA WireMock 429 retry + event log | qa | agent | S | TSK-069 | US-030 | done |
| TSK-071 | BE Ricalibra rate limit da wiki post US-029 | be | agent | XS | TSK-068, TSK-069 | US-031 | todo |
| TSK-072 | BE Migrazione FmpAdapterRestClient + DTO + fixture da v3 a /stable | be | agent | L | TSK-009, TSK-068 | US-031 | done |

**Nota US-025:** L4 già allineato (appendici ADR-001/002/003). TSK-060 = verifica formale, **nessun dev L5** salvo drift.

**Nota US-029:** TSK-068 `consumer: human` — wiki-keeper; `pending_clarification` su tre gap FMP. Non blocca TSK-069 (default 30/min ADR-016).

---

## Sprint 5.5 — Hotfix R1.1.x bug rule engine (EP-007 fase 2)

**Obiettivo:** Correggere tre bug produzione scoperti il 2026-05-23 nel rule engine e nel frontend: (1) DCF intrinsic value era il totale aziendale invece del valore per azione (falso GREEN su TTD); (2) ROE e ROIC sistematicamente NOT_CALCULABLE per mismatch @JsonProperty su KeyMetricsDto; (3) campo "Dati al" nel FE mostrasse epoch ms grezzo invece di data leggibile.

**Scope:** TSK-143..154 — 12 task, stima ~1.5 settimane.

**Dipendenze:** Sprint 5 Wave 1 completata (TSK-072 done — FMP stable migration). Sprint 5 Wave 2 (deploy infra) parallela e non bloccante per questo hotfix.

**Ordering raccomandato:** TSK-148 (ROE/ROIC fix, XS, sblocca analisi su 2 segnali su 7 per tutti i ticker) → TSK-143 (DCF fix, S, corregge il bug più visibile in produzione) → TSK-151 (FE helper, XS) in parallelo con TSK-144/TSK-145. Poi test suites TSK-149/TSK-150/TSK-146/TSK-147/TSK-152/TSK-153/TSK-154 in cascata.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|-----|--------|
| TSK-143 | BE DcfCalculator: divide per sharesOutstanding; AnalyzeTickerService wiring | be | agent | S | US-052 | todo |
| TSK-144 | BE MarginOfSafetyEvaluator: rationale messaggi per-share | be | agent | XS | US-052 | todo |
| TSK-145 | BE DcfResult: campo sharesUsed (audit) + OpenAPI description | be | agent | S | US-052 | todo |
| TSK-146 | QA DcfCalculatorTest + MarginOfSafetyEvaluatorTest + AnalysisControllerIT (TTD/AAPL/MSFT + edge case shares null) | qa | agent | M | US-052 | todo |
| TSK-147 | QA Contract test OpenAPI drift: dcfIntrinsicValue description per-share | qa | agent | XS | US-052 | todo |
| TSK-148 | BE KeyMetricsDto: @JsonProperty("returnOnEquity") su roe + @JsonProperty("returnOnInvestedCapital") su roic | be | agent | XS | US-053 | todo |
| TSK-149 | QA KeyMetricsDtoTest: deserializzazione fixture /stable con returnOnEquity/returnOnInvestedCapital | qa | agent | S | US-053 | todo |
| TSK-150 | QA Integration test rule engine: ROE_10Y_AVG e ROIC_10Y_AVG non NOT_CALCULABLE su AAPL/MSFT/TTD | qa | agent | S | US-053 | todo |
| TSK-151 | FE Crea lib/format-date.ts con formatSnapshotDate(value: number\|string): string | fe | agent | XS | US-054 | todo |
| TSK-152 | FE Sostituire raw display dataSnapshotAt in analysis/[ticker]/page.tsx con formatSnapshotDate | fe | agent | S | US-054 | todo |
| TSK-153 | QA Vitest unit test formatSnapshotDate (epoch ms, ISO string, edge cases null/NaN) | qa | agent | XS | US-054 | todo |
| TSK-154 | QA Playwright E2E: /analysis/AAPL verifica "Dati al" mostra mese abbreviato, non numero epoch | qa | agent | XS | US-054 | todo |

**Totale Sprint 5.5:** 12 TSK (3 be, 2 fe, 7 qa)

**Dipendenze interne Sprint 5.5:**
```
TSK-143 ──→ TSK-144
TSK-143 ──→ TSK-145
TSK-143 + TSK-144 + TSK-145 ──→ TSK-146 ──→ TSK-147
TSK-148 ──→ TSK-149 ──→ TSK-150
TSK-151 ──→ TSK-152
TSK-151 ──→ TSK-153
TSK-152 + TSK-153 ──→ TSK-154
```

---

## Sprint 6 — Graham Defensive Completeness (EP-010)

**Obiettivo:** Completare i 6 criteri Graham difensivi mancanti (SIZE_LATEST, EARNINGS_STABILITY_10Y, EPS_GROWTH_10Y, PE_3Y_AVG, PB_LATEST, DIVIDEND_CONTINUITY_20Y) nel Rule Engine, aggiornare TrafficLight FE a 13 ruleId, aggiornare contratto OpenAPI.

**Dipendenze:** Sprint 5 Wave 2 completata (TSK-072 done — FMP stable migration).

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-073 | BE Implementa SizeRule (SIZE_LATEST) | be | agent | S | US-032 | ready |
| TSK-074 | QA Test integrazione SizeRule — 3 scenari | qa | agent | S | US-032 | ready |
| TSK-075 | BE Implementa EarningsStabilityRule (EARNINGS_STABILITY_10Y) | be | agent | S | US-033 | ready |
| TSK-076 | QA Test integrazione EarningsStabilityRule — 4 scenari | qa | agent | S | US-033 | ready |
| TSK-077 | BE Implementa EpsGrowthRule (EPS_GROWTH_10Y) | be | agent | S | US-034 | ready |
| TSK-078 | QA Test integrazione EpsGrowthRule — 5 scenari | qa | agent | S | US-034 | ready |
| TSK-079 | BE Implementa Pe3yAvgRule (PE_3Y_AVG) | be | agent | S | US-035 | ready |
| TSK-080 | QA Test integrazione Pe3yAvgRule — 4 scenari | qa | agent | S | US-035 | ready |
| TSK-081 | BE Implementa PbLatestRule (PB_LATEST) | be | agent | S | US-036 | ready |
| TSK-082 | QA Test integrazione PbLatestRule — 4 scenari | qa | agent | S | US-036 | ready |
| TSK-083 | BE Estendi FmpAdapter con getDividendHistory | be | agent | S | US-037 | ready |
| TSK-084 | DB Migration V010__fmp_dividend_history_snapshot | db | agent | XS | US-037 | ready |
| TSK-085 | BE Implementa DividendContinuityRule (DIVIDEND_CONTINUITY_20Y) | be | agent | S | US-037 | ready |
| TSK-086 | QA Test DividendContinuityRule + contratto adapter | qa | agent | S | US-037 | ready |
| TSK-087 | BE Estendi OpenAPI con 6 nuovi ruleId Graham | be | agent | S | cross-EP010 | ready |
| TSK-088 | FE Aggiorna TrafficLight component a 13 ruleId | fe | agent | S | cross-EP010 | ready |
| TSK-089 | QA Contract test OpenAPI drift — 13 ruleId | qa | agent | XS | cross-EP010 | ready |
| TSK-090 | QA Integration test E2E EP-010 — AAPL/MSFT/KO fixture | qa | agent | M | cross-EP010 | ready |

**Totale Sprint 6:** 18 TSK (8 be, 1 fe, 1 db, 8 qa)

---

## Sprint 7 — Deep Analysis backend (EP-011 — BE/DB/Infra)

**Obiettivo:** Infrastruttura RAG completa (SEC EDGAR adapter + filing blob cache + pgvector + EmbeddingService sidecar), pipeline analisi qualitativa (MungerInversion LLM + NewsSentiment + PriceAction), cascade verdetto, endpoint `/api/analysis/{ticker}/deep`.

**Dipendenze:** Sprint 6 completato (i 13 ruleId Buffett+Graham necessari per la cascade US-044).

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-091 | BE SecEdgarAdapter interface + SecEdgarRestClient | be | agent | M | US-038 | ready |
| TSK-092 | BE Cache CIK→ticker TTL 30gg | be | agent | S | US-038 | ready |
| TSK-093 | QA WireMock SecEdgarRestClient — rate-limit, 429, cache | qa | agent | S | US-038 | ready |
| TSK-094 | BE Estendi FmpAdapter con getSecFilings | be | agent | S | US-039 | ready |
| TSK-095 | DB Migration V011__filing_blob | db | agent | XS | US-039 | ready |
| TSK-096 | BE Filing10KQDownloaderService — download, HTML strip, persist | be | agent | M | US-039 | ready |
| TSK-097 | QA Test Filing10KQDownloaderService — cache TTL, limit 50MB | qa | agent | S | US-039 | ready |
| TSK-098 | DB Migration V012__pgvector_enable + filing_chunks + HNSW | db | agent | S | US-040 | ready |
| TSK-099 | Infra Sidecar Python FastAPI embeddings Snowflake Arctic Embed L v2.0 | infra | agent | M | US-040 | ready |
| TSK-100 | BE EmbeddingService Kotlin HTTP client verso sidecar | be | agent | S | US-040 | ready |
| TSK-101 | BE FilingChunkingService — split testo RecursiveCharSplitter | be | agent | S | US-040 | ready |
| TSK-102 | BE FilingRagService — persist chunks + embedding + similarity search | be | agent | M | US-040 | ready |
| TSK-103 | QA Test integrazione pgvector — chunking, embedding, retrieval, idempotenza | qa | agent | M | US-040 | ready |
| TSK-104 | BE AnthropicClient config + LlmResilienceConfig circuit breaker | be | agent | S | US-041 | ready |
| TSK-105 | BE MungerInversionAnalyzer — 10 query inversione + prompt template | be | agent | L | US-041 | ready |
| TSK-106 | DB Migration V013__filing_analysis (deep_analysis_report) | db | agent | XS | US-041 | ready |
| TSK-107 | QA Test MungerInversionAnalyzer — mock Anthropic + golden response | qa | agent | S | US-041 | ready |
| TSK-108 | BE Estendi FmpAdapter con getStockNews | be | agent | S | US-042 | ready |
| TSK-109 | BE NewsSentimentService — classificatore Claude Opus + cache | be | agent | M | US-042 | ready |
| TSK-110 | DB Migration V014__news_sentiment_analysis | db | agent | XS | US-042 | ready |
| TSK-111 | QA Test NewsSentimentService — golden dataset, cache, limite 50 LLM | qa | agent | S | US-042 | ready |
| TSK-112 | BE Estendi FmpAdapter con getHistoricalEod | be | agent | S | US-043 | ready |
| TSK-113 | BE PriceActionAnalyzer — drawdown 52w + panic/deterioration flags | be | agent | S | US-043 | ready |
| TSK-114 | QA Test PriceActionAnalyzer — boundary flags + migration V015 | qa | agent | S | US-043 | ready |
| TSK-115 | BE MungerDecisionService — cascade 6 verdetti | be | agent | M | US-044 | ready |
| TSK-116 | BE PositionSizeCalculator — port da agent.py | be | agent | S | US-044 | ready |
| TSK-117 | QA Test MungerDecisionService — 6 combinazioni cascade + determinismo | qa | agent | S | US-044 | ready |
| TSK-118 | BE DeepAnalysisController + DeepAnalysisService orchestrator | be | agent | M | US-045 | ready |
| TSK-119 | BE DTO DeepAnalysisResultDto + OpenAPI schema /deep + migration V016 | be | agent | S | US-045 | ready |
| TSK-120 | QA Integration test E2E /deep — tutti i mock provider | qa | agent | M | US-045 | ready |
| TSK-121 | QA Contract test OpenAPI /deep — drift guard | qa | agent | S | US-045 | ready |

**Totale Sprint 7:** 31 TSK (15 be, 0 fe, 5 db, 1 infra, 10 qa)

---

## Sprint 8 — Deep Analysis frontend (EP-011 — FE)

**Obiettivo:** Tab "Deep Analysis" sul frontend con tutti i componenti UI (verdict badge, Munger report collapsibile, news sentiment, drawdown chart, filing links), SWR hook, test E2E Playwright.

**Dipendenze:** Sprint 7 completato (endpoint `/api/analysis/{ticker}/deep` disponibile).

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-122 | FE Route /analysis/{ticker}/deep + page component Next.js | fe | agent | S | US-046 | ready |
| TSK-123 | FE Componenti UI Deep Analysis (5 componenti) | fe | agent | M | US-046 | ready |
| TSK-124 | FE API client estensione + SWR hook useDeepAnalysis | fe | agent | S | US-046 | ready |
| TSK-125 | QA Test E2E Playwright Deep Analysis — happy path + value-trap + invalido | qa | agent | M | US-046 | ready |

**Totale Sprint 8:** 4 TSK (3 fe, 1 qa)

---

## Sprint 9 — Top Value Picks batch (EP-012)

**Obiettivo:** UniverseScreenerService (FMP screener + 13-F + news scout), job notturno cron 02:00 UTC, persistenza top_value_picks, endpoint GET /api/top-picks paginato, pagina FE /top-picks con filtri e deep-link.

**Dipendenze:** Sprint 7 completato (MungerDecisionService, AnthropicClient, SecEdgarAdapter disponibili). Sprint 8 completato per il link alla pagina /deep.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-126 | BE UniverseScreenerService — orchestratore FMP NASDAQ+NYSE | be | agent | M | US-047 | ready |
| TSK-127 | BE InstitutionalHoldingsService — overlay 13-F SEC EDGAR | be | agent | M | US-047 | ready |
| TSK-128 | BE NewsScoutService — Claude Opus scout top-200 | be | agent | S | US-047 | ready |
| TSK-129 | BE SectorBlacklist + SETTORI_BUFFETT_OK + FmpAdapter.companyScreener | be | agent | S | US-047 | ready |
| TSK-130 | QA Test UniverseScreenerService — mock FMP + SEC, dedup, cap 500 | qa | agent | S | US-047 | ready |
| TSK-131 | BE TopValuePicksJob @Scheduled cron 02:00 UTC + pipeline batch | be | agent | M | US-048 | ready |
| TSK-132 | BE BatchResilienceConfig — RateLimiter fmp-batch separato | be | agent | S | US-048 | ready |
| TSK-133 | Infra Migration V017__top_picks_run_log + scheduler enable yaml | infra | agent | XS | US-048 | ready |
| TSK-134 | QA Test rate-limit batch + idempotenza job | qa | agent | S | US-048 | ready |
| TSK-135 | DB Migration V016__top_value_picks — PK composta + indice | db | agent | XS | US-049 | ready |
| TSK-136 | BE TopValuePickEntity + TopValuePickRepository JPA | be | agent | S | US-049 | ready |
| TSK-137 | QA Test persistenza top_value_picks — PK, retention, indice | qa | agent | S | US-049 | ready |
| TSK-138 | BE TopPicksController GET /api/top-picks paginazione + filtri | be | agent | M | US-050 | ready |
| TSK-139 | QA OpenAPI schema /top-picks + contract test + integration test | qa | agent | S | US-050 | ready |
| TSK-140 | FE Route /top-picks page + tabella ordinabile Next.js | fe | agent | M | US-051 | ready |
| TSK-141 | FE Filtri sidebar + useTopPicks SWR hook | fe | agent | S | US-051 | ready |
| TSK-142 | QA Test E2E Playwright /top-picks — filtro, datepicker, navigazione /deep | qa | agent | S | US-051 | ready |

**Totale Sprint 9:** 17 TSK (7 be, 2 fe, 1 db, 1 infra, 6 qa)

---

## Riepilogo TSK per layer

| Release | Sprint | infra | db | be | fe | qa | Totale |
|---------|--------|-------|-----|-----|-----|-----|--------|
| R1.0 | 1–4 done | 2 | 7 | 23 | 13 | 7 | **49** |
| R1.1 | 5 in corso | 5 | 0 | 5 | 3 | 8 | **21** (14 done, 7 todo) |
| R1.1 | lookahead | 0 | 0 | 2 | 0 | 0 | **2** (TSK-071 todo, TSK-072 done) |
| R1.1.x | 5.5 hotfix | 0 | 0 | 3 | 2 | 7 | **12** (TSK-143…154) |
| R2.0 | 6 | 0 | 1 | 8 | 1 | 8 | **18** |
| R2.0 | 7 | 1 | 5 | 15 | 0 | 10 | **31** |
| R2.0 | 8 | 0 | 0 | 0 | 3 | 1 | **4** |
| R2.0 | 9 | 1 | 1 | 7 | 2 | 6 | **17** |
| | **Nuovi R2.0** | | | | | | **70** (TSK-073…142) |
| | **TOTALE** | | | | | | **154** |

---

## Dipendenze critiche Sprint 6–9

```
Sprint 6 (EP-010, parallelo per pair be+qa)
  TSK-073 ──→ TSK-074
  TSK-075 ──→ TSK-076
  TSK-077 ──→ TSK-078
  TSK-079 ──→ TSK-080
  TSK-081 ──→ TSK-082
  TSK-083 + TSK-084 ──→ TSK-085 ──→ TSK-086
  TSK-073..086 ──→ TSK-087 ──→ TSK-088 ──→ TSK-089 ──→ TSK-090

Sprint 7 (EP-011 BE, catena sequenziale per blocchi)
  TSK-091 ──→ TSK-092 ──→ TSK-093
  TSK-091 + TSK-094 + TSK-095 ──→ TSK-096 ──→ TSK-097
  TSK-095 ──→ TSK-098
  TSK-099 ──→ TSK-100
  TSK-098 + TSK-100 + TSK-101 ──→ TSK-102 ──→ TSK-103
  TSK-104 + TSK-102 ──→ TSK-105 + TSK-106 ──→ TSK-107
  TSK-108 + TSK-110 + TSK-104 ──→ TSK-109 ──→ TSK-111
  TSK-112 + TSK-113 ──→ TSK-114
  TSK-105 + TSK-109 + TSK-113 ──→ TSK-115 + TSK-116 ──→ TSK-117
  TSK-115 + TSK-119 ──→ TSK-118 ──→ TSK-120 + TSK-121

Sprint 8 (EP-011 FE)
  TSK-119 + TSK-118 ──→ TSK-122 + TSK-124 ──→ TSK-123 ──→ TSK-125

Sprint 9 (EP-012)
  TSK-091 ──→ TSK-127
  TSK-104 + TSK-108 ──→ TSK-128
  TSK-126 + TSK-127 + TSK-128 + TSK-129 ──→ TSK-130
  TSK-132 + TSK-133 + TSK-135 + TSK-136 ──→ TSK-131 ──→ TSK-134
  TSK-135 + TSK-136 ──→ TSK-137
  TSK-135 + TSK-136 ──→ TSK-138 ──→ TSK-139
  TSK-124 + TSK-138 ──→ TSK-140 + TSK-141 ──→ TSK-142
```

**Prossimo `/dev` suggerito:** `TSK-073` (SizeRule BE) in parallelo con `TSK-074` (QA). Completare Sprint 5 Wave 2 (TSK-061..067) in parallelo se possibile.
