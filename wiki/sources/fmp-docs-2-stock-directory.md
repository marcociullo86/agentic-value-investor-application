---
type: source
sources: ["raw/FMP_Docs_2_Stock_Directory.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, stock-directory, symbols]
---
# FMP Docs 2 — Stock Directory

> La sezione Stock Directory di FMP fornisce elenchi completi di simboli finanziari, numeri CIK, variazioni di simbolo, ETF attivi e lookup di exchange/settori/industrie/paesi.

## Contesto

Gli endpoint di directory consentono di esplorare l'intero catalogo di strumenti finanziari disponibili su FMP senza richiedere un simbolo specifico. [^src: raw/FMP_Docs_2_Stock_Directory.txt §Stock Directory]

## Endpoint

### Company / Financial Statement Symbols List
Elenco completo di simboli finanziari con dichiarazioni finanziarie disponibili. Nessun parametro obbligatorio. [^src: raw/FMP_Docs_2_Stock_Directory.txt §Company / Financial Statement Symbols List API]

### CIK List
Database completo di numeri CIK (USA). Parametri: `page` (numero, es. 0), `limit` (numero, max 10000). [^src: raw/FMP_Docs_2_Stock_Directory.txt §CIK List API]

### Symbol Changes List
Variazioni recenti di simboli azionari dovute a fusioni, acquisizioni o split. Parametri: `invalid` (stringa/booleano, default false), `limit`. [^src: raw/FMP_Docs_2_Stock_Directory.txt §Symbol Changes List API]

### ETF Symbol Search & Actively Trading List
Elenco di tutte le società e strumenti finanziari in fase di negoziazione attiva; ricerca per nome ETF. Nessun parametro obbligatorio documentato. [^src: raw/FMP_Docs_2_Stock_Directory.txt §ETF Symbol Search API & Actively Trading List API]

### Earnings Transcript List
Elenco dei trascritti di earnings call disponibili per le società. Nessun parametro obbligatorio documentato. [^src: raw/FMP_Docs_2_Stock_Directory.txt §Earnings Transcript List API]

### Available Exchanges, Sectors, Industries, Countries
Elenchi completi di exchange supportati, settori industriali, industrie e paesi in cui i simboli azionari sono disponibili. [^src: raw/FMP_Docs_2_Stock_Directory.txt §Available Exchanges, Sectors, Industries, Countries APIs]

## Concetti correlati
[[fmp-stock-directory]]

## Pagine collegate
[[fmp-api]]
[[fmp-docs-1-auth-and-search]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
