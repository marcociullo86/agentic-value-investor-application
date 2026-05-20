---
type: source
sources: ["raw/FMP_Docs_1_Auth_and_Search.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, auth, search]
---
# FMP Docs 1 — Autenticazione e Ricerca

> La Financial Modeling Prep API richiede autenticazione via API key su ogni richiesta; fornisce endpoint di ricerca per simboli, nomi, CIK, CUSIP/ISIN e screener.

## Contesto

Financial Modeling Prep (FMP) espone una REST API per dati finanziari. Ogni chiamata deve essere autenticata tramite API key, passabile sia come header HTTP sia come parametro di query. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

## Autenticazione

Due modalità supportate: [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

- **Header**: `apikey: YOUR_API_KEY`
- **Query param**: `?apikey=YOUR_API_KEY` in append a ogni URL di richiesta.

## Endpoint di ricerca

### Stock Symbol Search
Ricerca il ticker symbol di un titolo. Parametri: `query`* (stringa, es. `AAPL`), `limit` (numero, es. 50), `exchange` (stringa, es. `NASDAQ`). [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Stock Symbol Search API]

### Company Name Search
Ricerca per nome azienda restituendo ticker, nome e dettagli sull'exchange. Parametri: `query`* (stringa), `limit`, `exchange`. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Company Name Search API]

### CIK API
Recupera il Central Index Key (CIK) per società quotate in borsa negli USA. Parametri: `cik`* (stringa, es. `320193`), `limit`. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §CIK API]

### CUSIP & ISIN API
Ricerca informazioni su strumenti finanziari tramite codice CUSIP o ISIN. Parametri: `cusip`* oppure `isin`*. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §CUSIP API & ISIN API]

### Stock Screener API
Filtra titoli per criteri di investimento. Parametri principali: `marketCapMoreThan`, `marketCapLowerThan`, `sector`, `industry`, `betaMoreThan`, `betaLowerThan`, `priceMoreThan`, `priceLowerThan`, `dividendMoreThan`, `dividendLowerThan`, `volumeMoreThan`, `volumeLowerThan`, `exchange`, `country`, `isEtf`, `isFund`, `isActivelyTrading`, `limit`, `includeAllShareClasses`. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Stock Screener API]

### Exchange Variants API
Trova su quali exchange è quotato un dato simbolo. Parametri: `symbol`* (stringa, es. `AAPL`). [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Exchange Variants API]

## Concetti correlati
[[fmp-auth]]
[[fmp-search]]

## Pagine collegate
[[fmp-api]]
[[fmp-api-overview]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
