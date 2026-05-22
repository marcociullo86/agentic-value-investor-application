---
id: fmp-commodities
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, commodities, gold, oil]
---
# FMP — Commodities (stable)

^src: raw/fmp_docs.md §Commodities — ^src: raw/fmp_docs.json sezione="Commodities"

Sezione dell'API FMP stable con quotazioni e storico prezzi delle principali materie prime.

---

## Endpoint principali

### 1. Commodities List
- **Path**: `GET /stable/commodities-list`
- **Response**: lista di commodity disponibili (simboli, nomi)

### 2. Commodity Quote
- **Path**: `GET /stable/quote` (con simbolo commodity es. `GCUSD` per oro)
- **Response**: quotazione commodity corrente

### 3. Historical Commodity Prices
- **Path**: `GET /stable/historical-price-full`
- **Parametri**: `symbol*` (es. `GCUSD`, `CLUSD`), `from`, `to`
- **Response**: OHLCV storico commodity

---

## Note

Le commodity piu' monitorate per analisi macro value investing:
- `GCUSD` — Oro (gold)
- `CLUSD` — Petrolio WTI (crude oil)
- `SIUSD` — Argento (silver)

Non usate direttamente dal rule engine MVP.

---

## Cross-link

- Entity: [[fmp-api]]
- Synthesis: [[fmp-api-overview]]
