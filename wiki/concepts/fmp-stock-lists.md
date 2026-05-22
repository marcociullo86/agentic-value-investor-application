---
id: fmp-stock-lists
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, stock-list, symbols, etf, directory]
---
# FMP — Stock Lists (stable)

^src: raw/fmp_docs.md §Stock Lists — ^src: raw/fmp_docs.json sezione="Stock Lists"

Sezione dell'API FMP stable con i cataloghi di simboli disponibili, ETF, fondi e titoli attivi. Sostituisce il vecchio "Stock Directory" della v3.

---

## Endpoint principali

### 1. Stock List
- **Path**: `GET /stable/stock-list`
- **Parametri**: nessuno (restituisce tutti i simboli disponibili)
- **Response**: `[{symbol, name, price, exchange, exchangeShortName, type}]`
- **Uso**: lookup completo per popolare tabella `stocks` (DB seed iniziale)

### 2. Available Traded List
- **Path**: `GET /stable/available-traded`
- **Parametri**: nessuno
- **Response**: lista di simboli attivamente tradati
- **Uso**: filtrare i simboli con trading attivo (esclude OTC illiquidi)

### 3. ETF List
- **Path**: `GET /stable/etf-list`
- **Parametri**: nessuno
- **Response**: lista di ETF con symbol, name, exchange, currency

### 4. Actively Trading
- **Path**: `GET /stable/actively-trading`
- **Parametri**: nessuno (oppure `date`)
- **Response**: simboli con volume elevato nel giorno corrente

### 5. All Countries
- **Path**: `GET /stable/countries`
- **Response**: lista di paesi disponibili per filtro screener

### 6. Exchange Symbols List
- **Path**: `GET /stable/exchange-symbols`
- **Parametri**: `exchange*` (es. `NASDAQ`)
- **Response**: tutti i simboli di un exchange specifico

---

## Note sulla stable vs v3

- La stable API rimuove simboli duplicati e rinominazioni presenti in v3 (`exchange-variants` per tracking multi-listing).
- I simboli restituiti da `stock-list` corrispondono ai ticker validi per gli altri endpoint stable.

---

## Uso nel progetto

Il DB seed della tabella `stocks` puo' essere popolato da `/stable/stock-list`. I filtri screener (`company-screener`) operano sullo stesso universo di simboli. L'endpoint `etf-list` e' escluso dal rule engine value investing (focus su equity).

---

## Cross-link

- Entity: [[fmp-api]]
- Ricerca simboli: [[fmp-company-search]] (search-symbol, search-name)
- Screener: [[fmp-company-information]] (company-screener)
- Synthesis: [[fmp-api-overview]]
