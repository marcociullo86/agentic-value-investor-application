---
id: fmp-quotes-stable
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, quotes, realtime, historical, price]
---
# FMP — Quotes & Prices (stable)

^src: raw/fmp_docs.md §Quotes — ^src: raw/fmp_docs.json sezione="Quotes"

Sezione dell'API FMP stable con quotazioni in tempo reale, batch, premarket/aftermarket e storico prezzi OHLCV.

---

## Endpoint principali

### 1. Quote
- **Path**: `GET /stable/quote`
- **Parametri**: `symbol*` (string, es. `AAPL`)
- **Response campi chiave**:
  - `symbol`, `name`, `price`, `changesPercentage`, `change`
  - `dayLow`, `dayHigh`, `yearHigh`, `yearLow`
  - `marketCap`, `priceAvg50`, `priceAvg200`
  - `volume`, `avgVolume`
  - `open`, `previousClose`
  - `eps`, `pe`
  - `earningsAnnouncement`, `sharesOutstanding`
  - `timestamp`

### 2. Quote Short
- **Path**: `GET /stable/quote-short`
- **Parametri**: `symbol*`
- **Response**: `[{symbol, price, volume, open, previousClose, change, changesPercentage}]`
- **Uso**: versione ridotta per dashboard — riduce payload quando serve solo il prezzo

### 3. Batch Quote
- **Path**: `GET /stable/batch-quote`
- **Parametri**: `symbol*` (comma-separated, es. `AAPL,MSFT,GOOGL`)
- **Response**: array di quote complete
- **Uso**: watchlist multipla — una sola chiamata per N ticker

### 4. Historical Price Full (OHLCV)
- **Path**: `GET /stable/historical-price-full`
- **Parametri**: `symbol*`, `from` (YYYY-MM-DD), `to` (YYYY-MM-DD), `serietype` (`line` | `bar`)
- **Response**: `{symbol, historical: [{date, open, high, low, close, adjClose, volume, unadjustedVolume, change, changePercent, vwap, label, changeOverTime}]}`
- **Uso**: grafici storici (TSK-024 HistoricalChart, US-015)

### 5. Stock Price Change
- **Path**: `GET /stable/stock-price-change`
- **Parametri**: `symbol*`
- **Response**: variazioni su 1d, 5d, 1M, 3M, 6M, ytd, 1Y, 3Y, 5Y, 10Y, max

### 6. After-Hours Price
- **Path**: `GET /stable/after-hours-price`
- **Parametri**: `symbol*`

### 7. Pre-Market Price
- **Path**: `GET /stable/pre-market-price`
- **Parametri**: `symbol*`

---

## Uso nel progetto

Il prezzo corrente per MarginOfSafetyEvaluator viene preferibilmente da `profile.price` (FmpCacheService, TTL 1h) piuttosto che da `quote`, per coerenza con i dati di profilo aziendale. Il `quote` endpoint puo' essere usato come aggiornamento real-time in scenari non-cache.

Lo storico prezzi (`historical-price-full`) alimenta il componente HistoricalChart (TSK-024, US-015, Recharts).

---

## Cross-link

- Entity: [[fmp-api]]
- Profilo con prezzo: [[fmp-company-information]] (profile.price — preferito per MoS)
- Watchlist FE: [[webapp-architecture-vi]] (WatchlistController, WatchlistTable)
- Runbook: [[fmp-api-quickstart]]
- Synthesis: [[fmp-api-overview]]
