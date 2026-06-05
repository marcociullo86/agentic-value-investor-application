---
id: fmp-key-metrics-ratios
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, key-metrics, ratios, dcf, roe, roic, graham, platform-domain]
domain: platform
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
- Concept: [[dcf-discount-rate-policy]]
- Concept: [[owner-earnings-formula-variants]]

---

## Aggiornamenti (v2026-05-23) — audit schema drift /stable

^src: raw/fmp_docs.json:1176 — payload AAPL `GET /stable/key-metrics` (FY 2024-09-28)

Audit eseguito il 2026-05-23 confrontando il payload reale `raw/fmp_docs.json:1176` con la documentazione esistente della sezione `## Response shape key-metrics`. Sono emerse tre categorie di divergenze: rinomina di field, rimozione di field (spostati in `/stable/ratios`), e un caso speciale su `bookValuePerShare`. Tutte le correzioni al codice Kotlin sono tracciate nel commit `bdb2d3e`.

### Field rinominati v3 → stable

I seguenti field sono presenti in `/stable/key-metrics` ma con nomi JSON diversi rispetto all'API v3. Il `KeyMetricsDto.kt` usa `@JsonProperty` per mappare il nome JSON reale sul nome Kotlin storico, preservando la back-compat dei caller interni. [^src: raw/fmp_docs.json:1176] [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/dto/KeyMetricsDto.kt]

| Nome legacy v3 / nome Kotlin | Nome JSON reale `/stable/key-metrics` | Mapping Kotlin |
|---|---|---|
| `roe` | `returnOnEquity` | `@JsonProperty("returnOnEquity") val roe` |
| `roic` | `returnOnInvestedCapital` | `@JsonProperty("returnOnInvestedCapital") val roic` |
| `daysSalesOutstanding` | `daysOfSalesOutstanding` | `@JsonProperty("daysOfSalesOutstanding") val daysSalesOutstanding` |
| `daysPayablesOutstanding` | `daysOfPayablesOutstanding` | `@JsonProperty("daysOfPayablesOutstanding") val daysPayablesOutstanding` |
| `daysOfInventoryOnHand` | `daysOfInventoryOutstanding` | `@JsonProperty("daysOfInventoryOutstanding") val daysOfInventoryOnHand` |
| `researchAndDdevelopementToRevenue` (doppia D, typo Kotlin) | `researchAndDevelopementToRevenue` (singola D, typo FMP) | `@JsonProperty("researchAndDevelopementToRevenue") val researchAndDdevelopementToRevenue` |

Nota: il typo "Ddevelopement" (doppia D) nel nome Kotlin e' un artefatto storico del DTO v3; il nome JSON FMP stable ha una sola D ma mantiene comunque "Developement" (typo FMP). Entrambi i typo sono preservati via `@JsonProperty` per non rompere i consumer esistenti. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/dto/KeyMetricsDto.kt:51]

### Field NON presenti in /stable/key-metrics (spostati in /stable/ratios)

I seguenti field erano inclusi nel payload v3 di `/key-metrics` ma sono **assenti** dal payload `/stable/key-metrics` (confermato da `raw/fmp_docs.json:1176`). Sono invece presenti in `/stable/ratios` (confermato da `raw/fmp_docs.json:1198`). [^src: raw/fmp_docs.json:1176] [^src: raw/fmp_docs.json:1198]

**Valuation / price ratios** (ora in `/stable/ratios`):
- `peRatio` → `priceToEarningsRatio` in `/stable/ratios`
- `pbRatio` → `priceToBookRatio` in `/stable/ratios`
- `priceToSalesRatio` → `priceToSalesRatio` in `/stable/ratios`
- `pocfratio` → `priceToOperatingCashFlowRatio` in `/stable/ratios`
- `pfcfRatio` → `priceToFreeCashFlowRatio` in `/stable/ratios`
- `ptbRatio` → `priceToBookRatio` in `/stable/ratios`

**Per-share metrics** (ora in `/stable/ratios`):
- `revenuePerShare`, `netIncomePerShare`, `operatingCashFlowPerShare`, `freeCashFlowPerShare`
- `cashPerShare`, `bookValuePerShare`, `tangibleBookValuePerShare`, `shareholdersEquityPerShare`
- `interestDebtPerShare`, `capexPerShare`

