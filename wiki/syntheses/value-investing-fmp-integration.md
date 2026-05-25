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
RuleEngineService (13 regole)   <- rule-engine interno
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

---

## Endpoint FMP Aggiuntivi da agent.py

**Fonte**: `raw/09_agent_py_method_analysis.md §3` + `raw/agent.py`. Questi endpoint sono usati da agent.py v2.6.1 e non sono ancora wrappati nel Rule Engine Kotlin MVP. Sono necessari per EP-011 (Deep Analysis) e EP-012 (Batch Universe Screener).

| Endpoint | Nodo agent.py | Uso | EP target |
|---|---|---|---|
| `/stable/sec-filings-search/symbol?symbol={symbol}&from={date}&to={date}` | `node_leggi_report_10k` | Lista filing SEC con `formType` (10-K, 10-Q, 8-K) + `finalLink` URL diretto al filing | EP-011 |
| `/stable/historical-price-eod/full?symbol={symbol}&from={date}&to={date}` | `node_check_price_action` | Storico OHLCV 12 mesi per drawdown 52-settimane | EP-011 |
| `/stable/news/stock?symbols={symbol}&page=0&limit={N}` | `node_news_sentiment` | News per ticker; agent.py filtra post-hoc a 90gg | EP-011 |
| `/stable/news/stock-latest?page=0&limit=200` | `node_screener` (Segnale 3 news scout) | News mercato generali per screener universe (nessun ticker specifico) | EP-012 |
| `/stable/quote?symbol={symbol}` | `node_calcola_valore_buffett` | Prezzo corrente + `sharesOutstanding` + `marketCap` (alternativa leggera a `/stable/profile`) | EP-011 |

[^src: raw/09_agent_py_method_analysis.md §3] [^src: raw/agent.py:1140-1182] [^src: raw/agent.py:1661-1669] [^src: raw/agent.py:1521-1525] [^src: raw/agent.py:790] [^src: raw/agent.py:1801]

### Note di Integrazione

- **`/stable/sec-filings-search/symbol`**: il campo `finalLink` restituisce l'URL diretto al filing SEC (formato HTML o XBRL). agent.py scarica il raw HTML e lo processa con `BSHTMLLoader` + `BeautifulSoup`. Il porting Kotlin richiede un HTTP client che scarichi il contenuto e lo passi al chunker (sidecar Python).
- **`/stable/historical-price-eod/full`**: i parametri `from`/`to` sono date in formato `YYYY-MM-DD`. agent.py richiede 12 mesi di storico per calcolare il max 52-settimane. Il campo `close` e' usato per il drawdown.
- **`/stable/news/stock`**: nessun parametro di data; agent.py filtra le news con data > `today - 90gg` post-hoc. La paginazione usa `page=0` + `limit` variabile.
- **`/stable/news/stock-latest`**: endpoint senza ticker — restituisce le ultime N news di mercato generale. Usato per il segnale "news scout" dello screener (Gemini Flash classifica se le news citano business Buffett-style).
- **`/stable/quote`**: piu' leggero di `/stable/profile` per il solo recupero del prezzo corrente e `sharesOutstanding`. Usato in `node_calcola_valore_buffett` per dividere il valore totale DCF per le azioni.

Vedi [[value-investor-bot-architecture]] per il mapping completo nodo → endpoint.
