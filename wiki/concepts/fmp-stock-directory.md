---
type: concept
sources: ["raw/FMP_Docs_2_Stock_Directory.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, directory, symbols, etf, cik, exchanges]
---
# Stock Directory FMP

> La directory di FMP fornisce cataloghi completi e aggiornati di simboli, CIK, ETF attivi, variazioni di simbolo e tassonomie di exchange/settori/industrie/paesi.

## Dettaglio

### Catalogo Simboli
L'endpoint Company/Financial Statement Symbols List restituisce l'universo completo di strumenti per cui FMP dispone di dichiarazioni finanziarie. Non richiede parametri ed è utile per inizializzare database locali o pipeline di aggiornamento batch. [^src: raw/FMP_Docs_2_Stock_Directory.txt §Company / Financial Statement Symbols List API]

### CIK Database
La CIK List fornisce accesso paginato all'intero registro SEC. Il parametro `limit` ha un massimo di 10000 record per pagina, adatto per bulk download. [^src: raw/FMP_Docs_2_Stock_Directory.txt §CIK List API]

### Variazioni di Simbolo
Symbol Changes List monitora i cambiamenti di ticker dovuti a fusioni, acquisizioni e frazionamenti azionari. Il parametro `invalid` filtra i simboli non più attivi. [^src: raw/FMP_Docs_2_Stock_Directory.txt §Symbol Changes List API]

### ETF e Strumenti Attivi
L'endpoint ETF Symbol Search & Actively Trading List identifica l'universo di strumenti attualmente in negoziazione, inclusi gli ETF. Utile per validare simboli prima di interrogare endpoint dati. [^src: raw/FMP_Docs_2_Stock_Directory.txt §ETF Symbol Search API & Actively Trading List API]

### Tassonomie di Supporto
Available Exchanges, Sectors, Industries, Countries fornisce le enum dei valori accettati dagli endpoint di ricerca e screener, evitando errori di parametro per valori non supportati. [^src: raw/FMP_Docs_2_Stock_Directory.txt §Available Exchanges, Sectors, Industries, Countries APIs]

## Concetti correlati
[[fmp-search]]
[[fmp-api]]

## Pagine collegate
[[fmp-docs-2-stock-directory]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
