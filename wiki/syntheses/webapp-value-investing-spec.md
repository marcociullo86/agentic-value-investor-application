---
type: synthesis
sources: ["raw/06_Documento_Funzionale_WebApp_Value_Investing.md", "raw/03_Analisi_Fondamentale_e_Valutazione.md", "raw/05_Analisi_10K_10Q_e_Regole_Buffett.md", "raw/FMP_Docs_4_Financial_Statements.txt", "raw/FMP_Docs_5_Metrics_and_Ratios.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [synthesis, product-spec, value-investing, fmp, rule-engine, webapp, graham, buffett, dcf, margin-of-safety]
---
# Specifica della WebApp Value Investing — Sintesi Cross-Domain

> Consolidamento dei requisiti funzionali della WebApp di screening value investing, collegando le regole di business Graham/Buffett agli endpoint FMP concreti e all'architettura Kotlin/Spring Boot.

## Contesto

La FSD del documento 06 formalizza come trasformare i principi analitici Graham/Buffett (raw 01-05) in software operativo: ogni regola del [[value-investing-rule-engine]] e' direttamente tracciabile a un endpoint [[fmp-api]] specifico e a una metrica di [[intrinsic-value]] o [[margin-of-safety]]. [^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md §1. Scopo del Progetto]

## Mappa Requisito → Regola → Endpoint FMP

| Requisito RF | Regola Business | Endpoint FMP | Concept |
|---|---|---|---|
| RF3 Redditività | ROE > 15%, ROIC > 12-15% | `/key-metrics/{t}?limit=10` | [[fmp-metrics-ratios]] |
| RF3 Pricing Power | Gross Margin > 40%, Net Margin > 10% | `/income-statement/{t}?limit=10` | [[fmp-financial-statements]] |
| RF3 Solidità | Current Ratio > 2; Debito LT/Net < 4 | `/balance-sheet-statement/{t}?limit=10` | [[fmp-financial-statements]] |
| RF3 CapEx | CapEx/Utile Netto < 25-30% | `/cash-flow-statement/{t}?limit=10` | [[fmp-financial-statements]] |
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

## Gap residui identificati

- Il FSD non specifica il framework SPA definitivo (React/Vue/Angular): lasciato come decisione tecnica aperta.
- La logica di calcolo degli Owner Earnings non e' dettagliata a livello di formula (solo riferimento a FCF/Owner Earnings). Vedi gap `vi-webapp-owner-earnings-formula` in `wiki/gaps.md`.
- Il gap `fmp-rate-limiting` (limiti ufficiali FMP) resta aperto: il throttling applicativo e' specificato come RNF ma i valori soglia non sono documentabili senza fonte ufficiale FMP.

## Concetti correlati
[[value-investing-rule-engine]]
[[webapp-architecture-vi]]
[[intrinsic-value]]
[[margin-of-safety]]
[[graham-number]]
[[economic-moat]]
[[fmp-financial-statements]]
[[fmp-metrics-ratios]]

## Pagine collegate
[[vi-06-webapp-value-investing-fsd]]
[[value-investing-fmp-integration]]
[[fmp-api-overview]]
[[warren-buffett]]
[[benjamin-graham]]
[[fmp-api]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-001 Ricerca e Screening — EP-002 Integrazione FMP — EP-003 Rule Engine — EP-004 Valore intrinseco e MoS — EP-005 Dashboard e Moat — EP-006 Watchlist
- Question hard aperte: Q_001 (Owner Earnings → blocca US-012), Q_002 (framework SPA → blocca US-014/15/16)
