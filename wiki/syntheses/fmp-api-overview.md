---
id: fmp-api-overview
type: synthesis
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, api, overview, synthesis, platform-domain]
domain: platform
---
# FMP API Stable — Panoramica

^src: raw/fmp_docs.md (intero documento, 263 endpoint) — ^src: raw/fmp_docs.json (versione strutturata)

Sintesi cross-source dell'API Financial Modeling Prep versione stable. La v3 e' stata dismessa il **2025-08-31**. Tutti gli endpoint usano `https://financialmodelingprep.com/stable/`.

---

## Dati chiave

| Attributo | Valore |
|-----------|--------|
| Provider | Financial Modeling Prep (FMP) |
| Base URL | `https://financialmodelingprep.com/stable/` |
| Endpoint totali | **263** |
| Autenticazione | API key (header `apikey:` o query `?apikey=`) |
| Documentazione | https://site.financialmodelingprep.com/developer/docs |
| v3 EOL | 2025-08-31 |

---

## Organizzazione per sezione

| # | Sezione | Endpoint tipo | Relevanza progetto |
|---|---------|---------------|-------------------|
| 1 | **Company Search** | search-symbol, search-name, search-cik, search-cusip, search-isin | CRITICA (SearchService) |
| 2 | **Company Information** | profile, company-screener, market-cap, stock-peers | CRITICA (MoS, Screener) |
| 3 | **Financial Statements** | income-statement, balance-sheet, cash-flow, TTM | CRITICA (7 rules + DCF) |
| 4 | **Key Metrics & Ratios** | key-metrics, ratios, financial-growth, DCF | CRITICA (ROE, ROIC, BVPS) |
| 5 | **Stock Lists** | stock-list, etf-list, available-traded | MEDIA (DB seed) |
| 6 | **Quotes** | quote, batch-quote, historical-price-full, historical-price-eod/full | ALTA (HistoricalChart + EP-011 price action) |
| 7 | **Executives & Insiders** | key-executives, insider-trading | BASSA (analisi qualitativa) |
| 8 | **News & Media** | stock-news, fmp-articles, press-releases | ALTA (EP-011: sentiment classifier via getStockNews) |
| 9 | **Market Performance** | sector-performance, biggest-gainers | BASSA (dashboard) |
| 10 | **Commodities** | quote commodity, historical | OUT OF SCOPE MVP |
| 11 | **Cryptocurrency** | crypto-list, crypto-quote | OUT OF SCOPE MVP |
| 12 | **Forex** | forex-list, forex-quote | OUT OF SCOPE MVP |
| 13 | **ETFs & Mutual Funds** | etf-info, etf-holdings | OUT OF SCOPE MVP |

---

## Pattern di autenticazione

```
GET https://financialmodelingprep.com/stable/income-statement?symbol=AAPL&period=annual&limit=10
apikey: YOUR_API_KEY
```

Oppure inline: `...?symbol=AAPL&period=annual&limit=10&apikey=YOUR_API_KEY`

---

## Convention dati critiche

1. **Ordine array**: newest-first. `response[0]` = dato piu' recente.
2. **`capitalExpenditure`**: NEGATIVO (cash outflow FMP convention). Usare `abs()`.
3. **`period` parameter**: `annual` o `quarter` (non `FY`/`Q` come in v3).
4. **`netIncomePerShare` vs `eps`**: non equivalenti. Usare `income-statement.eps` per Graham Number.
5. **Stable vs v3**: la stable rimuove duplicati e simboli rinominati.
6. **`sec-filings-search/symbol`**: `from`/`to` OBBLIGATORI (senza → 400) e nessun filtro `formType` server-side → una chiamata per formType + filtro client-side. Dettaglio in [[fmp-api]] §Discovery filing SEC.

---

## Endpoint critici (12 endpoint integrati)

