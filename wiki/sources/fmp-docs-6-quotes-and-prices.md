---
type: source
sources: ["raw/FMP_Docs_6_Quotes_and_Prices.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, quotes, realtime, crypto, forex]
---
# FMP Docs 6 — Quotes & Prices

> FMP fornisce quotazioni real-time, premarket/aftermarket, variazioni di prezzo, batch quote e quotazioni per asset non azionari (crypto, forex, indici).

## Contesto

Gli endpoint Quotes consentono di recuperare prezzi aggiornati per singoli titoli o in batch, con supporto per sessioni estese e classi di asset alternative. [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Quotes]

## Endpoint

### Real-time, Short, Premarket, Aftermarket Quote
Quotazioni in tempo reale in varie modalità (standard, breve, premarket, aftermarket). Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Real-time, Short, Premarket, Aftermarket Quote APIs]

### Stock Price Change
Tracciamento delle variazioni di prezzo in tempo reale su diversi periodi. Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Stock Price Change API]

### Batch Quote (Standard, Short, Aftermarket Trade/Quote)
Recupero di quotazioni real-time per più titoli in una singola richiesta. Parametri: `symbols`* (stringa, es. `AAPL,MSFT`). [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Batch Quote APIs]

### Crypto, Forex, Index Quotes
Quotazioni real-time per asset non azionari (criptovalute, valute estere, indici). Parametri: `short` (booleano). [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Crypto, Forex, Index Quotes APIs]

## Concetti correlati
[[fmp-quotes]]

## Pagine collegate
[[fmp-api]]
[[fmp-docs-1-auth-and-search]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
