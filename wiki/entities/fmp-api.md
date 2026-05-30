---
id: fmp-api
type: entity
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-20
updated: 2026-05-30
tags: [fmp, api, stable, entity, provider, financial-data, operations]
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
| `/stable/dividends` | Storico dividendi per continuità Graham (20y) | Earnings, Dividends, Splits |
| `/stable/sec-filings-search/symbol` | Discovery filing SEC (10-K, 10-Q) per ticker — `from`/`to` obbligatori | Sec Filings |
| `/stable/news/stock` | News per ticker, sentiment classifier (90gg) | News & Media |
| `/stable/historical-price-eod/full` | Storico EOD per price action analyzer (52w) | Quotes |

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

---

## Documentazione operativa (US-029 / TSK-068 + US-031 / TSK-072)

Runbook [[fmp-api-quickstart]] espone sezioni esplicite per rate limiting, URL base e errori HTTP. Stato ingest e migrazione adapter:

| Tema | Stato wiki | Gap |
|------|------------|-----|
| Rate limit / 429 | Non specificato dalla doc stable | `fmp-stable-rate-limiting` aperto |
| URL base (path) | Migrato a `/stable/` (verificato) | `fmp-endpoint-base-urls` chiuso |
| Codici errore HTTP / body | Documentazione laterale ricostruita da osservazioni | `fmp-stable-error-codes` aperto |
| Stime analisti (consensus EPS, price target) | Non trovate nella sezione stable | `fmp-stable-analyst-estimates` aperto |

Default throttling e path MVP in configurazione applicativa: ADR-016 (non citazione provider). Dettaglio: [[fmp-api-quickstart]] § Rate limiting, § URL base, § Errori HTTP, § Limitazioni documentazione.

## Discovery filing SEC — quirk operativi (`/stable/sec-filings-search/symbol`)

Comportamento reale verificato sul campo (ticker TTD, maggio 2026), non documentato nella doc ufficiale ma load-bearing per l'integrazione EP-011:

- **`from`/`to` sono OBBLIGATORI**: senza finestra temporale l'endpoint risponde `400 BAD_REQUEST`. Il client applica `to = oggi`, `from = oggi - lookbackMonths` con `lookbackMonths=15` (15 mesi indietro: copre l'ultimo 10-K annuale + gli ultimi 10-Q, con margine per ritardi di deposito).
- **Nessun filtro `formType` server-side**: l'endpoint ritorna TUTTI i form type (Form-4, 8-K, SC 13G, 10-K, 10-Q...) ordinati DESC per `filingDate`; passare `formType` è innocuo (forward-compatible) ma il filtro 10-K/10-Q va applicato **client-side**. Conseguenza pratica: i 10-K/10-Q (pochi) sono annegati tra decine di Form-4/8-K più recenti → un `limit` basso (es. 10) li escluderebbe dalla pagina (root cause del bug "No SEC filings"). Si usa quindi un page-limit ampio (1000) per chiamata.
- **Una chiamata per formType**: per ogni form type richiesto (10-K, 10-Q) si emette una chiamata distinta al `/symbol` con quel `formType` in querystring + filtro client-side, poi si fa union deduplicata per link canonico, ordinata DESC per `filingDate` e troncata a `limit` (cap totale).
- **L'endpoint gemello `/sec-filings-search/form-type` IGNORA il `symbol`** (ritorna i filing di TUTTE le aziende) → inutilizzabile per ticker singolo.
- **Parsing data tollerante**: `filingDate` può arrivare come `yyyy-MM-dd` o come datetime `yyyy-MM-dd HH:mm:ss` (o ISO `T`); il downloader isola la sola parte data prima del parse per evitare il fallback a EPOCH.

Error policy per chiamata: `429 → FmpUnavailableException(429)` (route Resilience4j), `5xx → FmpUnavailableException(status)`, `4xx` non-429 → trattato come "nessun filing per quel tipo" (emptyList). [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/FmpAdapterRestClient.kt §getSecFilings] [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/service/Filing10KQDownloaderService.kt §parseFilingDate]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-002 (R1.0 done): US-004, US-005, US-006
- EP-002 (R1.1): US-031 (TSK-072 — migrazione adapter v3 → /stable)
- EP-009 (R1.1): US-029, US-030
- EP-010 (done): US-037 (TSK-083 — getDividendHistory adapter + DividendContinuityRule)
- EP-011 (in progress): US-039 (TSK-094 — getSecFilings adapter), US-042 (TSK-108 — getStockNews adapter), US-043 (TSK-112 — getHistoricalEodPrices adapter)
