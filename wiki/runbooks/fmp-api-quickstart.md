---
type: runbook
sources: ["raw/FMP_Docs_1_Auth_and_Search.txt", "raw/FMP_Docs_6_Quotes_and_Prices.txt", "raw/FMP_Docs_4_Financial_Statements.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, quickstart, runbook, integration]
---
# Quickstart — Integrazione FMP API

> Procedura minima per autenticarsi, cercare un simbolo, recuperare una quotazione e un rendiconto finanziario tramite FMP API.

## Prerequisiti

- API key FMP valida (ottenibile su financialmodelingprep.com)
- Client HTTP (curl, Postman, requests Python, fetch JS)

## Step 1 — Verifica Autenticazione

Chiamata di test con query param: [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

```
GET https://financialmodelingprep.com/api/v3/profile/AAPL?apikey=YOUR_API_KEY
```

Risposta attesa: JSON con profilo Apple Inc.

## Step 2 — Ricerca Simbolo per Nome

Trovare il ticker di una societa' per nome: [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Company Name Search API]

```
GET https://financialmodelingprep.com/api/v3/search?query=Apple&limit=5&apikey=YOUR_API_KEY
```

## Step 3 — Quotazione Real-time

Recuperare il prezzo corrente di un titolo: [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Real-time, Short, Premarket, Aftermarket Quote APIs]

```
GET https://financialmodelingprep.com/api/v3/quote/AAPL?apikey=YOUR_API_KEY
```

Per quotazioni multiple in batch: [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Batch Quote APIs]

```
GET https://financialmodelingprep.com/api/v3/quote/AAPL,MSFT,GOOGL?apikey=YOUR_API_KEY
```

## Step 4 — Conto Economico Annuale

Recuperare gli ultimi 5 anni di conto economico: [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API]

```
GET https://financialmodelingprep.com/api/v3/income-statement/AAPL?period=annual&limit=5&apikey=YOUR_API_KEY
```

## Step 5 — Stock Screener

Trovare titoli tech con market cap > 1B e prezzo < 100: [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Stock Screener API]

```
GET https://financialmodelingprep.com/api/v3/stock-screener?sector=Technology&marketCapMoreThan=1000000000&priceLowerThan=100&apikey=YOUR_API_KEY
```

## Note

- Gli URL esatti degli endpoint non sono documentati nei raw disponibili. Gli URL nel runbook sono esempi basati su pattern comuni FMP (v3). Per gli URL ufficiali consultare la documentazione FMP aggiornata.
- Il rate limiting non e' documentato nei raw: vedere gap in `wiki/gaps.md`.

## Pagine collegate
[[fmp-auth]]
[[fmp-api-overview]]
[[fmp-search]]
[[fmp-quotes]]
[[fmp-financial-statements]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
