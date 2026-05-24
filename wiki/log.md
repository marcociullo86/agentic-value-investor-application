---
id: log
type: log
title: Wiki Log
status: draft
created: 2026-05-20
sources: []
tags: [audit]
---
# Wiki Operations Log — App Template Demo

Audit trail append-only. Una riga per operazione canonica. Formato:
`[YYYY-MM-DD HH:MM] <operation> — <one-line summary> — files touched: <N>`

Vedi `.claude/skills/wiki-log-entry.md` per i template per operazione.

---

[2026-05-20 00:00] bootstrap — factory llm-wiki++ v2.8 scaffolded (full-stack-agents + auto stack + monorepo) — files touched: 50
[2026-05-23 00:00] ingest — raw/agent.py (Value Investor Bot v2.6.1, 2350 righe LangGraph Python) + raw/09_agent_py_method_analysis.md (raw analitico): 7 nuove pagine wiki (6 concept + 1 synthesis), 3 pagine estese append-only, 3 gap chiusi (vi-sec-narrative-gap parziale, wiki-promote-sec-edgar-adapter-spec, wiki-promote-universe-screener-spec), 2 nuovi gap aperti (agent-py-roe-lookback-policy, agent-py-current-ratio-routing-gap) — files touched: 11

[2026-05-23 10:00] lint — health check completo EP-010/011/012 (70 TSK, 20 US, 3 EP, 6 wiki concepts new, 1 synthesis new, 2 entities extended); checks 1–4d: 4 ERROR (2 wikilink false-positive, 1 unsourced-claim, 1 pending-clarification TSK-131 fmp-stable-rate-limiting) + 14 WARNING (8 frontmatter updated missing, 4 citation non-adjacent, 2 wiki_page pending-clarification expected); 0 topology mismatch, 0 VCS mismatch; sprint.md coerente TSK-073…142 + US-032…051; heal-eligible: 2 (frontmatter updated + wikilink false-positive); human-only: 4 gap clarifications (sec-edgar-adapter-spec, analysis-api-pipeline-deep, universe-screener-spec, fmp-stable-rate-limiting) — wiki/lint/2026-05-23-lint-report.md written — files touched: 1

## [2026-05-20] ingest | FMP_Docs_1_Auth_and_Search + FMP_Docs_2_Stock_Directory + FMP_Docs_3_Company_Info + FMP_Docs_4_Financial_Statements + FMP_Docs_5_Metrics_and_Ratios + FMP_Docs_6_Quotes_and_Prices + FMP_Docs_7_Executives_and_Compensation + FMP_Docs_8_News_and_Estimates
Pagine create: 20 | Figure: 0 | Aggiornamenti: 2 (index, gaps) | Gap nuovi: 3 | Gap chiusi: 0
[2026-05-20 12:00] ingest — nuova area tematica value-investing: 5 sources, 8 concepts, 2 entities, 1 synthesis cross-domain, 1 runbook — files touched: 17

## [2026-05-20] ingest | 06_Documento_Funzionale_WebApp_Value_Investing
Pagine create: 5 | Figure: 0 | Aggiornamenti: 3 (index, gaps, value-investing-fmp-integration) | Gap nuovi: 2 | Gap chiusi: 0
[2026-05-20 14:00] ingest — nuova area tematica product-spec: 1 source, 2 concepts, 1 synthesis cross-domain, 1 runbook; cross-link FMP+ValueInvesting — files touched: 9

