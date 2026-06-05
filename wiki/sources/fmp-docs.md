---
id: fmp-docs
type: source
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, api, stable, source, platform-domain]
domain: platform
---
# Source: FMP Stable API Documentation

^src: raw/fmp_docs.md (versione narrativa, ~500 KB) — ^src: raw/fmp_docs.json (versione strutturata, 263 record)

Sorgente originale: https://site.financialmodelingprep.com/developer/docs

Ingestito il 2026-05-22 in sostituzione degli 8 vecchi `FMP_Docs_*.txt` (basati su API v3 dismessa il 2025-08-31).

---

## Metadati delle fonti

| File | Formato | Endpoint | Note |
|------|---------|----------|------|
| `raw/fmp_docs.md` | Markdown narrativo | 263 | Organizzato per sezione, con parametri e response example |
| `raw/fmp_docs.json` | JSON strutturato | 263 | Campi: `section`, `title`, `url`, `endpoint_url`, `short_desc`, `long_desc`, `features`, `parameters`, `response_example` |

---

## Autenticazione (da raw/fmp_docs.md, sezione "Autorizzazione")

Tutte le richieste devono includere la API key in uno dei due modi:

```
Header:       apikey: YOUR_API_KEY
Query string: ?apikey=YOUR_API_KEY
              &apikey=YOUR_API_KEY  (se altri parametri gia' presenti)
```

Base URL universale: `https://financialmodelingprep.com/stable/`

---

## Struttura delle sezioni (263 endpoint totali)

Le sezioni identificate nel campo `section` del JSON:

| # | Sezione | Slug pagina concept |
|---|---------|---------------------|
| 1 | Company Search | [[fmp-company-search]] |
| 2 | Company Information | [[fmp-company-information]] |
| 3 | Financial Statements | [[fmp-financial-statements-stable]] |
| 4 | Key Metrics & Financial Ratios | [[fmp-key-metrics-ratios]] |
| 5 | Stock Lists | [[fmp-stock-lists]] |
| 6 | Quotes | [[fmp-quotes-stable]] |
| 7 | Executives & Insiders | [[fmp-executives-insiders]] |
| 8 | News & Media | [[fmp-news-media]] |
| 9 | Market Performance | [[fmp-market-performance]] |
| 10 | Commodities | [[fmp-commodities]] |
| 11 | Cryptocurrency | [[fmp-cryptocurrency]] |
| 12 | Forex | [[fmp-forex]] |
| 13 | ETFs & Mutual Funds | [[fmp-etfs-funds]] |
| 14+ | Altre sezioni (Calendar, Economics, ecc.) | incluse nelle pagine concept corrispondenti |

---

## Indice degli endpoint per sezione (selezionati)

### Company Search (6 endpoint)
- `/stable/search-symbol` — ricerca per simbolo ticker
- `/stable/search-name` — ricerca per nome azienda
- `/stable/search-cik` — ricerca per CIK SEC
- `/stable/search-cusip` — ricerca per CUSIP
- `/stable/search-isin` — ricerca per ISIN
- `/stable/exchange-variants` — varianti di listing per simbolo

### Company Information
- `/stable/profile` — profilo completo azienda (settore, market cap, CEO, exchange)
- `/stable/company-notes` — note aziendali
- `/stable/stock-peers` — peer group
- `/stable/market-cap` — market cap storico
- `/stable/company-screener` — screener con filtri multipli

### Financial Statements
- `/stable/income-statement` — conto economico (annuale/trimestrale)
- `/stable/balance-sheet-statement` — stato patrimoniale
- `/stable/cash-flow-statement` — rendiconto finanziario
- `/stable/income-statement-ttm` — TTM income statement

### Key Metrics & Financial Ratios
- `/stable/key-metrics` — ROE, ROIC, P/E, EV/EBITDA, BVPS, ecc.
- `/stable/ratios` — ratios finanziari
- `/stable/financial-growth` — crescita YoY
- `/stable/discounted-cash-flow` — DCF value
- `/stable/advanced-discounted-cash-flow` — DCF avanzato

### Quotes
- `/stable/quote` — quotazione in tempo reale per simbolo
- `/stable/quote-short` — quotazione ridotta (price, volume)
- `/stable/batch-quote` — batch di quotazioni
- `/stable/historical-price-full` — storico prezzi OHLCV

---

## Cross-link

- Entity: [[fmp-api]]
- Concepts: [[fmp-company-search]], [[fmp-company-information]], [[fmp-financial-statements-stable]], [[fmp-key-metrics-ratios]], [[fmp-stock-lists]], [[fmp-quotes-stable]], [[fmp-executives-insiders]], [[fmp-news-media]]
- Syntheses: [[fmp-api-overview]], [[value-investing-fmp-integration]]
- Runbook: [[fmp-api-quickstart]]
