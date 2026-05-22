---
id: fmp-executives-insiders
type: concept
sources: ["raw/fmp_docs.md", "raw/fmp_docs.json"]
status: draft
created: 2026-05-22
tags: [fmp, stable, executives, insiders, compensation]
---
# FMP — Executives & Insiders (stable)

^src: raw/fmp_docs.md §Executives & Insiders — ^src: raw/fmp_docs.json sezione="Executives & Insiders"

Sezione dell'API FMP stable con dati sui dirigenti aziendali, insider trading e compensi. Non usata direttamente dal rule engine value investing ma rilevante per l'analisi qualitativa del management.

---

## Endpoint principali

### 1. Key Executives
- **Path**: `GET /stable/key-executives`
- **Parametri**: `symbol*`
- **Response**: `[{title, name, pay, currencyPay, gender, yearBorn, titleSince}]`
- **Uso**: identificazione CEO/CFO/management team per analisi qualitativa

### 2. Executive Compensation
- **Path**: `GET /stable/executive-compensation`
- **Parametri**: `symbol*`
- **Response**: dettaglio compensi (salary, bonus, stock awards, option awards)
- **Uso**: benchmark remunerativo, allineamento incentivi management-azionisti

### 3. Compensation Benchmark
- **Path**: `GET /stable/compensation-benchmark`
- **Parametri**: `industryTitle`
- **Response**: compenso medio per ruolo nel settore

### 4. Insider Trading
- **Path**: `GET /stable/insider-trading`
- **Parametri**: `symbol*`, `limit`
- **Response**: transazioni insider (acquisti/vendite azioni da parte di dirigenti/board)
- **Uso**: segnale qualitativo — acquisti insider = segnale bullish (value investing)

### 5. Insider Roster
- **Path**: `GET /stable/insider-roster`
- **Parametri**: `symbol*`
- **Response**: lista degli insider registrati SEC (Form 3/4/5)

### 6. Insider Roster Statistics
- **Path**: `GET /stable/insider-roster-statistics`
- **Parametri**: `symbol*`
- **Response**: statistiche aggregate acquisti/vendite insider

---

## Rilevanza value investing

L'analisi degli insider e' un elemento qualitativo del metodo Buffett:
- Insider buying sostenuto = management convinto del valore aziendale.
- Compensi eccessivi rispetto ai peer = possibile agent problem (destruzione di valore per azionisti).
- Stabilita' del management team nel tempo = indicatore di visione di lungo termine.

Questa sezione NON e' attualmente integrata nel rule engine quantitativo (fuori scope MVP R1.0).

---

## Cross-link

- Entity: [[fmp-api]]
- Synthesis: [[fmp-api-overview]]
- Runbook: [[fmp-api-quickstart]]
