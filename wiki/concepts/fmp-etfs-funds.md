---
id: fmp-etfs-funds
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, etf, mutual-fund, holdings, platform-domain]
domain: platform
---
# FMP — ETFs & Mutual Funds (stable)

^src: raw/fmp_docs.md §ETFs & Mutual Funds — ^src: raw/fmp_docs.json sezione="ETFs & Mutual Funds"

Sezione dell'API FMP stable con dati su ETF e fondi comuni: informazioni, holdings, performance.

---

## Endpoint principali

### 1. ETF Info
- **Path**: `GET /stable/etf-info`
- **Parametri**: `symbol*`
- **Response**: informazioni ETF (gestore, AUM, TER, benchmark, asset class)

### 2. ETF Holdings
- **Path**: `GET /stable/etf-holdings`
- **Parametri**: `symbol*`, `date`
- **Response**: composizione del portafoglio ETF con pesi percentuali

### 3. ETF Country Weighting
- **Path**: `GET /stable/etf-country-weighting`
- **Parametri**: `symbol*`

### 4. ETF Sector Weighting
- **Path**: `GET /stable/etf-sector-weighting`
- **Parametri**: `symbol*`

### 5. Mutual Fund List
- **Path**: `GET /stable/mutual-fund-list`
- **Response**: lista dei fondi comuni disponibili

---

## Note

Non integrati nel rule engine value investing MVP (focus su stock picking individuale). Utile per il profilo "Investitore Difensivo" Graham (portafoglio ETF diversificato) in future feature.

---

## Cross-link

- Entity: [[fmp-api]]
- Investitore difensivo: [[defensive-vs-enterprising-investor]]
- Synthesis: [[fmp-api-overview]]
