---
id: fmp-api-quickstart
type: runbook
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, runbook, quickstart, auth, integration]
---
# Runbook: FMP API Stable — Quickstart

^src: raw/fmp_docs.md §Autorizzazione, §Company Search, §Company Information, §Financial Statements, §Key Metrics — ^src: raw/fmp_docs.json (endpoint_url verificati)

Guida operativa per integrare la FMP API stable nel rule engine value investing. Copre autenticazione, i 6-8 endpoint critici, esempi curl e note di integrazione BE.

---

## Step 1 — Ottenere la API Key

1. Registrarsi su https://site.financialmodelingprep.com/developer/docs
2. Il piano gratuito ha accesso limitato; i piani a pagamento sbloccano storico 10 anni e endpoint avanzati.
3. Copiare la API key nel file di configurazione:

```yaml
# src/backend/src/main/resources/application.yml (profilo dev)
app:
  fmp:
    base-url: https://financialmodelingprep.com/stable
    api-key: ${FMP_API_KEY}
```

La chiave e' iniettata a runtime da variabile d'ambiente `FMP_API_KEY` (mai in chiaro nel codice).

---

## Step 2 — Autenticazione

^src: raw/fmp_docs.md §Autorizzazione

Due modalita' equivalenti (scegliere una):

```bash
# Modalita' 1: Header HTTP (preferita in FmpAdapterRestClient)
curl -H "apikey: YOUR_API_KEY" \
  "https://financialmodelingprep.com/stable/profile?symbol=AAPL"

# Modalita' 2: Query string
curl "https://financialmodelingprep.com/stable/profile?symbol=AAPL&apikey=YOUR_API_KEY"
```

In `FmpAdapterRestClient` Spring RestClient usa la modalita' header via interceptor o `defaultHeader`.

---

## Step 3 — Endpoint critici per il Rule Engine

### 3.1 Search Symbol (ricerca ticker)

^src: raw/fmp_docs.json endpoint_url="https://financialmodelingprep.com/stable/search-symbol?query=AAPL"

```bash
curl -H "apikey: KEY" \
  "https://financialmodelingprep.com/stable/search-symbol?query=AAPL&limit=10"
```

Response:
```json
[
  {
    "symbol": "AAPL",
    "name": "Apple Inc.",
    "currency": "USD",
    "exchangeFullName": "NASDAQ Global Select",
    "exchange": "NASDAQ"
  }
]
```

### 3.2 Search Name (ricerca per nome)

^src: raw/fmp_docs.json endpoint_url="https://financialmodelingprep.com/stable/search-name?query=AA"

```bash
curl -H "apikey: KEY" \
  "https://financialmodelingprep.com/stable/search-name?query=Apple&limit=5&exchange=NASDAQ"
```

### 3.3 Company Profile (profilo + prezzo corrente)

^src: raw/fmp_docs.json sezione="Company Information"

```bash
curl -H "apikey: KEY" \
  "https://financialmodelingprep.com/stable/profile?symbol=AAPL"
```

Response campi critici:
```json
[{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "price": 189.50,
  "mktCap": 2950000000000,
  "sector": "Technology",
  "industry": "Consumer Electronics",
  "exchange": "NASDAQ",
  "currency": "USD"
}]
```

### 3.4 Income Statement (conto economico 10 anni)

^src: raw/fmp_docs.json sezione="Financial Statements"

```bash
curl -H "apikey: KEY" \
  "https://financialmodelingprep.com/stable/income-statement?symbol=AAPL&period=annual&limit=10"
```

Campi chiave per le regole: `date`, `calendarYear`, `revenue`, `grossProfit`, `grossProfitRatio`, `netIncome`, `netIncomeRatio`, `eps`

**ATTENZIONE**: `response[0]` e' l'esercizio piu' recente (newest-first).

### 3.5 Balance Sheet Statement (stato patrimoniale 10 anni)

```bash
curl -H "apikey: KEY" \
  "https://financialmodelingprep.com/stable/balance-sheet-statement?symbol=AAPL&period=annual&limit=10"
```

Campi chiave: `totalCurrentAssets`, `totalCurrentLiabilities`, `longTermDebt`, `totalStockholdersEquity`

### 3.6 Cash Flow Statement (rendiconto finanziario 10 anni)

