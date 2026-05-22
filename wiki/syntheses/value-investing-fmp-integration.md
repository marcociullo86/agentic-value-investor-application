---
id: value-investing-fmp-integration
type: synthesis
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json", "raw/01_Principi_Fondamentali_Value_Investing.md", "raw/03_Analisi_Fondamentale_e_Valutazione.md", "raw/05_Analisi_10K_10Q_e_Regole_Buffett.md", "raw/06_Documento_Funzionale_WebApp_Value_Investing.md"]
status: draft
created: 2026-05-22
tags: [fmp, stable, value-investing, rule-engine, integration, adr-004, synthesis]
---
# Value Investing — Mapping Metriche → Endpoint FMP Stable

^src: raw/fmp_docs.md §Financial Statements, §Key Metrics, §Company Information — ^src: raw/fmp_docs.json — ^src: raw/03_Analisi_Fondamentale_e_Valutazione.md — ^src: raw/06_Documento_Funzionale_WebApp_Value_Investing.md

Sintesi cross-dominio che mappa le metriche del framework value investing (Graham/Buffett) agli endpoint FMP stable corrispondenti, con indicazione dell'invariante architetturale BE.

**Invariante**: l'**architettura** lato BE definita in ADR-004 (adapter pattern, cache 24h, Resilience4j, event log) **non cambia** con la migrazione v3 -> stable. Cambiano solo i path URL, i parametri di query e i DTO di mapping.

---

## 1. Pipeline analisi ticker

```
Ticker
  |
  v
search-symbol / search-name    <- fmp-company-search
  |
  v
profile                         <- fmp-company-information (prezzo corrente, sector)
  |
  v
income-statement (10 anni)      <- fmp-financial-statements-stable
balance-sheet-statement (10y)   <- fmp-financial-statements-stable
cash-flow-statement (10y)       <- fmp-financial-statements-stable
key-metrics (10 anni)           <- fmp-key-metrics-ratios
  |
  v
RuleEngineService (7 regole)    <- rule-engine interno
GrahamNumberCalculator          <- calcolatore interno
DcfCalculator (Greenwald/FCF)   <- calcolatore interno
MarginOfSafetyEvaluator         <- valutatore finale
  |
  v
RuleEngineResult (DB)           <- persistenza JSONB
```

---

## 2. Mapping metriche → endpoint FMP stable

### Regole quantitative (7 segnali)

| Regola (ruleId) | Metrica | Endpoint FMP stable | Campo FMP | Note |
|-----------------|---------|---------------------|-----------|------|
| `ROE_10Y_AVG` | ROE medio 10 anni | `/stable/key-metrics` | `roe` | Min 5 anni usabili |
| `ROIC_10Y_AVG` | ROIC medio 10 anni | `/stable/key-metrics` | `roic` | Min 5 anni usabili |
| `GROSS_MARGIN_10Y_AVG` | Gross margin medio 10y | `/stable/income-statement` | `grossProfitRatio` (o `grossProfit/revenue`) | Fallback derivato |
| `NET_MARGIN_10Y_AVG` | Net margin medio 10y | `/stable/income-statement` | `netIncomeRatio` (o `netIncome/revenue`) | Fallback derivato |
| `CURRENT_RATIO_LATEST` | Current ratio piu' recente | `/stable/balance-sheet-statement` | `totalCurrentAssets / totalCurrentLiabilities` | LATEST year (newest-first [0]) |
| `DEBT_TO_INCOME_LATEST` | Long term debt / net income | `/stable/balance-sheet-statement` + `/stable/income-statement` | `longTermDebt / netIncome` | LATEST year; INDETERMINATE se netIncome<=0 |
| `CAPEX_INTENSITY_10Y_AVG` | |CapEx| / netIncome medio 10y | `/stable/cash-flow-statement` + `/stable/income-statement` | `abs(capitalExpenditure) / netIncome`; convenzione FMP: capEx NEGATIVO |

### Calcolatori scalari

