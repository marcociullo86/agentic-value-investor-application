---
id: fmp-forex
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, forex, currency, exchange-rate, platform-domain]
domain: platform
---
# FMP — Forex (stable)

^src: raw/fmp_docs.md §Forex — ^src: raw/fmp_docs.json sezione="Forex"

Sezione dell'API FMP stable con tassi di cambio valutari e storico forex.

---

## Endpoint principali

### 1. Forex List
- **Path**: `GET /stable/forex-list`
- **Response**: lista di coppie valutarie disponibili

### 2. Forex Quote
- **Path**: `GET /stable/quote` (con simbolo forex es. `EURUSD`)
- **Response**: tasso di cambio corrente

### 3. Historical Forex Prices
- **Path**: `GET /stable/historical-price-full`
- **Parametri**: `symbol*` (es. `EURUSD`, `GBPUSD`), `from`, `to`

---

## Note

Rilevante per il rule engine in presenza di titoli internazionali quotati in valuta estera. Non integrato nel MVP (focus su titoli USD-denominati). Utile per normalizzazione valutaria in future versioni.

---

## Cross-link

- Entity: [[fmp-api]]
- Synthesis: [[fmp-api-overview]]
