---
type: source
sources: ["raw/FMP_Docs_3_Company_Info.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, company, profile, market-cap]
---
# FMP Docs 3 — Company Information

> FMP fornisce endpoint completi per profili aziendali, note societarie, confronto tra pari, aziende delistate, conteggio dipendenti, market cap, float azionario e attività M&A.

## Contesto

La sezione Company Information aggrega dati strutturati sulle società quotate, incluse informazioni operative, struttura del capitale e eventi societari straordinari. [^src: raw/FMP_Docs_3_Company_Info.txt §Company Information]

## Endpoint

### Company Profile Data & Profile by CIK
Dati di profilo dettagliati: market cap, prezzo azionario, settore, industria e altro. Parametri: `symbol`* oppure `cik`* (stringa). [^src: raw/FMP_Docs_3_Company_Info.txt §Company Profile Data API & Profile by CIK API]

### Company Notes
Informazioni dettagliate sulle note emesse dalla società (USA). Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_3_Company_Info.txt §Company Notes API]

### Stock Peer Comparison
Identifica e confronta aziende nello stesso settore e fascia di capitalizzazione. Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_3_Company_Info.txt §Stock Peer Comparison API]

### Delisted Companies
Elenco delle aziende delistate dagli exchange USA. Parametri: `page` (numero), `limit` (numero). [^src: raw/FMP_Docs_3_Company_Info.txt §Delisted Companies API]

### Company Employee Count & Historical Count
Informazioni sulla forza lavoro e dati storici sul numero di dipendenti (USA). Parametri: `symbol`* (stringa), `limit` (numero). [^src: raw/FMP_Docs_3_Company_Info.txt §Company Employee Count & Historical Count API]

### Company Market Cap (Current, Batch, Historical)
Capitalizzazione di mercato attuale, batch e storica. Parametri: `symbol`* oppure `symbols`*; per storico: `limit`, `from`, `to`. [^src: raw/FMP_Docs_3_Company_Info.txt §Company Market Cap APIs]

### Company Share Float & Liquidity
Numero totale di azioni pubblicamente negoziate, free float e azioni outstanding. Parametri: `symbol`* (singolo), `limit` e `page` (all shares). [^src: raw/FMP_Docs_3_Company_Info.txt §Company Share Float & Liquidity API]

### Mergers & Acquisitions (Latest & Search)
Dati sulle attività di M&A. Parametri: `page`, `limit` (latest); `name`* (search). [^src: raw/FMP_Docs_3_Company_Info.txt §Mergers & Acquisitions APIs]

## Concetti correlati
[[fmp-company-info]]

## Pagine collegate
[[fmp-api]]
[[fmp-docs-1-auth-and-search]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