## [2026-05-20] ingest | 07_Risoluzione_Q002_Q003 + 08_Risoluzione_Q001_Owner_Earnings
Pagine create: 2 | Figure: 0 | Aggiornamenti: 4 (index, gaps, value-investing-rule-engine, webapp-architecture-vi) | Gap nuovi: 0 | Gap chiusi: 3 (vi-webapp-owner-earnings-formula, vi-webapp-spa-framework-decision, vi-webapp-screener-criteria)
[2026-05-20 16:00] ingest — risoluzione Q_001/Q_002/Q_003: 2 sources, 3 gap chiusi, 4 aggiornamenti non-distruttivi — files touched: 8
[2026-05-24 00:00] dev(be) TSK-073 — SizeRule.kt creato (SIZE_LATEST, single-year revenue check, threshold $100M; INDETERMINATE su income vuoto/revenue null; RED su revenue=0.0; utility carve-out flagged TODO out-of-scope); TSK-073 → done — files touched: 1
[2026-05-24 00:00] dev(be) TSK-075 — EarningsStabilityRule.kt creato (EARNINGS_STABILITY_10Y, multi-year netIncome>0 check su top-10 fiscal years; GREEN 10/10, YELLOW 9/10, RED ≤8/10, INDETERMINATE <10 record, NOT_CALCULABLE income vuoto); netIncome null trattato come anno non-positivo con label "n/d" nel rationale (PATTERN §7 r.13 + US-004); DRIFT path corretto da TSK (com/valueinvesting/rules/ → com.valueinvesting.webapp.ruleengine.rules) e scope ridotto a lettura dataset.income (NO FmpAdapter injection, rule pure); RuleSignal non esteso (yearsPositive veicolato via observedValue + rationale, scope TSK-087); TSK-075 → done — files touched: 1
[2026-05-24 14:08] dev(qa) TSK-074 — SizeRuleTest.kt creato (11 unit test JUnit5+AssertJ; AC US-032 coperti: GREEN/RED/INDETERMINATE + boundary 100M exact / 99_999_999.99 / 0.0 / null + latest-year picker fwd+rev + ruleId check + threshold label); integration test (WireMock GET /api/analysis/{ticker}) demandato a TSK-090 (E2E); TSK-074 → done — files touched: 2
[2026-05-24 14:08] dev(qa) TSK-076 — EarningsStabilityRuleTest.kt creato (11 unit test JUnit5+AssertJ; AC US-033 coperti: GREEN 10/10 / YELLOW 9/10 con anno in rationale / RED 8/10 / INDETERMINATE <10 record / NOT_CALCULABLE empty; edge: netIncome=null → YELLOW "n/d", netIncome=0.0 → non-positivo strict-greater-than, 12 record → top-10 più recenti + 2 vecchi scartati, ruleId check, threshold label); integration test demandato a TSK-090; DRIFT path corretto (com/valueinvesting/rules/ → com.valueinvesting.webapp.ruleengine.rules); TSK-076 → done — files touched: 2
[2026-05-24 14:13] dev(qa) build+test verification — gradle:8-jdk21-alpine container (Podman bind-mount src/backend) — gradle test --tests "*SizeRuleTest" --tests "*EarningsStabilityRuleTest" --no-daemon → BUILD SUCCESSFUL in 4m 30s; SizeRuleTest 11 tests 0 failures 0 errors; EarningsStabilityRuleTest 11 tests 0 failures 0 errors; total 22/22 PASS; warning pre-esistenti (Kotlin 2.2 @JsonProperty default-target KT-73255 + Spring Security DaoAuthenticationProvider deprecation) non legati a TSK-073/074/075/076
[2026-05-24 00:00] dev(be) TSK-087 — design_&_architecture/api/openapi.yaml: RuleSignal.ruleId trasformato da string libero (example "profitability.roe", drift OpenAPI↔codice) a enum chiuso 13 valori (7 Buffett-leaning EP-003: ROE_10Y_AVG, ROIC_10Y_AVG, GROSS_MARGIN_10Y_AVG, NET_MARGIN_10Y_AVG, CURRENT_RATIO_LATEST, DEBT_TO_INCOME_LATEST, CAPEX_INTENSITY_10Y_AVG; 6 Graham defensive EP-010: SIZE_LATEST, EARNINGS_STABILITY_10Y, EPS_GROWTH_10Y, PE_3Y_AVG, PB_LATEST, DIVIDEND_CONTINUITY_20Y); x-extension `x-buffett-quality` + `x-graham-defensive` aggiunte come array di ruleId a livello property; example aggiornato a ROE_10Y_AVG; DRIFT path corretto da TSK (src/backend/src/main/resources/openapi/ inesistente → design_&_architecture/api/openapi.yaml canonico); DRIFT nome schema (RuleEngineResultItem inesistente → RuleSignal a riga ~587, container RuleEngineResult invariato); SCOPE RIDOTTO motivato (no additionalProperties / no oneOf discriminato: shape `observedValue+rationale` stabile in 9 rule Kotlin gia' in produzione, evitato breaking sui client TS auto-generati; nota aggiunta in description di RuleSignal: strutturazione tipata per-ruleId rimandata a futuro refactor); nessuna modifica a RuleEngineResult ne' a RuleSignal {signal, observedValue, threshold, rationale}; nessuna modifica al BE Kotlin (RuleSignal.kt resta `ruleId: String` libero, contract tightening non-breaking finche' i ruleId Kotlin restano nel set definito); lint OpenAPI 3.x NON eseguito (npx @redocly/cli non disponibile + nessun package.json/.redocly.yaml al root); TSK-087 → done — files touched: 1
[2026-05-24 14:30] dev(be) Blocco 1 BE — 3 nuove rule create in com.valueinvesting.webapp.ruleengine.rules: EpsGrowthRule.kt (TSK-077, EPS_GROWTH_10Y, criterio Graham 3: media triennale iniziale anni 1-3 vs finale 8-10, soglia +33%; INDETERMINATE su avgEpsInitial≤0 o serie<10y; rationale italiano formattato); Pe3yAvgRule.kt (TSK-079, PE_3Y_AVG, criterio Graham 5: currentPrice/avgEps3y, soglie ≤15/15-20/>20; reads dataset.currentPrice; INDETERMINATE su avgEps3y≤0 o currentPrice null); PbLatestRule.kt (TSK-081, PB_LATEST, criterio Graham 6: currentPrice/bookValuePerShare latest, soglie ≤1.5/1.5-3.0/>3.0; INDETERMINATE su bvps null o ≤0, no fallback derivativo); FinancialDataset esteso con campo `currentPrice: Double?` (default null, backward-compat); AnalyzeTickerService.analyze() popola currentPrice da profile.value.price PRIMA di chiamare RuleEngineService.evaluateAll; zero modifiche a RuleEngineService/RuleSignal/9 rule pre-esistenti; auto-discovery Spring via @Component; TSK-077/079/081 → done — files touched: 5
