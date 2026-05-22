---
id: fmp-api
type: entity
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, api, stable, entity, provider]
---
# Financial Modeling Prep (FMP) — API Stable

^src: raw/fmp_docs.md §Autorizzazione — ^src: raw/fmp_docs.json (263 record, campo `endpoint_url`)

**Financial Modeling Prep** e' un provider di dati finanziari via REST API. La versione v3 e' stata dismessa il **2025-08-31**. La nuova base URL e' `https://financialmodelingprep.com/stable/`.

---

## Identificazione

| Attributo | Valore |
|-----------|--------|
| Nome | Financial Modeling Prep (FMP) |
| Sito | https://financialmodelingprep.com |
| Documentazione | https://site.financialmodelingprep.com/developer/docs |
| Base URL stable | `https://financialmodelingprep.com/stable/` |
| API v3 (deprecata) | `https://financialmodelingprep.com/api/v3/` (EOL 2025-08-31) |
| Totale endpoint (stable) | **263** |

---

## Autenticazione

^src: raw/fmp_docs.md §Autorizzazione (riga 8-13)

Due modalita' equivalenti:

```
# Via header HTTP
apikey: YOUR_API_KEY

# Via query string
https://financialmodelingprep.com/stable/<endpoint>?apikey=YOUR_API_KEY
```

La API key si ottiene registrandosi su https://site.financialmodelingprep.com/developer/docs.

**Nota sui rate limit**: la documentazione ufficiale stable non specifica esplicitamente i limiti di frequenza (richieste/minuto, quota giornaliera). Vedi gap `fmp-stable-rate-limiting` in [[gaps]].

---

## Organizzazione per sezione

| Sezione | Endpoint rappresentativi | Pagina concept |
|---------|--------------------------|----------------|
| Company Search | search-symbol, search-name, search-cik, search-cusip, search-isin | [[fmp-company-search]] |
| Company Information | profile, company-screener, market-cap, stock-peers | [[fmp-company-information]] |
| Financial Statements | income-statement, balance-sheet-statement, cash-flow-statement, TTM | [[fmp-financial-statements-stable]] |
| Key Metrics & Ratios | key-metrics, ratios, financial-growth, discounted-cash-flow | [[fmp-key-metrics-ratios]] |
| Stock Lists | stock-list, available-traded, etf-list, actively-trading | [[fmp-stock-lists]] |
| Quotes | quote, quote-short, batch-quote, historical-price-full | [[fmp-quotes-stable]] |
| Executives & Insiders | key-executives, insider-trading, executive-compensation | [[fmp-executives-insiders]] |
| News & Media | fmp-articles, stock-news, general-news | [[fmp-news-media]] |
| Market Performance | sector-performance, biggest-gainers, biggest-losers | [[fmp-market-performance]] |
| Commodities | commodities-list, historical-price-full (commodity) | [[fmp-commodities]] |
| Cryptocurrency | cryptocurrency-list, crypto-quote, crypto-historical | [[fmp-cryptocurrency]] |
| Forex | forex-list, forex-quote, forex-historical | [[fmp-forex]] |
| ETFs & Mutual Funds | etf-info, etf-holdings, mutual-fund-list | [[fmp-etfs-funds]] |

---

## Endpoint critici per il Rule Engine

Il rule engine value investing usa questi endpoint:

| Endpoint | Scopo | Sezione |
|----------|-------|---------|
| `/stable/search-symbol` | Ricerca ticker per simbolo | Company Search |
| `/stable/search-name` | Ricerca ticker per nome | Company Search |
| `/stable/company-screener` | Screener parametrico (sector, market-cap) | Company Information |
| `/stable/profile` | Profilo aziendale + prezzo corrente | Company Information |
| `/stable/income-statement` | ROE, net margin, EPS (10 anni) | Financial Statements |
| `/stable/balance-sheet-statement` | Current ratio, debt (10 anni) | Financial Statements |
| `/stable/cash-flow-statement` | CapEx, OCF, Owner Earnings (10 anni) | Financial Statements |
| `/stable/key-metrics` | ROIC, BVPS, Graham Number inputs | Key Metrics & Ratios |

---

## Architettura di integrazione (ADR-004 invariante)

L'architettura lato BE **non cambia** con la migrazione v3 -> stable:
- Adapter pattern: `FmpAdapter` interface + `FmpAdapterRestClient` implementazione
- Cache 24h su `fmp_financial_snapshot` (TTL profile 1h)
- Resilience4j: Circuit Breaker + Retry + Rate Limiter + Bulkhead
- `fmp_api_event_log` per audit eventi (429, 5xx, circuit open, fallback stale)

Cambiano solo: path URL (da `/api/v3/` a `/stable/`), parametri e DTO.

Vedi [[webapp-architecture-vi]] per dettagli implementativi e [[value-investing-fmp-integration]] per il mapping metriche.

---

## Fonti

- Source narrativa: [[fmp-docs]] (`raw/fmp_docs.md`)
- Source strutturata: [[fmp-docs]] (`raw/fmp_docs.json`)
- Runbook operativo: [[fmp-api-quickstart]]
- Panoramica: [[fmp-api-overview]]
