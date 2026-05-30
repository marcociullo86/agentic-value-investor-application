---
type: runbook
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-20
updated: 2026-05-23
tags: [fmp, quickstart, runbook, integration, rate-limit, errors, us-029, us-031, stable]
---
# Quickstart — Integrazione FMP API (`/stable`)

> Procedura minima per autenticarsi, cercare un simbolo, recuperare quotazioni e rendiconti tramite FMP API. Sezioni operative US-029 (rate limit, URL base, errori HTTP) con citazioni `raw/` o gap espliciti.
>
> **Nota migrazione (US-031 / TSK-072, 2026-05-22):** la base URL e' passata da `https://financialmodelingprep.com/api/v3` (deprecata 2025-08-31) a `https://financialmodelingprep.com/stable`. Le citazioni `raw/FMP_Docs_*.txt` qui sotto sono **stale** — i file raw originali sono stati sostituiti dall'ingest `raw/fmp_docs.md` + `raw/fmp_docs.json` (263 endpoint stable). Rebuild completo di questo runbook contro la nuova doc e' gap aperto (`fmp-stable-runbook-refresh`).

## Prerequisiti

- API key FMP valida (ottenibile su financialmodelingprep.com)
- Client HTTP (curl, Postman, requests Python, fetch JS)

## Autenticazione

