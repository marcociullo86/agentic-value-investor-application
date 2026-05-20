---
type: concept
sources: ["raw/FMP_Docs_5_Metrics_and_Ratios.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, metrics, ratios, dcf, enterprise-value, altman, piotroski]
---
# Metriche, Indici e Valutazione FMP

> FMP offre metriche chiave, indici fondamentali, punteggi di salute finanziaria (Altman Z-Score, Piotroski Score), Enterprise Value e valutazione DCF per l'analisi quantitativa delle società quotate.

## Dettaglio

### Key Metrics
Aggregato di indicatori principali quali revenue, net income, P/E ratio, book value per share, free cash flow per share. Disponibile per periodo e in modalità TTM. [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Key Metrics & TTM Key Metrics API]

### Financial Ratios
Indici strutturati per categoria: [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Financial Ratios & TTM Financial Ratios API]

- **Redditività**: ROE, ROA, gross profit margin, net profit margin
- **Liquidità**: current ratio, quick ratio
- **Efficienza**: asset turnover, inventory turnover

### Financial Scores
Altman Z-Score valuta il rischio di default per le società manifatturiere quotate. Il Piotroski F-Score misura la forza finanziaria complessiva su 9 criteri binari. Entrambi richiedono solo `symbol` come parametro. [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Financial Scores API]

### Enterprise Value
Calcola l'EV come capitalizzazione + debito netto - liquidità. Disponibile sia puntuale sia TTM, con supporto per `period` e `limit`. [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Enterprise Value & TTM Enterprise Value API]

### DCF (Discounted Cash Flow)
Restituisce il valore intrinseco calcolato da FMP tramite modello DCF. Utile come riferimento di consensus, non come valutazione proprietaria. [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Discounted Cash Flow (DCF) API]

## Concetti correlati
[[fmp-financial-statements]]
[[fmp-api]]

## Pagine collegate
[[fmp-docs-5-metrics-and-ratios]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