| Calcolatore | Input | Endpoint FMP stable | Campo FMP |
|-------------|-------|---------------------|-----------|
| `GrahamNumberCalculator` | EPS + BVPS | `/stable/income-statement` (eps) + `/stable/key-metrics` (bookValuePerShare) | `eps` (NON `netIncomePerShare`) |
| `DcfCalculator` (Greenwald) | OCF, CapEx, D&A | `/stable/cash-flow-statement` | `operatingCashFlow`, `capitalExpenditure`, `depreciationAndAmortization` |
| `DcfCalculator` (FCF fallback) | FreeCashFlow | `/stable/cash-flow-statement` | `freeCashFlow` (gia' calcolato da FMP) |
| `MarginOfSafetyEvaluator` | prezzo corrente | `/stable/profile` | `price` |

---

## 3. Soglie delle regole (invarianti)

| ruleId | GREEN | YELLOW | RED | INDETERMINATE |
|--------|-------|--------|-----|---------------|
| ROE_10Y_AVG | >15% | 10-15% | <10% | <5 anni dati |
| ROIC_10Y_AVG | >12% | 8-12% | <8% | <5 anni dati |
| GROSS_MARGIN_10Y_AVG | >40% | 30-40% | <30% | <5 anni dati |
| NET_MARGIN_10Y_AVG | >10% | — | <=10% | <5 anni dati |
| CURRENT_RATIO_LATEST | >2.0 | 1.5-2.0 | <1.5 | assets/liabilities null |
| DEBT_TO_INCOME_LATEST | <4 | 4-5 | >5 | netIncome<=0 o null |
| CAPEX_INTENSITY_10Y_AVG | <25% | 25-30% | >30% | netIncome<=0 o null |

---

## 4. Screener parametrico (EP-001)

| Filtro | Endpoint FMP stable | Parametro |
|--------|---------------------|-----------|
| Market cap (5 fasce: $50M-$200B+) | `/stable/company-screener` | `marketCapMoreThan` + `marketCapLessThan` |
| Settore GICS (11 settori) | `/stable/company-screener` | `sector` |
| Exchange | `/stable/company-screener` | `exchange` (es. `NASDAQ,NYSE`) |
| Esclusione settori hard-to-predict | Filtro applicativo post-screener | `sector NOT IN (Finance, Utilities, ...)` |

---

## 5. Architettura BE — invariante ADR-004

La migrazione v3 -> stable **non richiede cambiamenti architetturali**. Cambiamenti limitati a:

| Componente | Cambiamento richiesto |
|-----------|----------------------|
| `FmpAdapterRestClient` | Path URL: `/api/v3/{symbol}` -> `/stable/endpoint?symbol={symbol}` |
| `IncomeStatementDto` | Verificare campo mapping (es. `period` enum) |
| `BalanceSheetDto` | Verificare campo mapping |
| `CashFlowDto` | Verificare `capitalExpenditure` sign (negativo in stable — gia' gestito da `abs()` in CapexIntensityRule) |
| `KeyMetricsDto` | Verificare `bookValuePerShare` spelling |
| `ProfileDto` | Verificare `mktCap` vs `marketCap` spelling |

Invarianti (non cambiano):
- `FmpAdapter` interface e firme metodi
- `FmpCacheService` logica cache-aside + TTL 24h/1h
- `ResilientFmpAdapter` (Resilience4j CB/Retry/RateLimiter/Bulkhead)
- `FmpEventLogger` e `fmp_api_event_log` schema DB
- `RuleEngineService`, tutte le 7 rule, GrahamNumberCalculator, DcfCalculator

Vedi [[webapp-architecture-vi]] per dettagli implementativi.

---

## 6. Gap e limiti FMP stable (value investing)

| Gap | Descrizione | Impatto |
|-----|-------------|---------|
| `vi-sec-narrative-gap` | FMP non espone testo narrativo SEC (10-K Item 1, 1A, 7) | Step 1-3 analisi 10-K richiede EDGAR diretto |
| `fmp-stable-rate-limiting` | Rate limit non documentato | Cache 24h mitiga; vedi gap aperto |
| `fmp-stable-analyst-estimates` | Stime analisti (consensus EPS, price target) non trovate nella stable | Non integrabili nel MVP senza verifica |

---

## Cross-link

- Entity: [[fmp-api]]
- Source: [[fmp-docs]]
- Panoramica API: [[fmp-api-overview]]
- Financial statements: [[fmp-financial-statements-stable]]
- Key metrics: [[fmp-key-metrics-ratios]]
- Company search: [[fmp-company-search]]
- Company info: [[fmp-company-information]]
- Rule engine: [[value-investing-rule-engine]]
- Architettura: [[webapp-architecture-vi]]
- Runbook: [[fmp-api-quickstart]]
- Concetti: [[intrinsic-value]], [[margin-of-safety]], [[economic-moat]], [[graham-number]]
