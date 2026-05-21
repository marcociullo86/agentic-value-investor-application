---
type: entity
sources: ["raw/FMP_Docs_1_Auth_and_Search.txt", "raw/FMP_Docs_2_Stock_Directory.txt", "raw/FMP_Docs_3_Company_Info.txt", "raw/FMP_Docs_4_Financial_Statements.txt", "raw/FMP_Docs_5_Metrics_and_Ratios.txt", "raw/FMP_Docs_6_Quotes_and_Prices.txt", "raw/FMP_Docs_7_Executives_and_Compensation.txt", "raw/FMP_Docs_8_News_and_Estimates.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, financial-data, provider]
---
# Financial Modeling Prep (FMP) API

> Financial Modeling Prep (FMP) e' un provider di dati finanziari che espone una REST API per accedere a quotazioni, rendiconti finanziari, profili aziendali, metriche, notizie e dati ESG per strumenti quotati globalmente. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

## Descrizione

FMP offre una copertura ampia di strumenti finanziari globali tramite una REST API autenticata tramite API key. I dati spaziano da quotazioni real-time a serie storiche di rendiconti contabili, con supporto per azioni, ETF, crypto, forex e indici. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

## Aree funzionali

| Area | Source | Endpoint principali |
|------|--------|---------------------|
| Autenticazione e Ricerca | [[fmp-docs-1-auth-and-search]] | Symbol Search, Screener, CIK, CUSIP/ISIN |
| Stock Directory | [[fmp-docs-2-stock-directory]] | Symbols List, CIK List, ETF List |
| Company Info | [[fmp-docs-3-company-info]] | Profile, Market Cap, Float, M&A |
| Financial Statements | [[fmp-docs-4-financial-statements]] | Income, Balance Sheet, Cash Flow, TTM |
| Metrics & Ratios | [[fmp-docs-5-metrics-and-ratios]] | Key Metrics, Ratios, DCF, EV, Scores |
| Quotes & Prices | [[fmp-docs-6-quotes-and-prices]] | Real-time, Batch, Crypto/Forex/Index |
| Executives | [[fmp-docs-7-executives-and-compensation]] | Profiles, Compensation, Benchmark |
| News & Estimates | [[fmp-docs-8-news-and-estimates]] | News, Dividends, Analyst, SEC, ESG |

## Copertura geografica

Gli endpoint contrassegnati con "Globe Flag" hanno copertura globale. Quelli con "USA Flag" sono limitati al mercato statunitense (SEC, CIK, compensi esecutivi, aziende delistate USA). [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Company Search]

## Concetti correlati
[[fmp-auth]]
[[fmp-api-overview]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
