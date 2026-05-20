---
type: source
sources: ["raw/FMP_Docs_4_Financial_Statements.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, financial-statements, income, balance-sheet, cash-flow]
---
# FMP Docs 4 — Financial Statements

> FMP espone conto economico, stato patrimoniale e rendiconto finanziario per simboli globali, con supporto per dati trimestrali, annuali e Trailing Twelve Months (TTM).

## Contesto

Gli endpoint Financial Statements consentono di recuperare i tre principali rendiconti contabili per qualsiasi società quotata, sia in forma puntuale che aggregata (TTM). [^src: raw/FMP_Docs_4_Financial_Statements.txt §Financial Statements]

## Endpoint

### Income Statement, Balance Sheet, Cash Flow Statement
Rendiconti finanziari dettagliati. Parametri: `symbol`* (stringa), `limit` (numero, max 1000), `period` (stringa: Q1, Q2, Q3, Q4, FY, annual, quarter). [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API]

### Latest Financial Statements
Ultimi rendiconti finanziari disponibili per tutte le società. Parametri: `page` (numero), `limit` (numero). [^src: raw/FMP_Docs_4_Financial_Statements.txt §Latest Financial Statements API]

### TTM Financial Statements (Income, Balance Sheet, Cashflow)
Dati finanziari su base Trailing Twelve Months. Parametri: `symbol`* (stringa), `limit` (numero). [^src: raw/FMP_Docs_4_Financial_Statements.txt §TTM Financial Statements]

## Note sui periodi

Il parametro `period` accetta i valori `Q1`, `Q2`, `Q3`, `Q4` per i trimestri, `FY` o `annual` per l'anno fiscale completo, `quarter` come alias generico trimestrale. Il limite massimo di record restituibili per chiamata è 1000. [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API]

## Concetti correlati
[[fmp-financial-statements]]

## Pagine collegate
[[fmp-api]]
[[fmp-docs-5-metrics-and-ratios]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
