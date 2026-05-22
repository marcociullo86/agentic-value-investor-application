---
type: synthesis
sources: ["raw/06_Documento_Funzionale_WebApp_Value_Investing.md", "raw/03_Analisi_Fondamentale_e_Valutazione.md", "raw/05_Analisi_10K_10Q_e_Regole_Buffett.md", "raw/FMP_Docs_4_Financial_Statements.txt", "raw/FMP_Docs_5_Metrics_and_Ratios.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-21
tags: [synthesis, product-spec, value-investing, fmp, rule-engine, webapp, graham, buffett, dcf, margin-of-safety]
---
# Specifica della WebApp Value Investing — Sintesi Cross-Domain

> Consolidamento dei requisiti funzionali della WebApp di screening value investing, collegando le regole di business Graham/Buffett agli endpoint FMP concreti e all'architettura Kotlin/Spring Boot.

## Contesto

La FSD del documento 06 formalizza come trasformare i principi analitici Graham/Buffett (raw 01-05) in software operativo: ogni regola del [[value-investing-rule-engine]] e' direttamente tracciabile a un endpoint [[fmp-api]] specifico e a una metrica di [[intrinsic-value]] o [[margin-of-safety]]. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §1. Scopo del Progetto]

## Mappa Requisito → Regola → Endpoint FMP

| Requisito RF | Regola Business | Endpoint FMP | Concept |
|---|---|---|---|
| RF3 Redditività | ROE > 15%, ROIC > 12-15% | `/key-metrics/{t}?limit=10` | [[fmp-key-metrics-ratios]] |
| RF3 Pricing Power | Gross Margin > 40%, Net Margin > 10% | `/income-statement/{t}?limit=10` | [[fmp-financial-statements-stable]] |
| RF3 Solidità | Current Ratio > 2; Debito LT/Net < 4 | `/balance-sheet-statement/{t}?limit=10` | [[fmp-financial-statements-stable]] |
| RF3 CapEx | CapEx/Utile Netto < 25-30% | `/cash-flow-statement/{t}?limit=10` | [[fmp-financial-statements-stable]] |
| RF4 Graham Number | `Sqrt(22.5 * EPS * BVPS)` | `/key-metrics/` + `/income-statement/` | [[graham-number]] |
| RF4 DCF | FCF proiettato, max 5-7% crescita, DR 9-10% | `/cash-flow-statement/` + `/key-metrics/` | [[intrinsic-value]] |
| RF4 Margin of Safety | Prezzo < DCF * 0.70 | `/profile/{t}` (prezzo corrente) | [[margin-of-safety]] |

[^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §RF2: Integrazione API (Financial Modeling Prep)]
[^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Key Metrics & TTM Key Metrics API]

## Corrispondenza con i Principi Graham/Buffett

Le soglie del Rule Engine sono derivate direttamente dai testi di riferimento: [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3C. Le Regole Finanziarie Quantitative]

- ROE > 15%, ROIC > 12-15%: criterio Buffett di redditività sostenibile.
- Gross Margin > 40%: proxy dell'[[economic-moat]] (vantaggio competitivo durevole).
- CapEx/Utile < 25-30%: indicatore di business "capital-light" preferito da Buffett.
- Debito LT/Utile Netto < 4: criterio Graham di solidità patrimoniale.
- Graham Number: formula diretta da [[graham-number]] (raw 03).
- DCF con MoS 30%: applicazione del [[margin-of-safety]] di Graham.

[^src: raw/03_Analisi_Fondamentale_e_Valutazione.md §2. Il Numero di Graham e Metriche Avanzate]

## Architettura di Supporto

Il [[webapp-architecture-vi]] definisce il layer tecnico che abilita queste regole:

- **Kotlin/Spring Boot**: orchestrazione, caching 24h, throttling FMP.
- **PostgreSQL**: persistenza watchlist e cache giornaliera bilanci.
- **SPA**: rendering Traffic Light, grafici storici, checklist Moat qualitativo.

Il caching 24h e il throttling affrontano parzialmente il gap `fmp-rate-limiting` a livello applicativo, ma non sostituiscono la documentazione dei limiti ufficiali FMP. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §5. Requisiti Non Funzionali]

## Stato implementazione R1.0 (v2026-05-21)

| Epica | Sprint | Stato backend (L5) |
|-------|--------|-------------------|
| EP-002 FMP | 1 | Adapter, cache 24h, resilienza, `GET /api/financials/{ticker}` |
| EP-003 Rule Engine | 2 | 7 regole + `RuleEngineService` |
| EP-004 Valutazione | 2 | Graham, DCF Greenwald/FCF, MoS, `GET /api/analysis/{ticker}` |
| EP-001 Screening | 2–3 | Contratto OpenAPI; **non** implementato (search/screener) |
| EP-005 Dashboard | 3 | Non implementato (FE Traffic Light, grafici, moat) |
| EP-006 Watchlist/Auth | 3 | Non implementato |

**Milestone Sprint 2 raggiunta** su branch `feature/sprint2-analysis`: analisi end-to-end con test integrazione e [[openapi-contract-check]]. Merge verso `master` previsto dopo review PR.

Endpoint implementati vs contratto completo: vedi [[analysis-api-pipeline]] e `design_&_architecture/api/openapi.yaml`.

## Gap residui identificati

- Framework SPA: stack scelto in ADR-001 (Next.js) ma bootstrap FE (TSK-030) ancora su `master` / track parallelo.
- Owner Earnings: formula formalizzata in [[vi-08-risoluzione-q001-owner-earnings]]; gap `vi-webapp-owner-earnings-formula` **chiuso** in `wiki/gaps.md`.
- `fmp-rate-limiting`: limiti ufficiali FMP non documentati nei raw; Resilience4j + cache mitigano a livello app.

## Concetti correlati
[[value-investing-rule-engine]]
[[analysis-api-pipeline]]
[[openapi-contract-check]]
[[webapp-architecture-vi]]
[[intrinsic-value]]
[[margin-of-safety]]
[[graham-number]]
[[economic-moat]]
[[fmp-financial-statements-stable]]
[[fmp-key-metrics-ratios]]

## Pagine collegate
[[vi-06-webapp-value-investing-fsd]]
[[value-investing-fmp-integration]]
[[fmp-api-overview]]
[[warren-buffett]]
[[benjamin-graham]]
[[fmp-api]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- **R1.0 (done):** EP-001…EP-006 (US-001…US-020)
- **R1.1 (defined):** EP-007 Hardening (US-021…025), EP-008 Deploy/ops (US-026…028), EP-009 Throttling FMP (US-029, US-030)
- Question hard aperte: nessuna (vedi `management/questions.md`)
