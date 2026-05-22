---
id: fmp-key-metrics-ratios
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, key-metrics, ratios, dcf, roe, roic, graham]
---
# FMP — Key Metrics & Financial Ratios (stable)

^src: raw/fmp_docs.md §Key Metrics & Financial Ratios — ^src: raw/fmp_docs.json sezione="Key Metrics & Financial Ratios"

Sezione dell'API FMP stable con le metriche finanziarie aggregate e i ratios calcolati da FMP (evitano la ricalcolazione manuale su income/balance/cashflow). Critica per il rule engine.

---

## Endpoint principali

### 1. Key Metrics
- **Path**: `GET /stable/key-metrics`
- **Parametri**: `symbol*`, `period` (`annual` | `quarter`), `limit` (default 10)
- **Campi chiave response**:
  - `date`, `calendarYear`
  - `roe` (Return on Equity)
  - `roic` (Return on Invested Capital)
  - `bookValuePerShare` (`bvps`) — usato per Graham Number
  - `pe` (Price/Earnings)
  - `priceToBookValue`
  - `evToEbitda`
  - `debtToEquity`
  - `currentRatio`
  - `earningsYield`
  - `freeCashFlowPerShare`
  - `revenuePerShare`
  - `netIncomePerShare` (ATTENZIONE: non equivalente a EPS reported — usare `eps` da income-statement)
- **Uso rule engine**: ROE 10y avg (RoeRule), ROIC 10y avg (RoicRule), BVPS per GrahamNumberCalculator

### 2. Financial Ratios
- **Path**: `GET /stable/ratios`
- **Parametri**: `symbol*`, `period`, `limit`
- **Campi chiave**:
  - `grossProfitMargin`, `netProfitMargin`, `operatingProfitMargin`
  - `returnOnAssets`, `returnOnEquity`
  - `currentRatio`, `quickRatio`
  - `debtRatio`, `debtEquityRatio`
  - `priceEarningsRatio`, `priceToBookRatio`
- **Uso**: alternativa semplificata per accesso diretto ai ratio senza calcolo manuale

### 3. Key Metrics TTM
- **Path**: `GET /stable/key-metrics-ttm`
- **Parametri**: `symbol*`
- **Response**: singolo record TTM di key metrics

### 4. Financial Ratios TTM
- **Path**: `GET /stable/ratios-ttm`
- **Parametri**: `symbol*`

### 5. Financial Growth
- **Path**: `GET /stable/financial-growth`
- **Parametri**: `symbol*`, `period`, `limit`
- **Campi chiave**:
  - `revenueGrowth`, `netIncomeGrowth`, `epsgrowth`
  - `freeCashFlowGrowth`, `operatingCashFlowGrowth`
  - `bookValueperShareGrowth`
- **Uso**: identificazione trend crescita per DCF assumptions

### 6. Discounted Cash Flow
- **Path**: `GET /stable/discounted-cash-flow`
- **Parametri**: `symbol*`
- **Response**: `{symbol, date, dcf, stockPrice}`
- **Uso**: DCF pre-calcolato da FMP come riferimento; il rule engine usa il proprio DcfCalculator (Greenwald/FCF)

### 7. Advanced Discounted Cash Flow
- **Path**: `GET /stable/advanced-discounted-cash-flow`
- **Parametri**: `symbol*`, `period`
- **Response**: proiezioni annuali dettagliate del DCF

### 8. Historical DCF
- **Path**: `GET /stable/historical-discounted-cash-flow-statement`
- **Parametri**: `symbol*`, `period`, `limit`

---

## Distinzione critica: `netIncomePerShare` vs `eps`

^src: raw/fmp_docs.json (campo response key-metrics)

| Campo | Fonte | Significato |
|-------|-------|-------------|
| `key-metrics.netIncomePerShare` | Key Metrics | Net Income / shares outstanding (calcolato FMP) |
| `income-statement.eps` | Income Statement | EPS reported (come da filing SEC, puo' includere extraordinary items) |

**Decisione implementativa** (TSK-016): per GrahamNumberCalculator si usa `income-statement.eps` (EPS reported) perche' `key-metrics` non espone un campo `eps` diretto e `netIncomePerShare` non e' la stessa grandezza.

---

## Response shape key-metrics (campi critici)

```json
[
  {
    "date": "2024-09-28",
    "calendarYear": "2024",
    "symbol": "AAPL",
    "roe": 1.5472,
    "roic": 0.5432,
    "bookValuePerShare": 3.77,
    "pe": 31.0,
    "currentRatio": 0.87,
    "debtToEquity": 1.45
  }
]
```

---

## Cross-link

- Entity: [[fmp-api]]
- Financial statements raw: [[fmp-financial-statements-stable]]
- Rule engine rules: [[value-investing-rule-engine]] (RoeRule, RoicRule, GrahamNumberCalculator)
- Synthesis: [[value-investing-fmp-integration]]
- Runbook: [[fmp-api-quickstart]]
