<!-- generated, do not edit — rigenerato da tpm ad ogni run -->
---
id: sprint
title: Sprint Plan — R1.0 MVP
generated: 2026-05-22
tpm: tpm
release: R1.0
closed: 2026-05-22
---
# Sprint Plan — R1.0 MVP

> Copertura: **EP-001 + EP-002 + EP-003 + EP-004 + EP-005 + EP-006** (20 US, 49 TSK).
> **R1.0 MVP chiuso:** Sprint 1–4 completati (49/49 TSK `done`, 20/20 US `done`, 6/6 EP `done`).
> Sprint 4 ha colmato i delta ADR-010 (US-018/019) e ADR-011 (US-020). Merge PR #2 + PR #3 su `master`.
> Generato da TPM secondo PATTERN.md §3 + §13. Ultima rigenerazione: chiusura amministrativa 2026-05-22.

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

**Milestone Sprint 4:**
- `POST /api/auth/register` email duplicata → `409 application/problem+json`.
- `POST /api/auth/login` email inesistente e password errata → risposta `401` con `detail` identico (contract-test verde).
- `POST /api/auth/refresh` → sliding 7d; cap 30d da `first_issued_at` → `401`.
- `GET /api/dcf-overrides/{ticker}` → `200` con override o `404`.
- `POST /api/dcf-overrides` con metodo non applicabile → `422` con `extensions.reason`.
- `GET /api/analysis/{ticker}` → `dcfMethodSource` nel payload; header `Vary: Authorization`.
- FE: badge "Default policy" / "Tuo override" visibile; banner "Sessione scaduta" su 401 non recuperabile.
- OpenAPI spec allineata; contract-test green in CI.

---

## Lookahead Sprint 5 / R1.1 (candidati, non generati)

Nessun TSK in backlog per R1.0. Debito tecnico documentato in `wiki/gaps.md` (non bloccante):
`be-problemdetail-flatten`, `fe-swr-peer-r19`, `fe-static-export-tickers`. Per R1.1:
compliance, SSO (gap `arch-auth-provider-choice`), deploy target (`arch-deployment-target`).

---

## Riepilogo TSK per layer (R1.0 completo, Sprint 1–4)

| Layer | Sprint 1–3 | Sprint 4 | Totale |
|-------|------------|----------|--------|
| infra | 2 | 0 | 2 |
| db | 6 | 1 | 7 |
| be | 17 | 6 | 23 |
| fe | 11 | 2 | 13 |
| qa | 5 | 2 | 7 |
| **Totale** | **38** | **11** | **49** |

> Tutti i 49 TSK in stato `done`. US-018/019/020 chiuse con Sprint 4.

---

## Dipendenze critiche Sprint 4

```
TSK-033 (done) ──→ TSK-040 (db) ──→ TSK-041 (be) ──→ TSK-042 (qa)
TSK-033 (done) ──→ TSK-039 (be) ──→ TSK-042 (qa)
TSK-018 (done) ──→ TSK-044 (be) ──→ TSK-046 (be) ──→ TSK-047 (qa)
TSK-018 (done) ──→ TSK-045 (be) ──→ TSK-047 (qa)
                                  └──→ TSK-049 (be)
TSK-044 + TSK-046 + TSK-034 (done) ──→ TSK-048 (fe)
TSK-034 (done) + TSK-041 ──→ TSK-043 (fe)
```
