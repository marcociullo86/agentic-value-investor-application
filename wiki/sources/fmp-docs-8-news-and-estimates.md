---
type: source
sources: ["raw/FMP_Docs_8_News_and_Estimates.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, news, dividends, earnings, analyst, sec, esg]
---
# FMP Docs 8 — News, Dividends, Earnings & Analyst Estimates

> FMP centralizza dati su corporate action (dividendi, split, earnings), notizie finanziarie, stime degli analisti, filing SEC e scoring ESG.

## Contesto

Questa sezione copre gli endpoint informativi e di sentiment di mercato, incluse le azioni societarie storiche e future, i rating degli analisti e i dati di sostenibilità. [^src: raw/FMP_Docs_8_News_and_Estimates.txt §News, Dividends, Earnings & Analyst Estimates]

## Endpoint

### Earnings, Dividends, Splits
Accesso a corporate action storiche e future. Parametri: `symbol`* (stringa), `from` (data), `to` (data). [^src: raw/FMP_Docs_8_News_and_Estimates.txt §Earnings, Dividends, Splits APIs]

### Stock News
Ultimi articoli di notizie correlati a specifici ticker. Parametri: `tickers` (stringa), `limit` (numero). [^src: raw/FMP_Docs_8_News_and_Estimates.txt §Stock News API]

### Analyst Estimates & Upgrades/Downgrades
Aspettative degli analisti di Wall Street e variazioni di rating. Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_8_News_and_Estimates.txt §Analyst Estimates & Upgrades/Downgrades]

### SEC Filings
Link diretti e dati estratti da filing SEC (moduli 10-K, 10-Q, 8-K). Parametri: `symbol`* (stringa), `type` (stringa). [^src: raw/FMP_Docs_8_News_and_Estimates.txt §SEC Filings API]

### ESG API
Dati di scoring ambientale, sociale e di governance (ESG). Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_8_News_and_Estimates.txt §ESG API]

## Concetti correlati
[[fmp-news-estimates]]

## Pagine collegate
[[fmp-api]]
[[fmp-docs-3-company-info]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
