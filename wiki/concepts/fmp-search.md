---
type: concept
sources: ["raw/FMP_Docs_1_Auth_and_Search.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, search, ticker, screener, cik, cusip, isin]
---
# Ricerca Titoli FMP

> FMP offre sei modalità di ricerca per identificare strumenti finanziari: per simbolo, per nome azienda, per CIK, per CUSIP/ISIN, tramite screener multidimensionale e per varianti di exchange.

## Dettaglio

### Ricerca per Simbolo e per Nome
Gli endpoint Stock Symbol Search e Company Name Search consentono lookup testuale rapido. Il parametro `exchange` permette di filtrare per mercato specifico (es. NASDAQ). [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Company Search]

### Ricerca per Identificatori Regolatori
- **CIK**: Central Index Key assegnato dalla SEC agli emittenti USA. Lookup diretto tramite ID numerico (es. `320193` per Apple). [^src: raw/FMP_Docs_1_Auth_and_Search.txt §CIK API]
- **CUSIP / ISIN**: Standard internazionali di identificazione degli strumenti finanziari. CUSIP è usato principalmente nel mercato nordamericano; ISIN è lo standard globale ISO 6166. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §CUSIP API & ISIN API]

### Stock Screener
Strumento di filtro multidimensionale che consente di selezionare titoli per capitalizzazione, settore, industria, beta, prezzo, dividendo, volume, exchange, paese, e tipologia (ETF, fondo, attivamente negoziato). [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Stock Screener API]

### Exchange Variants
Identifica su quali exchange globali è quotato un determinato simbolo. Utile per titoli dual-listed o con depositary receipt. [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Exchange Variants API]

## Concetti correlati
[[fmp-auth]]
[[fmp-stock-directory]]

## Pagine collegate
[[fmp-docs-1-auth-and-search]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
