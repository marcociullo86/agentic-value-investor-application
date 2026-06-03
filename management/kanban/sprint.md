<!-- generated, do not edit — rigenerato da tpm ad ogni run -->
---
id: sprint
title: Sprint Plan — R1.0 MVP + R1.1 + R1.1.x + R2.0 + R2.1 + R3.0 + CQRL Bonifica + R3.2 + R3.3
generated: 2026-06-03
tpm: tpm
release: R3.0 EP-018 Sprint 15 (chiuso) + EP-017 chiusa (Sprint 14+17) + R3.1 EP-019 chiusa (Sprint 16) + R3.2 Sprint 18 COMPLETATO (EP-002 US-031 + EP-010) + R3.3 Sprint 19 COMPLETATO (EP-020)
r10_closed: 2026-05-22
r11_closed: 2026-05-23
r11x_closed: 2026-05-26
r20_closed: 2026-05-26
r21_closed: 2026-05-26
r30_closed: 2026-05-29
r31_cqrl_closed: 2026-05-29
r32_closed: 2026-06-03
r33_closed: 2026-06-03
---
# Sprint Plan

> **R1.0 MVP chiuso:** Sprint 1–4 completati (49/49 TSK `done`, 20/20 US, 6/6 EP).
> **R1.1 chiuso:** Sprint 5 — 23/23 TSK `done`.
> **R1.1.x hotfix chiuso:** Sprint 5.5 — 12/12 TSK `done`. US-052, US-053, US-054 completate.
> **R2.0 chiuso:** Sprint 6–9 completati — 79/79 TSK `done`, 21/21 US, 3/3 EP (EP-010, EP-011, EP-012).
> **R2.1 chiuso:** Sprint 10 — EP-013 Mr. Market Context Flags — 6/6 TSK `done`, 2/2 US (US-056, US-057).
> **R3.0 chiuso:** Sprint 15 (EP-018) — **22/22 TSK `done`**. Sprint 15.5 chiuso (TSK-239). EP-014..016 `done`. EP-018 `done`.
> **R3.0 chiuso:** Sprint 14+17 (EP-017) — **18/18 TSK `done`**, 7/7 US (US-073..078 + US-087). EP-017 `done`; ADR-026 `accepted`.
> **R3.1 chiuso:** Sprint 16 (EP-019 CQRL) — **25/25 TSK `done`**. US-084/085/086 `done`; EP-019 `done`.
> **R3.2 chiuso:** Sprint 18 — EP-002 US-031 (migrazione FMP /stable) + EP-010 US-032..037 (6 criteri Graham) — **21/21 TSK `done`**, review CQRL `passed` su tutti i TSK con copertura CQRL. EP-002 US-031 `done`; EP-010 `done`.
> **R3.3 chiuso:** Sprint 19 — EP-020 Trasparenza analisi LLM (US-088..091) — **10/10 TSK `done`**, review CQRL `passed` su tutti i TSK. EP-020 `done`.

---

## Sprint 19 — Trasparenza analisi LLM nel verdetto Deep Analysis (EP-020) — COMPLETATO

**Obiettivo:**
- **EP-020 US-088:** Log gated delle interazioni LLM (prompt+risposta, 11 call Munger + sintesi news) con flag configurabile e troncamento difensivo.
- **EP-020 US-089:** Campo `sintesi` narrativo nel report Munger — schema LLM, persistenza in `report_json`, contratto API (openapi+DTO+tipi FE), paragrafo sintesi nel blocco Verdetto.
- **EP-020 US-090:** `MungerReportCollapsible` con sintesi narrativa in testa; Top Rischi/Punti di Forza/Segnali 10-Q per esteso.
- **EP-020 US-091:** `NewsSentimentBlock.items[]` con headline+textExcerpt+classe+motivazione — migration V030 `text_excerpt`, persistenza, contratto, UI lista notizie analizzate.

**Stato:** COMPLETATO — **10/10 TSK `done`**. US-088/089/090/091 `done`. EP-020 `done`. CQRL review `passed` su tutti i TSK (TSK-299..308).

**Wave 1 — fondamenta backend/contratto (parallela):**

