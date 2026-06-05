---
id: fmp-market-performance
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, market, performance, sector, gainers, losers, platform-domain]
domain: platform
---
# FMP — Market Performance (stable)

^src: raw/fmp_docs.md §Market Performance — ^src: raw/fmp_docs.json sezione="Market Performance"

Sezione dell'API FMP stable con dati di performance di mercato aggregati: settori, gainers, losers, volume, indici.

---

## Endpoint principali

### 1. Sector Performance
- **Path**: `GET /stable/sector-performance`
- **Response**: performance percentuale per settore GICS nel giorno corrente
- **Uso**: dashboard panoramica mercato

### 2. Historical Sector Performance
- **Path**: `GET /stable/historical-sector-performance`
- **Parametri**: `limit`
- **Response**: serie storica delle performance settoriali

### 3. Biggest Gainers
- **Path**: `GET /stable/biggest-gainers`
- **Response**: top N titoli per variazione percentuale positiva nel giorno

### 4. Biggest Losers
- **Path**: `GET /stable/biggest-losers`
- **Response**: top N titoli per variazione percentuale negativa nel giorno

### 5. Most Active
- **Path**: `GET /stable/most-active`
- **Response**: titoli con maggiore volume di trading nel giorno

### 6. Market Hours
- **Path**: `GET /stable/market-hours`
- **Response**: orari apertura/chiusura dei principali mercati mondiali

---

## Uso nel progetto

Non integrata nel rule engine MVP. Utile per dashboard widget di stato mercato (feature futura). Il filtro per settore nel company-screener usa la stessa tassonomia GICS.

---

## Cross-link

- Entity: [[fmp-api]]
- Screener settori: [[fmp-company-information]] (company-screener con parametro `sector`)
- Synthesis: [[fmp-api-overview]]
