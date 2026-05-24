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