| Priority | Endpoint | Path stable | Sezione | Stato |
|----------|----------|-------------|---------|-------|
| P0 | Search symbol | `/stable/search-symbol` | Company Search | Implementato (R1.0) |
| P0 | Company profile | `/stable/profile` | Company Information | Implementato (R1.0) |
| P0 | Income statement | `/stable/income-statement` | Financial Statements | Implementato (R1.0) |
| P0 | Balance sheet | `/stable/balance-sheet-statement` | Financial Statements | Implementato (R1.0) |
| P0 | Cash flow | `/stable/cash-flow-statement` | Financial Statements | Implementato (R1.0) |
| P0 | Key metrics | `/stable/key-metrics` | Key Metrics & Ratios | Implementato (R1.0) |
| P1 | Company screener | `/stable/company-screener` | Company Information | Implementato (EP-001) |
| P1 | Historical prices | `/stable/historical-price-full` | Quotes | Implementato (R1.0) |
| P1 | Dividends | `/stable/dividends` | Earnings, Dividends | Implementato (EP-010, TSK-083) |
| P1 | SEC filings search | `/stable/sec-filings-search/symbol` | Sec Filings | Implementato (EP-011, TSK-094) |
| P1 | Stock news | `/stable/news/stock` | News & Media | Implementato (EP-011, TSK-108) |
| P1 | EOD prices | `/stable/historical-price-eod/full` | Quotes | Implementato (EP-011, TSK-112) |

---

## Migrazioni da v3 (path changes)

| Endpoint | v3 (deprecato) | stable |
|----------|----------------|--------|
| Income statement | `/api/v3/income-statement/{symbol}` | `/stable/income-statement?symbol={symbol}` |
| Balance sheet | `/api/v3/balance-sheet-statement/{symbol}` | `/stable/balance-sheet-statement?symbol={symbol}` |
| Cash flow | `/api/v3/cash-flow-statement/{symbol}` | `/stable/cash-flow-statement?symbol={symbol}` |
| Key metrics | `/api/v3/key-metrics/{symbol}` | `/stable/key-metrics?symbol={symbol}` |
| Profile | `/api/v3/profile/{symbol}` | `/stable/profile?symbol={symbol}` |
| Quote | `/api/v3/quote/{symbol}` | `/stable/quote?symbol={symbol}` |
| Search | `/api/v3/search?query=` | `/stable/search-symbol?query=` |
| Screener | `/api/v3/stock-screener` | `/stable/company-screener` |

**Nota**: il path di query cambia da `/{symbol}` (path variable) a `?symbol={symbol}` (query parameter) per la maggior parte degli endpoint.

---

## Gap noti

- Rate limiting e quote giornaliere non documentati: vedi gap `fmp-stable-rate-limiting` in [[wiki/gaps.md]].
- Stime analisti (consensus EPS, price target): non trovate nella sezione "News & Media" stable; possibile assenza o sezione separata non documentata nei raw. Vedi gap `fmp-stable-analyst-estimates`.

---

## Pagine concept per sezione

- [[fmp-company-search]] — Company Search
- [[fmp-company-information]] — Company Information
- [[fmp-financial-statements-stable]] — Financial Statements
- [[fmp-key-metrics-ratios]] — Key Metrics & Ratios
- [[fmp-stock-lists]] — Stock Lists
- [[fmp-quotes-stable]] — Quotes
- [[fmp-executives-insiders]] — Executives & Insiders
- [[fmp-news-media]] — News & Media
- [[fmp-market-performance]] — Market Performance
- [[fmp-commodities]] — Commodities
- [[fmp-cryptocurrency]] — Cryptocurrency
- [[fmp-forex]] — Forex
- [[fmp-etfs-funds]] — ETFs & Mutual Funds

---

## Cross-link

- Entity: [[fmp-api]]
- Source: [[fmp-docs]]
- Integrazione: [[value-investing-fmp-integration]]
- Runbook: [[fmp-api-quickstart]]
