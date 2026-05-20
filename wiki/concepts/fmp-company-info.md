---
type: concept
sources: ["raw/FMP_Docs_3_Company_Info.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, company, profile, market-cap, float, m-and-a]
---
# Informazioni Aziendali FMP

> FMP aggrega dati fondamentali sulle società quotate: profilo, capitalizzazione, struttura azionaria, dipendenti, eventi M&A e confronto tra pari.

## Dettaglio

### Profilo Aziendale
L'endpoint Company Profile Data restituisce snapshot strutturato della società: market cap, prezzo corrente, settore, industria, paese, sito web, descrizione e altro. Disponibile per lookup sia per `symbol` che per `cik`. [^src: raw/FMP_Docs_3_Company_Info.txt §Company Profile Data API & Profile by CIK API]

### Struttura del Capitale
- **Market Cap**: disponibile in tre forme (corrente, batch su lista di simboli, storico con range date). [^src: raw/FMP_Docs_3_Company_Info.txt §Company Market Cap APIs]
- **Share Float**: fornisce free float, azioni outstanding e totale azioni pubblicamente negoziate. [^src: raw/FMP_Docs_3_Company_Info.txt §Company Share Float & Liquidity API]

### Dati Operativi
- **Employee Count**: numero di dipendenti corrente e storico. Utile per analisi di efficienza del lavoro e trend di crescita. [^src: raw/FMP_Docs_3_Company_Info.txt §Company Employee Count & Historical Count API]
- **Company Notes**: note emesse dalla società (strumenti di debito); riservato al mercato USA. [^src: raw/FMP_Docs_3_Company_Info.txt §Company Notes API]

### Analisi Competitiva
- **Stock Peer Comparison**: identifica automaticamente i comparables per settore e fascia di capitalizzazione. [^src: raw/FMP_Docs_3_Company_Info.txt §Stock Peer Comparison API]
- **Delisted Companies**: utile per analisi storica di società non più quotate. [^src: raw/FMP_Docs_3_Company_Info.txt §Delisted Companies API]

### M&A
Latest e Search M&A forniscono dati sulle operazioni di fusione e acquisizione, con ricerca per nome della transazione. [^src: raw/FMP_Docs_3_Company_Info.txt §Mergers & Acquisitions APIs]

## Concetti correlati
[[fmp-financial-statements]]
[[fmp-executives]]

## Pagine collegate
[[fmp-docs-3-company-info]]
[[fmp-api]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
