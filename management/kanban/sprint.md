<!-- generated, do not edit — rigenerato da tpm ad ogni run -->
---
id: sprint
title: Sprint Plan — R1.0 MVP
generated: 2026-05-22
tpm: tpm
release: R1.0
---
# Sprint Plan — R1.0 MVP

> Copertura: **EP-001 + EP-002 + EP-003 + EP-004 + EP-005 + EP-006** (21 US, 50 TSK).
> Sprint 1–3 completati. Sprint 4 copre i delta ADR-010 (US-018/019) e ADR-011 (US-020).
> Sprint 5 copre la manutenzione tecnica US-021 (migrazione FMP v3 → /stable, ADR-004 §7).
> Generato da TPM secondo PATTERN.md §3 + §13. ADR-010 e ADR-011 accettati 2026-05-22.

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

**Capacity indicativa:** 18h di lavoro agente.

| TSK | Titolo | Layer | Consumer | Estimate | Depends on | Status |
|-----|--------|-------|----------|----------|------------|--------|
| TSK-040 | DB V009: add first_issued_at to refresh_tokens | db | agent | XS | TSK-033 | todo |
| TSK-039 | BE GlobalExceptionHandler: 409 email-already-registered | be | agent | S | TSK-033 | todo |
| TSK-041 | BE AuthService: sliding refresh TTL 7d + cap 30d | be | agent | M | TSK-033, TSK-040 | todo |
| TSK-044 | BE DcfOverrideController: GET /api/dcf-overrides/{ticker} | be | agent | S | TSK-018, TSK-033 | todo |
| TSK-045 | BE DcfFeasibilityCheck + DcfMethodUnfeasibleException + 422 | be | agent | M | TSK-018 | todo |
| TSK-046 | BE AnalyzeTickerService: auth-aware + dcfMethodSource + Vary | be | agent | M | TSK-018, TSK-033, TSK-044 | todo |
| TSK-042 | QA Contract-test: generic-error login + 409 register OpenAPI | qa | agent | S | TSK-039, TSK-041 | todo |
| TSK-047 | QA Contract-test: 4 path US-020 (USER_OVERRIDE, DEFAULT_POLICY x2, Vary, 422) | qa | agent | S | TSK-044, TSK-045, TSK-046 | todo |
| TSK-049 | BE OpenAPI: GET dcf-overrides, 422, dcfMethodSource, Vary | be | agent | XS | TSK-044, TSK-045, TSK-046 | todo |
| TSK-043 | FE useAuthStore: gestione 401 + banner sessione scaduta | fe | agent | S | TSK-034, TSK-041 | todo |
| TSK-048 | FE DcfOverridePanel: badge source + form override + 422 inline | fe | agent | M | TSK-044, TSK-046, TSK-034 | todo |

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

## Sprint 5 — Manutenzione FMP: migrazione adapter v3 → /stable (EP-002 US-021)

**Obiettivo:** Ripristinare la piena operatività delle chiamate FMP migrando
`FmpAdapterRestClient` (+ DTO + fixture + test) dagli endpoint v3 dismessi
(EOL 2025-08-31) alla nuova API `/stable`. Nessuna modifica all'interfaccia pubblica
`FmpAdapter` né alla pipeline cache/resilienza.

**Capacity indicativa:** 8h di lavoro agente.

| TSK | Titolo | Layer | Consumer | Estimate | Depends on | Status |
|-----|--------|-------|----------|----------|------------|--------|
| TSK-050 | BE — Migrazione FmpAdapterRestClient + DTO + fixture da v3 a /stable | be | agent | L | TSK-009 | todo |

**Milestone Sprint 5:**
- `GET /api/search?query=TTD` → risultato non vuoto con "The Trade Desk Inc.".
- `GET /api/screener?marketCap=LARGE,MEGA` → pagina risultati senza 503.
- `GET /api/analysis/AAPL` → dati financials reali senza 403/503.
- `./gradlew test` verde completo.
- Container `vi-app` healthy dopo rebuild; smoke test end-to-end ok.
- Gap `fmp-stable-adapter-migration` marcabile come risolto da wiki-keeper.

---

## Lookahead Sprint 6 (candidati, non generati)

Dopo TSK-050 nessun altro TSK in backlog aperto per R1.0. Se emergono US di R1.1
(EP-005 partial, compliance, SSO) → avviare `/dev` su ADR futuro. Gap non bloccanti
residui: `fmp-stable-rate-limiting`, `fmp-stable-error-codes`, `fmp-stable-analyst-estimates`.

---

## Riepilogo TSK per layer (R1.0 completo, Sprint 1–5)

| Layer | TSK Sprint 1–3 (done) | TSK Sprint 4 (todo) | TSK Sprint 5 (todo) | Totale |
|-------|-----------------------|---------------------|---------------------|--------|
| infra | 2 | 0 | 0 | 2 |
| db | 6 | 1 (TSK-040) | 0 | 7 |
| be | 17 | 6 (TSK-039/041/044/045/046/049) | 1 (TSK-050) | 24 |
| fe | 11 | 2 (TSK-043/048) | 0 | 13 |
| qa | 5 | 2 (TSK-042/047) | 0 | 7 |
| **Totale** | **38** | **11** | **1** | **50** |

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

## Dipendenze critiche Sprint 5

```
TSK-009 (done) ──→ TSK-050 (be) [standalone, nessun blocco da Sprint 4]
```
