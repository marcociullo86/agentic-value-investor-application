<!-- generated, do not edit — rigenerato da tpm ad ogni run -->
---
id: sprint
title: Sprint Plan — R1.0 MVP + R1.1
generated: 2026-05-22
tpm: tpm
release: R1.1
r10_closed: 2026-05-22
---
# Sprint Plan

> **R1.0 MVP chiuso:** Sprint 1–4 completati (49/49 TSK `done`, 20/20 US, 6/6 EP).
> **R1.1 attivo:** Sprint 5 — 22 TSK nuovi (TSK-050…071); Sprint 6 lookahead — 1 TSK (TSK-071).
> Epiche: EP-007, EP-008, EP-009 — US-021…030 `ready`. L4: ADR-012…016 `accepted`.
> Ordine suggerito PM: **EP-007 ∥ EP-009 US-029 (TSK-068 human)** → **EP-008** → **EP-009 US-030**.

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

**Nota US-025:** L4 già allineato (appendici ADR-001/002/003). TSK-060 = verifica formale, **nessun dev L5** salvo drift.

**Nota US-029:** TSK-068 `consumer: human` — wiki-keeper; `pending_clarification` su tre gap FMP. Non blocca TSK-069 (default 30/min ADR-016).

---

## Lookahead Sprint 6 — R1.1 post-wiki FMP

| TSK | Titolo | Layer | Consumer | Est. | Depends on | Status |
|-----|--------|-------|----------|------|------------|--------|
| TSK-071 | BE Ricalibra rate limit da wiki post US-029 | be | agent | XS | TSK-068, TSK-069 | todo |
| TSK-072 | BE Migrazione FmpAdapterRestClient + DTO + fixture da v3 a /stable (ex sprint5 TSK-050 rinumerato) | be | agent | L | TSK-009, TSK-068 | done |

Eseguire TSK-071 solo dopo chiusura gap `fmp-rate-limiting` in wiki con citazione raw.
TSK-072 (US-031) introdotto retroattivamente: codice gia' implementato nel branch sprint5/tsk-050-fmp-stable-migration (ora rinumerato US-031/TSK-072 per evitare collisione con il TSK-050 di master).

---

## Riepilogo TSK per layer

| Release | Sprint | infra | db | be | fe | qa | Totale |
|---------|--------|-------|-----|-----|-----|-----|--------|
| R1.0 | 1–4 done | 2 | 7 | 23 | 13 | 7 | **49** |
| R1.1 | 5 in corso | 5 | 0 | 5 | 3 | 8 | **21** (14 done, 7 todo) |
| R1.1 | 6 lookahead | 0 | 0 | 1 | 0 | 0 | **1** |
| | **Nuovi R1.1** | | | | | | **22** (TSK-050…071) |

---

## Dipendenze critiche Sprint 5

```
Wave 1 (parallelo)
  TSK-050 ──→ TSK-051, TSK-052
  TSK-053 ──→ TSK-054 ──→ TSK-061 ──→ TSK-062, TSK-063 ──→ TSK-065
  TSK-055 ──→ TSK-056 ──→ TSK-057
  TSK-058 ──→ TSK-059
  TSK-069 ──→ TSK-070
  TSK-068 (human, ∥) ──→ TSK-071 (Sprint 6)
  TSK-060 (doc verify, ∥)

Cutover
  TSK-050 + TSK-057 + TSK-061 ──→ TSK-066 ──→ TSK-067
  TSK-064 + TSK-065 ──→ TSK-067
```

**Prossimo `/dev` suggerito:** `TSK-061` (docker-compose.prod + nginx TLS). In parallelo human: `TSK-068` (wiki-keeper ingest FMP). Cutover: TSK-066 dopo deploy staging.