| TSK | Titolo | Layer | Consumer | Est. | US | `depends_on` | Status |
|-----|--------|-------|----------|------|----|--------------|--------|
| TSK-299 | BE Logging gated interazioni LLM (prompt+risposta) | be | agent | M | US-088 | — | done |
| TSK-300 | BE Campo sintesi narrativa report Munger — schema, persistenza, contratto | be | agent | M | US-089 | — | done |
| TSK-305 | DB Migration V030 text_excerpt su news_classification | db | agent | S | US-091 | — | done |
| TSK-306 | BE NewsSentimentBlock.items — snippet + esposizione lista news | be | agent | M | US-091 | TSK-305 | done |

**Wave 2 — frontend (parallela, dopo contratto):**

| TSK | Titolo | Layer | Consumer | Est. | US | `depends_on` | Status |
|-----|--------|-------|----------|------|----|--------------|--------|
| TSK-301 | FE Paragrafo sintesi narrativa nel blocco Verdetto | fe | agent | S | US-089 | TSK-300 | done |
| TSK-303 | FE MungerReportCollapsible con sintesi in testa | fe | agent | S | US-090 | TSK-300 | done |
| TSK-307 | FE Lista notizie analizzate (titolo+testo) nel blocco Sentiment News | fe | agent | M | US-091 | TSK-306 | done |

**Wave 3 — QA/test/E2E (parallela):**

| TSK | Titolo | Layer | Consumer | Est. | US | `depends_on` | Status |
|-----|--------|-------|----------|------|----|--------------|--------|
| TSK-302 | QA Test contratto + service per il campo sintesi Munger | qa | agent | S | US-089 | TSK-300 | done |
| TSK-308 | QA Test contratto + service per NewsSentimentBlock.items | qa | agent | S | US-091 | TSK-305, TSK-306 | done |
| TSK-304 | QA E2E — pagina dettaglio mostra sintesi Munger + news analizzate | qa | agent | S | US-090 | TSK-301, TSK-303, TSK-307 | done |

**Totale Sprint 19:** 10 TSK (3 be, 3 fe, 1 db, 3 qa) — **10/10 `done`**

---

## Sprint 18 — FMP /stable migration + Graham Defensive completeness (EP-002 US-031, EP-010) — COMPLETATO

**Obiettivo:**
- **EP-002 US-031:** Completare la migrazione di `FmpAdapterRestClient` dagli endpoint v3 deprecati ai nuovi `/stable`, aggiornando routing, DTO, fixture WireMock e smoke test container.
- **EP-010 US-032..037:** Implementare i 6 criteri difensivi di Graham nel Rule Engine (SIZE_LATEST, EARNINGS_STABILITY_10Y, EPS_GROWTH_10Y, PE_3Y_AVG, PB_LATEST, DIVIDEND_CONTINUITY_20Y), portando il TrafficLight a 13 ruleId totali (7 Buffett + 6 Graham).

**Stato:** COMPLETATO — **21/21 TSK `done`**. US-031 `done`. US-032..037 `done`. EP-002 US-031 `done`. EP-010 `done`. CQRL review `passed` su tutti i TSK con copertura (TSK-273..292; TSK-272/TSK-286 DB/BE senza obbligo CQRL esplicito).

**Rationale assegnazione:** EP-002 US-031 e EP-010 US-032..037 assegnati allo stesso Sprint 18 perché (a) US-031 è prerequisito logico di tutti i rule Graham (i rule leggono da FmpAdapter, che deve puntare a `/stable`), (b) i 6 Graham rule hanno dipendenze interne sequenziali per US-037 e parallelizzabili per US-032..036, (c) entrambi gli epic sono BE-heavy. EP-002 TSK priorità P0 (bloccanti per ACs di produzione); EP-010 TSK priorità P1.

### EP-002 US-031 — Migrazione FMP /stable

| TSK | Titolo | Layer | Consumer | Est. | US | `depends_on` | Status |
|-----|--------|-------|----------|------|----|--------------|--------|
| TSK-272 | BE Migra FmpAdapterRestClient: endpoint routing + DTO /stable | be | agent | L | US-031 | — | done |
| TSK-273 | QA Aggiorna WireMock stub e integration test FMP /stable | qa | agent | M | US-031 | TSK-272 | done |
| TSK-274 | QA Smoke test container: rebuild image + spot-check /stable | qa | agent | S | US-031 | TSK-272, TSK-273 | done |

### EP-010 — Regole Graham (rule per rule)

