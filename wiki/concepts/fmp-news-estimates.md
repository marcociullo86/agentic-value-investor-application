---
type: concept
sources: ["raw/FMP_Docs_8_News_and_Estimates.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, news, earnings, dividends, splits, analyst, sec, esg]
---
# Notizie, Corporate Action e Stime Analisti FMP

> FMP integra in un'unica piattaforma news per ticker, corporate action (dividendi, earnings, split), rating analisti, filing SEC e scoring ESG.

## Dettaglio

### Corporate Action
L'endpoint Earnings, Dividends, Splits copre le tre principali azioni societarie con range temporale configurabile tramite `from` e `to`. Utile per ricostruire storici di performance adjusted. [^src: raw/FMP_Docs_8_News_and_Estimates.txt §Earnings, Dividends, Splits APIs]

### Notizie Finanziarie
Stock News recupera articoli recenti filtrabili per uno o più `tickers`. Il parametro `limit` controlla il numero di articoli restituiti. [^src: raw/FMP_Docs_8_News_and_Estimates.txt §Stock News API]

### Stime Analisti
- **Analyst Estimates**: aspettative di consensus su EPS, revenue e altri KPI per periodi futuri. [^src: raw/FMP_Docs_8_News_and_Estimates.txt §Analyst Estimates & Upgrades/Downgrades]
- **Upgrades/Downgrades**: variazioni di rating da parte degli analisti Wall Street, con storico per simbolo. [^src: raw/FMP_Docs_8_News_and_Estimates.txt §Analyst Estimates & Upgrades/Downgrades]

### SEC Filings
Accesso diretto ai principali moduli SEC: 10-K (relazione annuale), 10-Q (relazione trimestrale), 8-K (eventi materiali). Il parametro `type` filtra per tipo di modulo. [^src: raw/FMP_Docs_8_News_and_Estimates.txt §SEC Filings API]

### ESG
Environmental, Social and Governance scoring per simbolo. Integra dati di sostenibilità nell'analisi fondamentale. [^src: raw/FMP_Docs_8_News_and_Estimates.txt §ESG API]

## Concetti correlati
[[fmp-company-info]]
[[fmp-financial-statements]]
[[fmp-executives]]

## Pagine collegate
[[fmp-docs-8-news-and-estimates]]
[[fmp-api]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
