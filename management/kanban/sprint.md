<!-- generated, do not edit — rigenerato da tpm ad ogni run -->
---
id: sprint
title: Sprint Plan — R1.0 MVP
generated: 2026-05-20
tpm: tpm
release: R1.0
---
# Sprint Plan — R1.0 MVP

> Copertura: **EP-001 + EP-002 + EP-003 + EP-004 + EP-005 + EP-006** (17 US, 38 TSK).
> R1.1 (EP-005 partial + EP-006) incluso da Sprint 3 per completare il MVP.
> Generato da TPM secondo PATTERN.md §3 + §13.

---

## Sprint 1 — Fondamenta (infra, DB schema completo, FMP adapter + cache)

**Obiettivo:** Avere un backend avviabile con DB migrato, FMP adapter funzionante con
cache 24h e resilienza, più il bootstrap frontend.

**Capacity indicativa:** 24h di lavoro agente.

| TSK | Titolo | Layer | Consumer | Estimate | Depends on |
|-----|--------|-------|----------|----------|------------|
| TSK-031 | Setup Spring Boot + Gradle + Docker Compose | infra | agent | 3h | — |
| TSK-030 | Setup Next.js + Tailwind + Zustand + API client | fe | agent | 3h | — |
| TSK-001 | DB V001 (users/auth) + V002 (stocks) | db | agent | 2h | — |
| TSK-008 | DB V003 (fmp_cache) + V004 (rule_engine_result) | db | agent | 2h | TSK-001 |
| TSK-009 | FmpAdapter: income/balance/cashflow/key-metrics | be | agent | 5h | TSK-008 |
| TSK-010 | FmpCacheService: cache-aside TTL 24h | be | agent | 4h | TSK-008, TSK-009 |
| TSK-011 | FmpResilienceConfig + FmpEventLogger | be | agent | 3h | TSK-010 |
| TSK-032 | Dockerfile multi-stage + CI pipeline | infra | agent | 3h | TSK-031, TSK-030 |

**Milestone Sprint 1:** `GET /api/financials/AAPL` → dati strutturati; seconda call entro
TTL → cache hit; FMP down + cache → stale marcato; `/actuator/health` UP.

---

## Sprint 2 — Rule Engine + endpoint analisi (EP-003 + EP-004)

**Obiettivo:** Pipeline analisi completa (`GET /api/analysis/{ticker}`) con tutte e 7 le
regole, Graham Number, DCF Owner Earnings, Margin of Safety.

**Capacity indicativa:** 30h di lavoro agente.

| TSK | Titolo | Layer | Consumer | Estimate | Depends on |
|-----|--------|-------|----------|----------|------------|
| TSK-005 | ScreenerController GET /api/screener | be | agent | 3h | TSK-010, TSK-001 |
| TSK-012 | RuleEngineService scaffold + RoeRule + RoicRule | be | agent | 4h | TSK-009, TSK-010 |
| TSK-013 | GrossMarginRule + NetMarginRule | be | agent | 3h | TSK-012 |
| TSK-014 | CurrentRatioRule + DebtToIncomeRule | be | agent | 3h | TSK-012 |
| TSK-015 | CapexIntensityRule (completa 7 strategie) | be | agent | 2h | TSK-012 |
| TSK-016 | GrahamNumberCalculator | be | agent | 2h | TSK-012 |
| TSK-017 | DB V007 (dcf_method_override) | db | agent | 1h | TSK-001 |
| TSK-018 | DcfCalculator + GreenwaldCapexEstimator + FcfFallback | be | agent | 6h | TSK-012, TSK-017 |
| TSK-019 | MarginOfSafetyEvaluator + AnalysisController | be | agent | 4h | TSK-015, TSK-016, TSK-018 |
| TSK-020 | QA: integration test pipeline analisi E2E | qa | agent | 3h | TSK-019 |
| TSK-037 | QA: contract test OpenAPI drift | qa | agent | 2h | TSK-019, TSK-031 |

**Milestone Sprint 2:** `GET /api/analysis/AAPL` → `signals[7]`, `grahamNumber`,
`dcfIntrinsicValue`, `mosSignal`; contract test green; pipeline deterministica con fixture.

---

## Sprint 3 — Frontend MVP + auth + watchlist + dashboard completa (EP-001→EP-006)

