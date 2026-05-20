---
type: concept
sources: ["raw/FMP_Docs_6_Quotes_and_Prices.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, quotes, realtime, premarket, aftermarket, crypto, forex, batch]
---
# Quotazioni e Prezzi FMP

> FMP fornisce quotazioni in tempo reale per azioni, ETF, crypto, forex e indici, con supporto per sessioni estese (premarket/aftermarket) e recupero batch di simboli multipli.

## Dettaglio

### Tipologie di Quotazione
FMP distingue quattro modalità di quote per i titoli azionari: [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Quotes]

| Tipo | Descrizione |
|------|-------------|
| Real-time | Prezzo corrente durante la sessione |
| Short | Versione ridotta con solo i campi essenziali |
| Premarket | Prezzi prima dell'apertura del mercato |
| Aftermarket | Prezzi dopo la chiusura del mercato |

### Variazione di Prezzo
Stock Price Change traccia le variazioni percentuali e assolute su periodi multipli (giornaliero, settimanale, mensile, YTD, etc.) in tempo reale. [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Stock Price Change API]

### Batch Quote
Permette di recuperare quotazioni per simboli multipli in una singola chiamata API, separati da virgola (es. `AAPL,MSFT`). Riduce il numero di richieste necessarie per portafogli o watchlist. [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Batch Quote APIs]

### Asset Alternativi
L'endpoint Crypto, Forex, Index Quotes estende la copertura oltre le azioni tradizionali. Il parametro `short` (booleano) abilita la versione ridotta della risposta. [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Crypto, Forex, Index Quotes APIs]

## Concetti correlati
[[fmp-api]]
[[fmp-search]]

## Pagine collegate
[[fmp-docs-6-quotes-and-prices]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