```bash
curl -H "apikey: KEY" \
  "https://financialmodelingprep.com/stable/cash-flow-statement?symbol=AAPL&period=annual&limit=10"
```

Campi chiave: `operatingCashFlow`, `capitalExpenditure` (NEGATIVO), `freeCashFlow`, `depreciationAndAmortization`

### 3.7 Key Metrics (metriche aggregate 10 anni)

```bash
curl -H "apikey: KEY" \
  "https://financialmodelingprep.com/stable/key-metrics?symbol=AAPL&period=annual&limit=10"
```

Campi chiave: `roe`, `roic`, `bookValuePerShare`, `pe`, `currentRatio`

### 3.8 Company Screener (screener parametrico)

```bash
curl -H "apikey: KEY" \
  "https://financialmodelingprep.com/stable/company-screener?exchange=NASDAQ,NYSE&sector=Technology&marketCapMoreThan=1000000000&limit=50"
```

Parametri disponibili: `exchange`, `sector`, `marketCapMoreThan`, `marketCapLessThan`, `priceMoreThan`, `priceLessThan`, `volumeMoreThan`, `betaMoreThan`, `betaLessThan`, `country`, `limit`

---

## Step 4 — Configurazione FmpAdapterRestClient (migrazione da v3)

I path cambiano da `/{symbol}` (path variable v3) a `?symbol={symbol}` (query param stable):

```kotlin
// PRIMA (v3 — DEPRECATO)
restClient.get()
    .uri("/income-statement/{symbol}?period=annual&limit=10", symbol)

// DOPO (stable)
restClient.get()
    .uri("/income-statement?symbol={symbol}&period=annual&limit=10", symbol)
```

Base URL in `application.yml`:
```yaml
app:
  fmp:
    base-url: https://financialmodelingprep.com/stable
```

---

## Step 5 — Verifica risposta e gestione errori

| HTTP Status | Significato | Gestione |
|-------------|-------------|----------|
| 200 | Successo | elabora response |
| 200 con `[]` | Ticker non trovato (nessun dato) | FmpTickerNotFoundException |
| 429 | Rate limit superato | FmpUnavailable(httpStatus=429) -> log429RateLimited -> Circuit Breaker |
| 5xx | Errore FMP | FmpUnavailableException -> Retry -> stale fallback |
| 401 | API key invalida / scaduta | Log errore + alert operativo |

**Nota**: FMP stable risponde 200 con array vuoto `[]` per ticker inesistenti (non 404). La logica di rilevamento ticker non trovato e' nel codice applicativo (`if (response.isEmpty()) throw FmpTickerNotFoundException`).

---

## Step 6 — Caching operativo

| Dato | Endpoint | TTL | Tabella DB |
|------|----------|-----|------------|
| Financial snapshot | income/balance/cash-flow/key-metrics | 24h | `fmp_financial_snapshot` |
| Profile (prezzo) | profile | 1h | `fmp_profile_snapshot` |

Il `FmpCacheService` gestisce la logica cache-aside automaticamente. In caso di miss si chiama FMP; in caso di errore 5xx si serve lo snapshot stale con header `X-Data-Stale: true`.

---

## Troubleshooting

| Problema | Causa probabile | Soluzione |
|----------|----------------|-----------|
| Response vuota `[]` per ticker noto | Ticker non presente nell'universo stable | Verificare con `/stable/stock-list`; stable rimuove duplicati/rinominazioni |
| `capitalExpenditure` positivo in alcuni anni | FMP occasionalmente flipped sign | Usare `abs()` — gia' implementato in CapexIntensityRule |
| Dati mancanti per anni recenti | Bilancio non ancora depositato | TTM come fallback (`income-statement-ttm`) |
| Rate limit 429 in sviluppo | Troppe chiamate manuali in test | Usare fixture JSON locali (fmp-fixtures/) per i test |

---

## Cross-link

- Entity: [[fmp-api]]
- Source: [[fmp-docs]]
- Concetti per sezione: [[fmp-company-search]], [[fmp-company-information]], [[fmp-financial-statements-stable]], [[fmp-key-metrics-ratios]]
- Architettura BE: [[webapp-architecture-vi]]
- Mapping metriche: [[value-investing-fmp-integration]]
- Panoramica: [[fmp-api-overview]]
