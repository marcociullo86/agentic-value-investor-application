---
id: fmp-financial-statements-stable
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, financial-statements, income, balance-sheet, cash-flow, ttm]
---
# FMP — Financial Statements (stable)

^src: raw/fmp_docs.md §Financial Statements — ^src: raw/fmp_docs.json sezione="Financial Statements"

Sezione dell'API FMP stable con i tre rendiconti finanziari fondamentali (conto economico, stato patrimoniale, rendiconto finanziario) in formato annuale, trimestrale e TTM. Questa sezione e' la piu' critica per il rule engine value investing.

---

## Endpoint principali

### 1. Income Statement
- **Path**: `GET /stable/income-statement`
- **Parametri**: `symbol*`, `period` (`annual` | `quarter`), `limit` (default 10, max 120)
- **Campi chiave response**:
  - `date`, `calendarYear`, `period`
  - `revenue`, `grossProfit`, `operatingIncome`, `netIncome`
  - `grossProfitRatio`, `netIncomeRatio` (gia' normalizzati 0-1)
  - `eps`, `epsDiluted`
  - `ebitda`, `ebitdaratio`
  - `weightedAverageShsOut`, `weightedAverageShsOutDil`
- **Uso rule engine**: ROE (10y), net margin (10y), gross margin (10y), EPS per Graham Number

### 2. Balance Sheet Statement
- **Path**: `GET /stable/balance-sheet-statement`
- **Parametri**: `symbol*`, `period` (`annual` | `quarter`), `limit`
- **Campi chiave response**:
  - `date`, `calendarYear`
  - `totalCurrentAssets`, `totalCurrentLiabilities` (Current Ratio)
  - `totalAssets`, `totalLiabilities`, `totalStockholdersEquity`
  - `longTermDebt`, `shortTermDebt`, `totalDebt`
  - `cashAndCashEquivalents`
  - `netReceivables`, `inventory`
- **Uso rule engine**: Current Ratio latest, Debt-to-Income latest, base per ROIC

### 3. Cash Flow Statement
- **Path**: `GET /stable/cash-flow-statement`
- **Parametri**: `symbol*`, `period` (`annual` | `quarter`), `limit`
- **Campi chiave response**:
  - `date`, `calendarYear`
  - `operatingCashFlow` (OCF — flusso operativo)
  - `capitalExpenditure` (NEGATIVO per convenzione FMP — cash outflow)
  - `freeCashFlow` (OCF + CapEx, gia' calcolato da FMP)
  - `dividendsPaid`, `netIncome`
  - `depreciationAndAmortization`
- **Uso rule engine**: CapEx Intensity (10y), Owner Earnings (OCF - |CapEx|), DCF

### 4. Income Statement TTM (Trailing Twelve Months)
- **Path**: `GET /stable/income-statement-ttm`
- **Parametri**: `symbol*`
- **Response**: singolo record TTM con stessi campi di income-statement
- **Uso**: valutazione corrente senza attendere l'esercizio annuale completo

### 5. Balance Sheet TTM
- **Path**: `GET /stable/balance-sheet-statement-ttm`
- **Parametri**: `symbol*`

### 6. Cash Flow TTM
- **Path**: `GET /stable/cash-flow-statement-ttm`
- **Parametri**: `symbol*`

### 7. Financial Statements As Reported
- **Path**: `GET /stable/financial-statements-as-reported`
- **Parametri**: `symbol*`, `period`, `limit`
- **Uso**: dati non normalizzati, direttamente dai filing SEC (XBRL)

---

## Convention critiche (differenze da v3)

| Aspetto | API stable | Impatto |
|---------|-----------|---------|
| `capitalExpenditure` | NEGATIVO (cash outflow) | Usare `abs()` nel CapexIntensityRule |
| Ordine array | Newest-first (elemento [0] = piu' recente) | Usare `firstOrNull()` per latest |
| `period` param | `annual` / `quarter` (non `FY` / `Q`) | Aggiornare parametri FmpAdapterRestClient |
| `limit` default | 10 (10 anni per period=annual) | Adeguato per 10y average rules |

---

## Response shape (income statement — campi critici)

```json
[
  {
    "date": "2024-09-28",
    "calendarYear": "2024",
    "period": "FY",
    "revenue": 391035000000,
    "grossProfit": 180683000000,
    "grossProfitRatio": 0.46232,
    "netIncome": 93736000000,
    "netIncomeRatio": 0.23971,
    "eps": 6.11,
    "epsDiluted": 6.08,
    "capitalExpenditure": -9447000000
  }
]
```

---

## Caching (ADR-004)

- TTL: 24h per tutti e tre i rendiconti (`fmp_financial_snapshot`)
- Endpoint key: `INCOME_STATEMENT`, `BALANCE_SHEET`, `CASH_FLOW` (CHECK 4-valori in DB + `KEY_METRICS`)
- Stale fallback: `FmpCacheService.getStale()` su FmpUnavailableException
- Audit: ogni fetch loggato in `fmp_api_event_log`

---

## Cross-link

- Entity: [[fmp-api]]
- Metrics derivate: [[fmp-key-metrics-ratios]] (ROE, ROIC, BVPS calcolati da FMP)
- Rule engine: [[value-investing-rule-engine]] (usa questi endpoint come input primari)
- Integrazione: [[value-investing-fmp-integration]]
- Runbook: [[fmp-api-quickstart]]