| TSK | Titolo | Layer | Consumer | Est. | US | `depends_on` | Status |
|-----|--------|-------|----------|------|----|--------------|--------|
| TSK-275 | BE Implementa SizeRule (SIZE_LATEST) | be | agent | S | US-032 | TSK-272 | done |
| TSK-276 | QA Test integrazione SizeRule — 3 scenari | qa | agent | S | US-032 | TSK-275 | done |
| TSK-277 | BE Implementa EarningsStabilityRule (EARNINGS_STABILITY_10Y) | be | agent | S | US-033 | TSK-272 | done |
| TSK-278 | QA Test integrazione EarningsStabilityRule — 4 scenari | qa | agent | S | US-033 | TSK-277 | done |
| TSK-279 | BE Implementa EpsGrowthRule (EPS_GROWTH_10Y) | be | agent | S | US-034 | TSK-272 | done |
| TSK-280 | QA Test integrazione EpsGrowthRule — 5 scenari | qa | agent | S | US-034 | TSK-279 | done |
| TSK-281 | BE Implementa Pe3yAvgRule (PE_3Y_AVG) | be | agent | S | US-035 | TSK-272 | done |
| TSK-282 | QA Test integrazione Pe3yAvgRule — 4 scenari | qa | agent | S | US-035 | TSK-281 | done |
| TSK-283 | BE Implementa PbLatestRule (PB_LATEST) | be | agent | S | US-036 | TSK-272 | done |
| TSK-284 | QA Test integrazione PbLatestRule — 4 scenari | qa | agent | S | US-036 | TSK-283 | done |
| TSK-285 | BE Estendi FmpAdapter con getDividendHistory (/stable/dividends) | be | agent | S | US-037 | TSK-272 | done |
| TSK-286 | DB Migration V0XX__fmp_dividend_history_snapshot | db | agent | XS | US-037 | — | done |
| TSK-287 | BE Implementa DividendContinuityRule (DIVIDEND_CONTINUITY_20Y) | be | agent | S | US-037 | TSK-285, TSK-286 | done |
| TSK-288 | QA Test DividendContinuityRule + contratto adapter getDividendHistory | qa | agent | S | US-037 | TSK-285, TSK-286, TSK-287 | done |
| TSK-289 | BE Estendi OpenAPI con 6 nuovi ruleId Graham + schema metadati | be | agent | S | cross-EP010 | TSK-275..287 | done |
| TSK-290 | FE Aggiorna TrafficLight component React a 13 ruleId | fe | agent | S | cross-EP010 | TSK-289 | done |
| TSK-291 | QA Contract test OpenAPI drift — 13 ruleId Graham + Buffett | qa | agent | XS | cross-EP010 | TSK-289 | done |
| TSK-292 | QA Integration test E2E EP-010 — fixture AAPL/MSFT/KO | qa | agent | M | cross-EP010 | TSK-276..290 | done |

**Totale Sprint 18:** 21 TSK (8 be, 1 fe, 1 db, 11 qa) — **21/21 `done`**

---

## Sprint 17 — AuthGuard static export runtime parity (EP-017) — COMPLETATO

**Obiettivo:** Applicare ADR-026 (Opzione B): mantenere `output: 'export'` in produzione e migrare il comportamento AuthGuard a layer client-side con validazione E2E su bundle statico servito dal backend.