**Obiettivo:** SPA navigabile end-to-end: ricerca → analisi Traffic Light → grafici →
moat checklist → watchlist + auth.

**Capacity indicativa:** 48h di lavoro agente.

| TSK | Titolo | Layer | Consumer | Estimate | Depends on |
|-----|--------|-------|----------|----------|------------|
| TSK-002 | BE SearchController + SearchService + FmpAdapter (search/profile) | be | agent | 4h | TSK-001, TSK-010 |
| TSK-003 | FE SearchBar + landing page | fe | agent | 3h | TSK-002, TSK-030 |
| TSK-004 | QA integration test SearchController | qa | agent | 2h | TSK-002 |
| TSK-006 | FE ScreenerForm + page /screener | fe | agent | 4h | TSK-005, TSK-030 |
| TSK-007 | FE ResultsList + Ag-Grid | fe | agent | 3h | TSK-003, TSK-006, TSK-030 |
| TSK-021 | FE TrafficLightPanel + RuleSignalCard + ValuationSummary | fe | agent | 5h | TSK-019, TSK-030 |
| TSK-038 | FE StaleDataBadge | fe | agent | 1h | TSK-030, TSK-021 |
| TSK-022 | QA E2E Playwright: ricerca → Traffic Light | qa | agent | 3h | TSK-021, TSK-003 |
| TSK-023 | BE HistoricalController GET /api/historical/{ticker} | be | agent | 3h | TSK-010 |
| TSK-024 | FE HistoricalChart Recharts | fe | agent | 3h | TSK-023, TSK-030 |
| TSK-025 | DB V006 (moat_checklist_entry) | db | agent | 1h | TSK-001 |
| TSK-028 | DB V005 (watchlists) + V008 (fmp_event_log) | db | agent | 1h | TSK-001 |
| TSK-033 | BE AuthController + SecurityConfig + JwtService | be | agent | 5h | TSK-001, TSK-031 |
| TSK-026 | BE MoatChecklistController | be | agent | 3h | TSK-025, TSK-034 |
| TSK-027 | FE MoatChecklist component | fe | agent | 3h | TSK-026, TSK-030, TSK-035 |
| TSK-034 | FE LoginPage + RegisterPage + useAuthStore | fe | agent | 4h | TSK-033, TSK-030 |
| TSK-029 | BE WatchlistController | be | agent | 4h | TSK-028, TSK-034 |
| TSK-035 | FE WatchlistPage + WatchlistTable + AddToWatchlistButton | fe | agent | 3h | TSK-029, TSK-034, TSK-030 |
| TSK-036 | QA E2E Playwright: auth + watchlist | qa | agent | 2h | TSK-035, TSK-029, TSK-033 |

**Milestone Sprint 3:** SPA navigabile completa; E2E green su flusso principale e watchlist;
tutte le migration V001-V008 applicate.

---

## Riepilogo TSK per layer (R1.0 completo)

| Layer | Numero TSK | Estimate totale |
|-------|------------|-----------------|
| infra | 2 | 6h |
| db | 6 | 8h |
| be | 17 | 62h |
| fe | 11 | 37h |
| qa | 5 | 12h |
| **Totale** | **38** (+ 0 TSK R1.1 in questo run) | **~102h agente** |

---

## Dipendenze critiche (catena principale)

```
TSK-031 (infra) ──→ TSK-001 (db) ──→ TSK-008 (db) ──→ TSK-009 (be)
                                                              │
                                                        TSK-010 (be)
                                                         ├──→ TSK-011 (be)
                                                         ├──→ TSK-012 (be)──→ TSK-013/014/015/016
                                                         │          └──────→ TSK-018──→ TSK-019
                                                         └──→ TSK-010         └──→ TSK-020 (qa)
TSK-019 ──→ TSK-021 (fe) ──→ TSK-022 (qa)
TSK-001 ──→ TSK-033 (be) ──→ TSK-034 (fe) ──→ TSK-035 (fe) ──→ TSK-036 (qa)
TSK-030 (fe) ← tutti i TSK fe
```

---

## Note R1.1

EP-005 (grafici + moat) e EP-006 (watchlist + auth) sono inclusi nel **Sprint 3 R1.0**
perché EP-006 (auth) è prerequisito per US-016 e US-017.
Nessun TSK R1.1 rimanente da generare: tutte e 17 le US sono coperte.
