---
id: fmp-cryptocurrency
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, crypto, cryptocurrency, platform-domain]
domain: platform
---
# FMP — Cryptocurrency (stable)

^src: raw/fmp_docs.md §Cryptocurrency — ^src: raw/fmp_docs.json sezione="Cryptocurrency"

Sezione dell'API FMP stable con quotazioni e storico prezzi delle principali criptovalute.

---

## Endpoint principali

### 1. Cryptocurrency List
- **Path**: `GET /stable/cryptocurrency-list`
- **Response**: lista di criptovalute disponibili

### 2. Crypto Quote
- **Path**: `GET /stable/quote` (con simbolo crypto es. `BTCUSD`)
- **Response**: quotazione corrente

### 3. Historical Crypto Prices
- **Path**: `GET /stable/historical-price-full`
- **Parametri**: `symbol*` (es. `BTCUSD`, `ETHUSD`), `from`, `to`

---

## Note

Non usata dal rule engine value investing (focus su equity tradizionale). Esclusa dalla watchlist MVP.

---

## Cross-link

- Entity: [[fmp-api]]
- Synthesis: [[fmp-api-overview]]