**Stato:** COMPLETATO — **4/4 TSK `done`**, review CQRL `passed`. US-087 `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-266 | FE ClientAuthGuard e useAuthGuard per static export | fe | agent | M | US-087 | done |
| TSK-267 | FE Applicazione guard alle rotte protette e returnUrl | fe | agent | M | US-087 | done |
| TSK-268 | FE Hardening middleware.ts come dev-only | fe | agent | S | US-087 | done |
| TSK-269 | QA E2E AuthGuard su build statica servita dal backend | qa | agent | M | US-087 | done |

**Totale Sprint 17:** 4 TSK (3 fe, 1 qa)

---

## Sprint 16 — CQRL Bonifica Generale (EP-019) — COMPLETATO

**Obiettivo:** Retro-review CQRL su 224 TSK storici `done` (R1.0→15.5, esclusi TSK-224..238),
digest per wave, refactor da finding `conditional`/`high`, consolidamento ruleset canonical.

**Stato:** COMPLETATO — **25/25 TSK `done`**. EP-019 `done`. US-084/085/086 `done`. Snapshot analytics: `code_quality/reports/sprint-16-cqrl-summary.md` (locale, gitignored).

**Parallelismo (scheduler v2.11):** Fase A completata. Fase B slot 1+2 chiusi; slot 3 = 4 TSK no-op
(zero task_package dai digest). Level 2 = TSK-265 (gate: no-op close slot 3 o waiver PM).

### Fase A — Retro-review (`/review`, layer qa) — COMPLETATA

| TSK | Titolo | Est. | #review | `code_path` (glob) | Status |
|-----|--------|------|---------|-------------------|--------|
| TSK-240 | Wave A1 — BE auth & security | S | 8 | `security/**` | done |
| TSK-241 | Wave A2 — BE FMP adapter | S | 9 | `fmp/**` | done |
| TSK-242 | Wave A3 — BE rule engine (1/2) | M | 16 | `ruleengine/**` | done |
| TSK-263 | Wave A3b — BE rule engine (2/2) | M | 16 | `ruleengine/**` | done |
| TSK-243 | Wave A4 — BE deep analysis (1/2) | M | 18 | `secedgar/**` | done |
| TSK-264 | Wave A4b — BE deep LLM (2/2) | M | 17 | `llm/**` | done |
| TSK-244 | Wave A5 — BE screener & top-picks | M | 21 | `universe/**` | done |
| TSK-245 | Wave A6 — FE auth & session | S | 3 | `app/(auth)/**` | done |
| TSK-246 | Wave A7 — FE analysis & core pages | S | 13 | `app/analysis/**` | done |
| TSK-247 | Wave A8 — FE shared UI & lib | M | 24 | `components/**` | done |
| TSK-248 | Wave A9 — E2E Playwright | S | 9 | `frontend/e2e/**` | done |
| TSK-249 | Wave A10–11 — DB Flyway & Infra CI | M | 24 | `db/migration/**`, `.github/**` | done |
| TSK-251 | Wave A12a — BE platform services | M | 23 | `config/**` | done |
| TSK-262 | Wave A12b — QA contract & integration | M | 23 | `backend/src/test/**` | done |

### Fase B — Refactor (dev-agent per layer)

| TSK | Titolo | Layer | Est. | `depends_on` | Status |
|-----|--------|-------|------|--------------|--------|
| TSK-252 | Refactor BE auth & security | be | M | 240 | done |
| TSK-253 | Refactor BE FMP adapter (no-op slot 3) | be | M | 241 | done |
| TSK-254 | Refactor BE rule engine & valuation | be | L | 242, 263 | done |
| TSK-255 | Refactor BE deep analysis pipeline | be | L | 243, 264 | done |
| TSK-256 | Refactor BE screener & top-picks | be | M | 244 | done |
| TSK-257 | Refactor FE auth & session | fe | M | 245 | done |
| TSK-258 | Refactor FE pages & shared UI | fe | L | 246, 247 | done |
| TSK-259 | Refactor DB Flyway & infra CI (no-op slot 3) | db | M | 249 | done |
| TSK-260 | Refactor QA E2E & contract tests (no-op slot 3) | qa | M | 248, 262 | done |
| TSK-261 | Refactor BE platform & observability (no-op slot 3) | be | M | 251 | done |

### Fase C — Ruleset & chiusura

| TSK | Titolo | Layer | Est. | `depends_on` | Status |
|-----|--------|-------|------|--------------|--------|
| TSK-265 | Ruleset canonical + `/review summary` | qa | M | 252..261 | done |

**Totale Sprint 16:** 25 TSK (14 qa review + 6 be + 2 fe + 1 db + 2 qa refactor + 1 qa ruleset)

---

## Sprint 15.5 — Hotfix incident E2E locale (EP-016 US-083) — COMPLETATO

**Obiettivo:** Hardening Playwright mocked dopo incident run locale 2026-05-27: 4 fail
funzionali (keyboard a11y ×3, deep-analysis flake ×1). Vitest invariato; cutover-smoke skip
fuori scope. CI #131 resta riferimento.

**Stato:** COMPLETATO — 1/1 TSK `done`. US-083 `done`. EP-016 `done` (hotfix convalidato).

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|-----|--------|
| TSK-239 | QA Hardening E2E: 4 fail post-incident run locale | qa | agent | S | US-083 | done |

**Totale Sprint 15.5:** 1 TSK (0 be, 0 fe, 0 db, 1 qa)

---

## Sprint 15 — Hardening Sicurezza e Compliance (EP-018) — COMPLETATO

**Obiettivo:** Defense-in-depth enforcement, CSP nonce + CSRF per cookie auth, MFA TOTP con
recovery codes, rate limiting + progressive lockout + CAPTCHA threshold, HIBP password check,
dichiarazione formale PCI-DSS non applicabile. ADR-025. DB: mfa_secrets + login_attempts.

**Stato:** COMPLETATO — **22/22 TSK `done`**. Wave 1 + Wave 2 completate. EP-018 `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|-----|--------|
| TSK-219 | BE Audit + enforcement defense-in-depth: @Valid, @PreAuthorize, filtro userId | be | agent | M | US-079 | done |
| TSK-220 | QA Test defense-in-depth: 401, 403, filtro userId, payload invalido | qa | agent | M | US-079 | done |
| TSK-221 | BE SecurityHeadersConfig CSP header: script-src no unsafe-inline | be | agent | S | US-080 | done |
| TSK-222 | FE Next.js middleware CSP nonce per inline script | fe | agent | S | US-080 | done |
| TSK-223 | BE CsrfTokenConfig: CSRF per /api/auth/refresh e /api/auth/logout | be | agent | S | US-080 | done |
| TSK-224 | QA Test CSP + CSRF: header, XSS bloccato, 403 no CSRF, SameSite, E2E | qa | agent | M | US-080 | done |
| TSK-225 | DB Migration V026__create_mfa_secrets | db | agent | XS | US-081 | done |
| TSK-226 | DB Migration V025__create_login_attempts + indici | db | agent | XS | US-081 | done |
| TSK-227 | BE TotpService: secret TOTP, verifica codice, recovery codes BCrypt | be | agent | M | US-081 | done |
| TSK-228 | BE MfaController: endpoint enroll, verify, challenge, recovery, delete | be | agent | M | US-081 | done |
| TSK-229 | BE RateLimitingFilter: limiti IP + account su login/register/password-reset | be | agent | M | US-081 | done |
| TSK-230 | BE BruteForceProtectionService: lockout progressivo + CAPTCHA + cleanup | be | agent | M | US-081 | done |
| TSK-231 | BE HibpClient: verifica password compromesse k-anonymity SHA-1 | be | agent | S | US-081 | done |
| TSK-232 | FE MfaEnrollmentPage: QR code + verifica TOTP + recovery codes | fe | agent | M | US-081 | done |
| TSK-233 | FE MfaChallengeForm: form TOTP durante login MFA | fe | agent | S | US-081 | done |
| TSK-234 | QA Test MFA: enrollment, login TOTP, recovery, disabilitazione | qa | agent | M | US-081 | done |
| TSK-236 | QA Test HIBP: password compromessa rifiutata, sicura accettata | qa | agent | S | US-081 | done |
| TSK-237 | QA Verifica codebase no dati carta + validazione ADR PCI-DSS | qa | agent | S | US-082 | done |
| TSK-238 | FE Login CAPTCHA Turnstile quando captchaRequired | fe | agent | S | US-081 | done |
| TSK-270 | BE CSP allow-list Cloudflare Turnstile in SecurityHeadersConfig | be | agent | S | US-081 | done |
| TSK-235 | QA Test rate limiting + brute force: delay, CAPTCHA, lockout 30min | qa | agent | M | US-081 | done |
| TSK-271 | QA E2E Playwright mocked-tier flusso CAPTCHA login | qa | agent | M | US-081 | done |

**Totale Sprint 15:** 22 TSK (9 be, 4 fe, 2 db, 7 qa) — **22/22 `done`**

---

## Sprint 1 — Fondamenta (infra, DB schema completo, FMP adapter + cache)

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

**Stato:** COMPLETATO — 23/23 TSK `done`.

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
| TSK-071 | BE Ricalibra rate limit da wiki post US-029 | be | agent | XS | US-031 | done |
| TSK-072 | BE Migrazione FmpAdapterRestClient + DTO + fixture da v3 a /stable | be | agent | L | US-031 | done |

---

## Sprint 5.5 — Hotfix R1.1.x bug rule engine (EP-007 fase 2)

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

## Sprint 6 — Graham Defensive Completeness (EP-010) — COMPLETATO (prima wave)

**Obiettivo:** Prima implementazione dei 6 criteri Graham difensivi nel Rule Engine.

**Stato:** COMPLETATO — 18/18 TSK `done`. EP-010 prima wave chiusa.

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

## Follow-up EP-011 — Deep Analysis async/split + hardening discovery & embeddings (post-Sprint 8)

**Stato:** COMPLETATO — 5/5 TSK `done`. Hardening e refactor della pipeline deep analysis (EP-011) emersi durante la messa in esercizio, su US già chiuse (US-039, US-040, US-045/046). Non altera i totali storici di Sprint 7/8.

| TSK | Titolo | Layer | Consumer | Est. | US | `depends_on` | Status |
|-----|--------|-------|----------|------|----|--------------|--------|
| TSK-293 | BE Deep analysis run-model asincrono — V027 + DeepAnalysisRun + AsyncExecutor + enqueue/dedupe/getLatest + 2 endpoint | be | agent | M | US-045 | TSK-118 | done |
| TSK-294 | BE Split deep analysis INGEST vs ANALYSIS — V028 colonna kind + skip idempotente + dispatch executor + 2 endpoint ingest + not_indexed | be | agent | M | US-045 | TSK-293 | done |
| TSK-295 | BE Fix discovery filing SEC via FMP getSecFilings — from/to obbligatori, finestra 15 mesi, una chiamata per formType + filtro client-side | be | agent | S | US-039 | TSK-094 | done |
| TSK-296 | BE Fix trasporto EmbeddingService → SimpleClientHttpRequestFactory (body JSON vuoto via JdkClient → 422) | be | agent | S | US-040 | TSK-100 | done |
| TSK-297 | INFRA Sidecar embeddings su GPU NVIDIA via Podman CDI — override compose, app.py device+fp16, /health device, torch cu126, script CPU/GPU | infra | agent | M | US-040 | TSK-099 | done |

**Totale follow-up EP-011:** 5 TSK (4 be, 1 infra) — tutte `done`.

**Note di coerenza FE (US-046):** la tab Deep Analysis espone 3 tasti ("Indicizza filing" → `POST .../deep/ingest`, "Analizza" → `POST .../deep/runs?invoke_llm=false`, "Analizza + LLM" → `POST .../deep/runs?invoke_llm=true`), riga "ultima indicizzazione" da `GET .../deep/ingest/latest` e hint guidato su `error.reason = "not_indexed"`. Coperta dalle note datate 2026-05-29/30 in US-046 (FE già implementato; nessun nuovo TSK FE richiesto in questo follow-up).

---

## Sprint 9 — Top Value Picks batch (EP-012)

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

**Stato:** COMPLETATO (Sprint 12) — 10/10 TSK `done`. US-069–072 chiuse. **Hotfix Sprint 15.5:** US-083 + TSK-239 `done`. EP-016 `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-184 | Token CSS semantici: colors OKLCH + typography + shape + tailwind.config.ts | fe | agent | M | US-069 | done |
| TSK-185 | Migrazione 5 componenti principali da classi hardcoded a token semantici | fe | agent | M | US-069 | done |
| TSK-186 | QA Vitest test token system: seed change, tipografia, shape | qa | agent | S | US-069 | done |
| TSK-187 | ThemeProvider + useTheme + colors-dark.css + anti-FOUC script | fe | agent | M | US-070 | done |
| TSK-188 | QA Test ThemeProvider: prefers-color-scheme, persistenza, FOUC, contrasto | qa | agent | S | US-070 | done |
| TSK-189 | Token motion.css + state layer opacità + prefers-reduced-motion | fe | agent | S | US-071 | done |
| TSK-190 | QA Test motion: reduced-motion, state layers, focus visibile | qa | agent | S | US-071 | done |
| TSK-191 | Audit fix WCAG 2.2 AA: h1, focus, label, alt, zoom 200% su tutte le viste | fe | agent | L | US-072 | done |
| TSK-192 | QA Lighthouse CI + axe-core: target >= 95, zero serious/critical | qa | agent | M | US-072 | done |
| TSK-193 | QA Test E2E a11y tastiera: flussi critici senza mouse + screen reader | qa | agent | M | US-072 | done |

**Totale Sprint 12:** 10 TSK (5 fe, 5 qa)

---

## Sprint 13 — Notifiche Errori Frontend (EP-015)

**Stato:** COMPLETATO — 11/11 TSK `done`. US-064..068 chiuse. EP-015 `done`.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-194 | NotificationProvider React Context + useNotification hook API 4 livelli | fe | agent | M | US-064 | done |
| TSK-195 | NotificationToast wrapper shadcn/ui: WCAG roles, auto-dismiss, Esc, correlationId | fe | agent | M | US-064 | done |
| TSK-196 | QA Test NotificationProvider + Toast: 4 livelli, correlationId, a11y | qa | agent | S | US-064 | done |
| TSK-197 | errorCodeMap + locales/it.json: mapping 8+ type URI ProblemDetail + fallback | fe | agent | S | US-065 | done |
| TSK-198 | QA Test errorCodeMap: mapping + fallback + nessun HTTP raw | qa | agent | S | US-065 | done |
| TSK-199 | networkErrorInterceptor: offline/timeout/5xx/4xx + X-Correlation-Id | fe | agent | M | US-066 | done |
| TSK-200 | QA Test networkErrorInterceptor: 4 scenari rete | qa | agent | S | US-066 | done |
| TSK-201 | FormErrorSummary + aggiornamento form login/register/watchlist | fe | agent | M | US-067 | done |
| TSK-202 | QA Test FormErrorSummary: inline, summary, aria-describedby | qa | agent | S | US-067 | done |
| TSK-203 | Hardening a11y NotificationToast: contrasto, icone, multi-canale | fe | agent | S | US-068 | done |
| TSK-204 | QA axe-core audit notifiche: zero serious/critical, screen reader | qa | agent | S | US-068 | done |

**Totale Sprint 13:** 11 TSK (6 fe, 5 qa)

---

## Sprint 14 — Protezione Rotte e Sessione (EP-017)

**Stato:** COMPLETATO — 14/14 TSK `done`. US-073..US-078 chiuse.

| TSK | Titolo | Layer | Consumer | Est. | US | Status |
|-----|--------|-------|----------|------|----|--------|
| TSK-205 | route-config.ts mappa rotte dichiarativa tipizzata | fe | agent | S | US-074 | done |
| TSK-206 | AuthGuard middleware.ts Next.js: redirect login/returnUrl/403 | fe | agent | M | US-073 | done |
| TSK-207 | Pagina /403 + messaggio sessione scaduta su /login | fe | agent | S | US-073 | done |
| TSK-208 | QA Test E2E AuthGuard + route map: 6+ scenari | qa | agent | M | US-073/074 | done |
| TSK-209 | BE AuthController migrazione cookie httpOnly: login/refresh/logout | be | agent | L | US-075 | done |
| TSK-210 | BE OpenAPI: rimuovi refreshToken da response body | be | agent | XS | US-075 | done |
| TSK-211 | FE useAuthStore aggiornato: rehydration via cookie refresh | fe | agent | M | US-075 | done |
| TSK-212 | QA Test storage: no localStorage, httpOnly, rehydration F5, revoca | qa | agent | M | US-075 | done |
| TSK-213 | useTokenRefresh hook: timer pre-expiry 60s, mutex coda | fe | agent | M | US-076 | done |
| TSK-214 | QA Test refresh: no doppio refresh, sessione 20+ min, fallback | qa | agent | S | US-076 | done |
| TSK-215 | IdleTimeoutProvider: idle 15min + prompt 60s + absolute 8h | fe | agent | M | US-077 | done |
| TSK-216 | QA Test timeout: idle, extend, no-interaction, absolute, a11y | qa | agent | S | US-077 | done |
| TSK-217 | useLogout hook: revoca + pulizia store/cache + blocco history | fe | agent | M | US-078 | done |
| TSK-218 | QA Test E2E logout: revoca, cookie, store, back button, resilienza | qa | agent | S | US-078 | done |

**Totale Sprint 14:** 14 TSK (2 be, 7 fe, 5 qa)

---

## Riepilogo TSK per layer

| Release | Sprint | infra | db | be | fe | qa | Totale | Stato |
|---------|--------|-------|-----|-----|-----|-----|--------|-------|
| R1.0 | 1–4 | 2 | 6 | 22 | 12 | 7 | **49** | done |
| R1.1 | 5 | 5 | 0 | 7 | 3 | 8 | **23** | done |
| R1.1.x | 5.5 | 0 | 0 | 3 | 2 | 7 | **12** | done |
| R2.0 | 6 | 0 | 1 | 8 | 1 | 8 | **18** | done |
| R2.0 | 7 | 1 | 6 | 17 | 0 | 13 | **37** | done |
| R2.0 | 8 | 0 | 0 | 0 | 5 | 2 | **7** | done |
| R2.0 | EP-011 follow-up | 1 | 0 | 4 | 0 | 0 | **5** | done |
| R2.0 | 9 | 1 | 1 | 7 | 2 | 6 | **17** | done |
| R2.1 | 10 | 0 | 0 | 3 | 2 | 1 | **6** | done |
| R3.0 | 11 | 1 | 0 | 7 | 0 | 6 | **14** | done |
| R3.0 | 12 | 0 | 0 | 0 | 5 | 5 | **10** | done |
| R3.0 | 13 | 0 | 0 | 0 | 6 | 5 | **11** | done |
| R3.0 | 14 | 0 | 0 | 2 | 7 | 5 | **14** | done |
| R3.0 | 15 | 0 | 2 | 9 | 4 | 7 | **22** | done |
| R3.0 | 15.5 | 0 | 0 | 0 | 0 | 1 | **1** | done |
| R3.1 | 16 | 0 | 1 | 7 | 3 | 15 | **25** | done |
| R3.0 | 17 | 0 | 0 | 0 | 3 | 1 | **4** | done |
| R3.2 | 18 | 0 | 1 | 8 | 1 | 11 | **21** | done |
| R3.3 | 19 | 0 | 1 | 3 | 3 | 3 | **10** | done |
| | **TOTALE** | **11** | **19** | **107** | **59** | **112** | **308** | **308 done** |

---

## Ordinamento raccomandato e parallelismo

```
Sprint 11 (EP-014) ✅ ═══╗
                          ╠══► Sprint 13 (EP-015) ✅
Sprint 12 (EP-016) ✅ ═══╝        │
                                   ▼
                           Sprint 14 (EP-017 base) ✅
                                   │
                                   ├──► Sprint 17 (EP-017 US-087 ADR-026) ✅
                                   │
                                   ▼
                           Sprint 15 (EP-018) ✅ ◄── wave 1+2 completate
                                   │
                                   ├──► Sprint 15.5 (EP-016 hotfix TSK-239) ✅
                                   │
                                   ▼
                           Sprint 16 (EP-019 CQRL) ✅
                                   │
                                   ▼
                           Sprint 18 (EP-002 US-031 + EP-010) ✅ COMPLETATO
                                   │
                                   ▼
                           Sprint 19 (EP-020 LLM trasparenza) ✅ COMPLETATO
                                   │
                                   ▼
                           [BACKLOG ESAURITO — nessun EP/US in attesa]
```

---

## Dipendenze cross-sprint

```
Sprint 14 → Sprint 15: ✅ SODDISFATTA
  TSK-209 (cookie httpOnly auth) ──→ TSK-223 (CSRF per cookie endpoints)

Sprint 5 → Sprint 18 EP-002: ✅ SODDISFATTA (base adapter TSK-009/072 done)
  TSK-009/072 (FmpAdapter v3/stable) ──→ TSK-272 (migrazione routing /stable wave 2)

Sprint 18 EP-002 → Sprint 18 EP-010: ✅ SODDISFATTA (intra-sprint)
  TSK-272 (routing /stable) ──→ TSK-275..285 (rule Graham leggono da FmpAdapter /stable)

Sprint 18 → Sprint 19 EP-020: ✅ SODDISFATTA
  EP-011 pipeline deep analysis (done) ──→ EP-020 estensione trasparenza LLM
```

---

## Stato backlog

**Tutti gli sprint completati. Backlog attuale: 0 TSK in attesa.**

308/308 TSK `done` su 19 sprint (R1.0→R3.3). Nessun EP aperto rilevato nel kanban.
Per aggiungere nuove funzionalità aprire un nuovo EP tramite `/run` (product-manager → lead-architect → tpm).

**ADR accepted:** ADR-021, ADR-022, ADR-023, ADR-024, ADR-025, ADR-026.