Tutte le richieste richiedono API key in header `apikey` oppure query `?apikey=` [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

## Rate limiting

| Aspetto | Stato wiki (TSK-068) |
|---------|----------------------|
| Richieste/minuto, quota giornaliera, finestra | **Non documentato** nei raw `FMP_Docs_1`–`8` |
| HTTP 429, header `Retry-After` | **Non documentato** nei raw |
| Gap | `fmp-rate-limiting` in [[gaps]] — **aperto** |

**Policy applicazione (L4):** il backend usa **un unico RateLimiter `fmp` a 280 richieste / 60s** condiviso da tutto il traffico (online UI + batch notturno), override `FMP_RATE_LIMIT_PER_MINUTE` [^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §Appendice A]. Il rate limit FMP è **per-API-key (account-wide)**: con una sola key esiste un solo budget, quindi un solo bucket è il modello corretto (due bucket indipendenti potevano sommare oltre il cap → 429). Il valore 280 = piano **Starter 300/min** (confermato dall'operatore dell'account, non da doc contrattuale provider) − ~7% di margine.

**Calibrazione:** se cambia il piano FMP, aggiornare solo `FMP_RATE_LIMIT_PER_MINUTE` (e `DEFAULT_RATE_LIMIT_PER_MINUTE` in `FmpRateLimitProperties`).

**Online vs batch sul bucket condiviso:** l'online fa fail-fast (timeout 2s → degrada). Il batch notturno (`TopValuePicksJob`), che fa fan-out massiccio, su esaurimento del bucket **attende il refresh (~1 min) e ritenta** invece di perdere il ticker — selezione via flag thread-local `FmpBatchContext` letto da `ResilientFmpAdapter` [^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §Appendice A].

## URL base e path endpoint MVP

I raw `FMP_Docs_1`–`8` descrivono **nomi API e parametri**, non host né path HTTP completi (nessuna occorrenza di `financialmodelingprep.com` o `/api/v3` in quei file). Gap: `fmp-endpoint-base-urls` — **aperto**.

### Endpoint logici documentati nei raw (senza URL completo)

| Uso MVP | Nome API nei raw | Parametri chiave (raw) | Source |
|---------|------------------|------------------------|--------|
| Profile | Company Profile Data API | `symbol` | [^src: raw/FMP_Docs_3_Company_Info.txt §Company Profile Data API] |
| Search | Stock Symbol Search API / Company Name Search API | `query`, `limit`, `exchange` | [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Stock Symbol Search API] [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Company Name Search API] |
| Income | Income Statement, Balance Sheet, Cash Flow Statement API | `symbol`, `limit`, `period` | [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API] |
| Balance sheet | (stesso blocco income/balance/cashflow) | idem | [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API] |
| Cash flow | (stesso blocco) | idem | [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API] |
| Key metrics | Key Metrics & TTM Key Metrics API | `symbol`, `limit`, `period` | [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Key Metrics & TTM Key Metrics API] |

### URL completi

| Elemento | Stato |
|----------|--------|
| Host + versione API (`https://…/api/v3`) | **Non verificabile** da raw FMP_Docs 1–8 |
| Path relativi MVP (`/profile/{ticker}`, ecc.) | **Non verificabile** da raw FMP_Docs 1–8 |

**Configurazione L5/L4 (non fonte provider):** default applicativo `fmp.base-url` = `https://financialmodelingprep.com/stable` (aggiornato da `/api/v3` con migrazione US-031/TSK-072, 2026-05-22; pre-migrazione era `/api/v3`, deprecato dal 2025-08-31). Tabella path MVP in ADR-016 [^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §2. URL base endpoint MVP].

### Template richiesta (placeholder)

Sostituire `{base}` con valore configurato (ADR-016) e `{apikey}` con la chiave:

```
GET {base}/profile/{symbol}?apikey={apikey}
GET {base}/search?query={q}&limit=5&apikey={apikey}
GET {base}/income-statement/{symbol}?period=annual&limit=5&apikey={apikey}
```

I segmenti di path sopra **non** sono citabili da `raw/FMP_Docs_*`; sono allineati alla config ADR-016 fino a chiusura gap.

## Errori HTTP comuni

| Codice | Documentato in raw FMP_Docs 1–8 | Stato wiki |
|--------|----------------------------------|------------|
| 401, 403 | No | Gap `fmp-error-codes` — **aperto** |
| 404 | No | Gap `fmp-error-codes` — **aperto** |
| 429 | No | Gap `fmp-error-codes` + `fmp-rate-limiting` — **aperti** |
| 5xx | No | Gap `fmp-error-codes` — **aperto** |
| Formato body errore JSON | No | Gap `fmp-error-codes` — **aperto** |

**Comportamento adapter pianificato (L4):** mapping HTTP → evento `fmp_api_event_log` e retry in ADR-016 [^src: design_&_architecture/decisions/ADR-016-fmp-operations-throttling.md §3. Mapping errori HTTP provider → adapter]. Descrive la **nostra** integrazione, non la documentazione errori del provider.

## Limitazioni documentazione (US-029)

Ingest TSK-068 (2026-05-22): riesame `raw/FMP_Docs_1_Auth_and_Search.txt` … `raw/FMP_Docs_8_News_and_Estimates.txt` — **nessun** nuovo dato su rate limit, URL base ufficiali o codici errore HTTP.

| Gap | Esito TSK-068 |
|-----|----------------|
| `fmp-rate-limiting` | Aperto — serve raw FMP (pricing/FAQ/limiti) |
| `fmp-endpoint-base-urls` | Aperto — serve raw con URL/path ufficiali |
| `fmp-error-codes` | Aperto — serve raw su error response |

Dettaglio e piano ingest: [[gaps]] (sezioni `fmp-*`). EP-009 / US-029.

## Quickstart operativo (parametri da raw)

### Step 1 — Profilo azienda

API: Company Profile Data API [^src: raw/FMP_Docs_3_Company_Info.txt §Company Profile Data API]

```
GET {base}/profile/AAPL?apikey=YOUR_API_KEY
```

(`{base}` non definito nei raw FMP — vedi § URL base.)

### Step 2 — Ricerca per nome/simbolo

API: Company Name Search API [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Company Name Search API]

```
GET {base}/search?query=Apple&limit=5&apikey=YOUR_API_KEY
```

### Step 3 — Quotazione

API: Real-time Quote APIs [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Real-time, Short, Premarket, Aftermarket Quote APIs]

```
GET {base}/quote/AAPL?apikey=YOUR_API_KEY
```

Batch: stessa source, parametri multipli simbolo dove supportato [^src: raw/FMP_Docs_6_Quotes_and_Prices.txt §Batch Quote APIs]

### Step 4 — Conto economico annuale

API: Income Statement, Balance Sheet, Cash Flow Statement API — `period=annual`, `limit` [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API]

```
GET {base}/income-statement/AAPL?period=annual&limit=5&apikey=YOUR_API_KEY
```

### Step 5 — Stock screener

API: Stock Screener API [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Stock Screener API]

```
GET {base}/stock-screener?sector=Technology&marketCapMoreThan=1000000000&priceLowerThan=100&apikey=YOUR_API_KEY
```

## Pagine collegate

[[fmp-api]]
[[fmp-api-overview]]
[[fmp-company-search]]
[[fmp-quotes-stable]]
[[fmp-financial-statements-stable]]
[[gaps]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-002 (R1.0 done): US-004, US-005, US-006
- EP-009 (R1.1): US-029 documentazione rate limit/URL/errori, US-030 throttling backend