**Leverage / coverage** (ora in `/stable/ratios`):
- `debtToEquity` → `debtToEquityRatio` in `/stable/ratios`
- `debtToAssets` → `debtToAssetsRatio` in `/stable/ratios`
- `interestCoverage` → `interestCoverageRatio` in `/stable/ratios`

**Dividend** (ora in `/stable/ratios`):
- `dividendYield`, `payoutRatio` → `dividendYield`, `dividendPayoutRatio` in `/stable/ratios`

**Comportamento backend Kotlin:** il `KeyMetricsDto.kt` mantiene tutti questi field dichiarati come `Double? = null` (senza `@JsonProperty` dedicato, quindi Jackson non trova la chiave nel payload `/stable/key-metrics` e li popola silenziosamente con `null`). La back-compat e' preservata: i caller che leggevano questi field da `KeyMetricsDto` continueranno a ricevere `null` invece di un'eccezione. Le rule del Rule Engine che li utilizzano hanno fallback derivato: ad esempio `GrossMarginRule` deriva il margine da `grossProfit / revenue` dal payload income-statement, non da `KeyMetricsDto`. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/dto/KeyMetricsDto.kt]

### bookValuePerShare (caso speciale)

`bookValuePerShare` e' nel gruppo dei field assenti da `/stable/key-metrics` (vedi sezione precedente) ma merita trattazione separata perche' e' un input critico per il calcolo del **Graham Number**.

**Problema rilevato in produzione (TTD 2026-05-23):** il `GrahamNumberCalculator` leggeva `bookValuePerShare` direttamente da `KeyMetricsDto`. Poiche' `/stable/key-metrics` non include piu' questo field, il DTO restituiva `null`; il calculator produceva sistematicamente "Non applicabile" per tutti i ticker. Verificato su TTD con simbolo TTD il 2026-05-23. [^src: raw/fmp_docs.json:1176]

**Fix implementato (commit bdb2d3e):** il `GrahamNumberCalculator` deriva ora il BVPS dai financial statements fondamentali, che sono disponibili indipendentemente dall'endpoint key-metrics:

```
BVPS = totalStockholdersEquity / weightedAverageShsOutDil
```

Dove:
- `totalStockholdersEquity` proviene dal **Balance Sheet** (`/stable/balance-sheet-statement`) [^src: raw/fmp_docs.json sezione="Balance Sheet Statement"]
- `weightedAverageShsOutDil` proviene dall'**Income Statement** (`/stable/income-statement`) [^src: raw/fmp_docs.json sezione="Income Statement"]

Questa derivazione e' algebricamente equivalente al BVPS che FMP esponeva in v3 via `key-metrics.bookValuePerShare`, ma e' piu' robusta perche' dipende solo dagli statement primari — che sono sempre presenti — e non da un campo calcolato dell'endpoint key-metrics. Il campo `bookValuePerShare: Double? = null` rimane nel `KeyMetricsDto.kt` per back-compat ma non viene piu' usato dal `GrahamNumberCalculator`. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/dto/KeyMetricsDto.kt:22]

### TSK e gap correlati

- **US-053** (`@JsonProperty` mapping `/stable/key-metrics` ROE/ROIC e altri field rinominati): fix retroattivo che corrisponde ai 6 mapping `@JsonProperty` documentati nella sezione "Field rinominati" sopra. Implementato nel `KeyMetricsDto.kt` corrente. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/fmp/dto/KeyMetricsDto.kt]
- **Gap `wiki-fmp-key-metrics-stable-rename`** (aperto 2026-05-23 dal TPM): questo aggiornamento costituisce la chiusura parziale del gap documentale. Il gap segnalava che la wiki usava i nomi logici `roe`/`roic` senza distinguerli dalle chiavi JSON reali del payload. La sezione "Field rinominati" sopra chiarisce la distinzione: il payload JSON usa `returnOnEquity`/`returnOnInvestedCapital`; il campo Kotlin si chiama `roe`/`roic` per convenzione storica.
- **Commit bdb2d3e**: fonte dei fix al `GrahamNumberCalculator` (derivazione BVPS da balance sheet + income statement) e ai `@JsonProperty` di `KeyMetricsDto`. Tutti i fix documentati in questo aggiornamento sono stati introdotti in questo commit.
