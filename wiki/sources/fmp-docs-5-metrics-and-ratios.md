---
type: source
sources: ["raw/FMP_Docs_5_Metrics_and_Ratios.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, metrics, ratios, dcf, enterprise-value]
---
# FMP Docs 5 — Key Metrics, Ratios & Enterprise Value

> FMP fornisce metriche chiave, indici di redditività/liquidità/efficienza, punteggi finanziari (Altman Z-Score, Piotroski), Enterprise Value e valutazione DCF per qualsiasi simbolo quotato globalmente.

## Contesto

Gli endpoint di questa sezione supportano l'analisi fondamentale quantitativa, dalla valutazione relativa (P/E, EV) a quella assoluta (DCF), con supporto TTM e per periodo. [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Key Metrics, Ratios & Enterprise Value]

## Endpoint

### Key Metrics & TTM Key Metrics
Metriche principali: revenue, net income, P/E ratio e altro. Parametri: `symbol`* (stringa), `limit` (numero), `period` (stringa). [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Key Metrics & TTM Key Metrics API]

### Financial Ratios & TTM Financial Ratios
Indici dettagliati di redditività, liquidità ed efficienza. Parametri: `symbol`* (stringa), `limit` (numero), `period` (stringa). [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Financial Ratios & TTM Financial Ratios API]

### Financial Scores
Altman Z-Score, Piotroski Score e altri indicatori di salute finanziaria. Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Financial Scores API]

### Enterprise Value & TTM Enterprise Value
Calcolo dell'Enterprise Value di una società. Parametri: `symbol`* (stringa), `limit`, `period`. [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Enterprise Value & TTM Enterprise Value API]

### Discounted Cash Flow (DCF)
Valore DCF calcolato per una società. Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Discounted Cash Flow (DCF) API]

## Concetti correlati
[[fmp-metrics-ratios]]

## Pagine collegate
[[fmp-api]]
[[fmp-docs-4-financial-statements]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
