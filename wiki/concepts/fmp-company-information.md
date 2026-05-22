---
id: fmp-company-information
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, company, profile, screener]
---
# FMP — Company Information (stable)

^src: raw/fmp_docs.md §Company Information — ^src: raw/fmp_docs.json sezione="Company Information"

Sezione dell'API FMP stable con dati fondamentali sull'azienda: profilo, market cap, peer group, screener parametrico.

---

## Endpoint principali

### 1. Company Profile
- **Path**: `GET /stable/profile`
- **Parametri**: `symbol*` (string, es. `AAPL`)
- **Response**: `[{symbol, companyName, price, mktCap, sector, industry, country, exchange, currency, description, website, ceo, ...}]`
- **Uso critico rule engine**: fonte del prezzo corrente per MarginOfSafetyEvaluator e input per il calcolo del Graham Number
- **Caching**: TTL 1h in `fmp_profile_snapshot` (ADR-004; TTL proposta, vedi gap `tpm-profile-snapshot-ttl`)

### 2. Company Screener
- **Path**: `GET /stable/company-screener`
- **Parametri principali**:
  - `exchange` (string, es. `NASDAQ,NYSE`)
  - `sector` (string, es. `Technology`)
  - `marketCapMoreThan` / `marketCapLessThan` (number)
  - `priceMoreThan` / `priceLessThan` (number)
  - `volumeMoreThan` (number)
  - `betaMoreThan` / `betaLessThan` (number)
  - `country` (string)
  - `limit` (number)
- **Response**: lista di ticker con attributi base (symbol, name, sector, marketCap, price, volume, beta)
- **Uso**: ScreenerService (TSK-005) — filtro per market cap e settore (GICS Q_003)
- **Note**: La stable API rimuove duplicati rispetto a v3; ordine non garantito per market cap.

### 3. Market Cap
- **Path**: `GET /stable/market-cap`
- **Parametri**: `symbol*`
- **Response**: `{symbol, date, marketCap}`
- **Uso**: market cap storico per classificazione fasce

### 4. Historical Market Cap
- **Path**: `GET /stable/historical-market-cap`
- **Parametri**: `symbol*`, `limit`, `from`, `to`
- **Response**: serie storica `[{date, marketCap}]`

### 5. Stock Peers
- **Path**: `GET /stable/stock-peers`
- **Parametri**: `symbol*`
- **Response**: `{symbol, peersList: [...]}`
- **Uso**: identificazione peer group per analisi comparativa

### 6. Company Notes
- **Path**: `GET /stable/company-notes`
- **Parametri**: `symbol*`
- **Response**: note qualitative sull'azienda

### 7. Company Outlook
- **Path**: `GET /stable/company-outlook`
- **Parametri**: `symbol*`
- **Response**: dati consolidati (profilo + metriche + statements in un unico payload)
- **Uso**: alternativa all-in-one per ridurre il numero di chiamate API

---

## Response shape profile (campi chiave per rule engine)

```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "price": 189.50,
  "mktCap": 2950000000000,
  "sector": "Technology",
  "industry": "Consumer Electronics",
  "country": "US",
  "exchange": "NASDAQ",
  "currency": "USD",
  "ceo": "Tim Cook",
  "description": "Apple Inc. designs..."
}
```

---

## Mapping rule engine

| Campo FMP | Uso nel rule engine |
|-----------|---------------------|
| `price` | prezzo corrente per MoS (`current_price_at_eval`) |
| `mktCap` | filtro screener fasce market cap (Q_003: $50M-$200B+) |
| `sector` | filtro screener settori GICS (11 settori) |

---

## Cross-link

- Entity: [[fmp-api]]
- Ricerca: [[fmp-company-search]] (search-symbol/name -> poi profile)
- Financial: [[fmp-financial-statements-stable]] (dati bilancio per regole quantitative)
- Metrics: [[fmp-key-metrics-ratios]] (ROE, ROIC, BVPS)
- Synthesis: [[value-investing-fmp-integration]]
- Runbook: [[fmp-api-quickstart]]
