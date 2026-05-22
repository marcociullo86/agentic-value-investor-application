---
id: fmp-company-search
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, search, symbol, company]
---
# FMP — Company Search (stable)

^src: raw/fmp_docs.md §Company Search — ^src: raw/fmp_docs.json sezione="Company Search" (6 endpoint)

Sezione dell'API FMP stable dedicata alla ricerca di titoli e identificativi di sicurezza. Tutti gli endpoint rispondono con array JSON.

---

## Endpoint della sezione

### 1. Stock Symbol Search
- **Path**: `GET /stable/search-symbol`
- **Parametri**: `query*` (string, es. `AAPL`), `limit` (number, default 50), `exchange` (string, es. `NASDAQ`)
- **Response**: `[{symbol, name, currency, exchangeFullName, exchange}]`
- **Uso**: ricerca per simbolo o nome parziale — punto di ingresso principale per SearchService

### 2. Company Name Search
- **Path**: `GET /stable/search-name`
- **Parametri**: `query*` (string, es. `Apple`), `limit` (number), `exchange` (string)
- **Response**: `[{symbol, name, currency, exchangeFullName, exchange}]`
- **Uso**: ricerca per nome completo o parziale — complementare a search-symbol quando il ticker e' sconosciuto

### 3. CIK Search
- **Path**: `GET /stable/search-cik`
- **Parametri**: `cik*` (string, es. `320193`), `limit` (number)
- **Response**: `[{symbol, companyName, cik, exchangeFullName, exchange, currency}]`
- **Uso**: lookup CIK SEC — utile per accesso filings EDGAR

### 4. CUSIP Search
- **Path**: `GET /stable/search-cusip`
- **Parametri**: `cusip*` (string, es. `037833100`)
- **Response**: `[{symbol, companyName, cusip, marketCap}]`
- **Uso**: identificazione titoli per CUSIP (identif. nordamericano)

### 5. ISIN Search
- **Path**: `GET /stable/search-isin`
- **Parametri**: `isin*` (string, es. `US0378331005`)
- **Response**: `[{symbol, companyName, isin, marketCap}]`
- **Uso**: identificazione titoli internazionali per ISIN (12 caratteri)

### 6. Exchange Variants
- **Path**: `GET /stable/exchange-variants`
- **Parametri**: `symbol*` (string)
- **Response**: lista degli exchange dove il simbolo e' listato
- **Uso**: verifica liquidita' multi-exchange

---

## Pattern di risposta comune

```json
[
  {
    "symbol": "AAPL",
    "name": "Apple Inc.",
    "currency": "USD",
    "exchangeFullName": "NASDAQ Global Select",
    "exchange": "NASDAQ"
  }
]
```

---

## Note implementative

- `search-symbol` e `search-name` sono i due endpoint usati da `SearchService` (TSK-004/TSK-005).
- Il parametro `exchange` filtra per exchange specifico (es. `NASDAQ`, `NYSE`).
- La stable API omette i duplicati e rinominazioni di simboli rispetto alla v3.
- Il parametro `limit` controlla il numero massimo di risultati (default 50).

---

## Cross-link

- Entity: [[fmp-api]]
- Sezioni correlate: [[fmp-company-information]] (profile dopo ricerca), [[fmp-stock-lists]] (elenco completo simboli)
- Runbook: [[fmp-api-quickstart]]
- Synthesis: [[fmp-api-overview]]
- Implementazione: [[webapp-architecture-vi]] (SearchController)
