---
type: concept
sources: ["raw/FMP_Docs_4_Financial_Statements.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, financial-statements, income, balance-sheet, cash-flow, ttm]
---
# Rendiconti Finanziari FMP

> FMP fornisce i tre rendiconti contabili fondamentali (conto economico, stato patrimoniale, rendiconto finanziario) in forma periodica (trimestrale/annuale) e TTM per qualsiasi società quotata globalmente.

## Dettaglio

### I Tre Rendiconti
Gli endpoint Income Statement, Balance Sheet e Cash Flow Statement coprono l'analisi contabile completa: [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API]

| Rendiconto | Contenuto principale |
|------------|---------------------|
| Income Statement | Ricavi, costi, utile operativo, utile netto |
| Balance Sheet | Attivo, passivo, patrimonio netto |
| Cash Flow | Flussi operativi, d'investimento e finanziari |

### Periodi Supportati
Il parametro `period` accetta: `Q1`, `Q2`, `Q3`, `Q4` (trimestri), `FY` / `annual` (anno fiscale), `quarter` (alias generico). Il limite massimo per chiamata è 1000 record. [^src: raw/FMP_Docs_4_Financial_Statements.txt §Income Statement, Balance Sheet, Cash Flow Statement API]

### TTM (Trailing Twelve Months)
I TTM Financial Statements aggregano i dati degli ultimi 12 mesi mobili, eliminando la stagionalità e fornendo una visione corrente della performance. [^src: raw/FMP_Docs_4_Financial_Statements.txt §TTM Financial Statements]

### Latest Financial Statements
Endpoint di discovery che restituisce gli ultimi rendiconti disponibili per tutte le società, paginabile con `page` e `limit`. Utile per pipeline di aggiornamento incrementale. [^src: raw/FMP_Docs_4_Financial_Statements.txt §Latest Financial Statements API]

## Concetti correlati
[[fmp-metrics-ratios]]
[[fmp-company-info]]

## Pagine collegate
[[fmp-docs-4-financial-statements]]
[[fmp-api]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
