---
type: synthesis
sources: ["raw/01_Principi_Fondamentali_Value_Investing.md", "raw/03_Analisi_Fondamentale_e_Valutazione.md", "raw/05_Analisi_10K_10Q_e_Regole_Buffett.md", "raw/FMP_Docs_4_Financial_Statements.txt", "raw/FMP_Docs_5_Metrics_and_Ratios.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [synthesis, value-investing, fmp, cross-domain, graham, buffett, financial-statements, metrics]
---
# Come usare FMP API per il Value Investing

> Mappatura operativa tra le metriche del value investing (Graham, Buffett) e gli endpoint FMP API disponibili: quale dato prendere, da quale endpoint, per quale calcolo.

## Contesto

Il dominio value investing (raw 01-05) e il dominio FMP API (raw FMP_Docs 1-8) convergono nell'obiettivo di costruire un agente AI che recupera dati finanziari strutturati e li elabora con i filtri quantitativi di Graham e Buffett. Questa synthesis mappa i punti di contatto concreti. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3C. Le Regole Finanziarie Quantitative]

## Mappa Metrica → Endpoint FMP

### Numero di Graham e criteri difensivi

| Metrica Graham | Endpoint FMP | Concept |
|---|---|---|
| EPS (Utile per Azione) | Key Metrics, Income Statement | [[fmp-financial-statements]] |
| Book Value per Share | Key Metrics, Balance Sheet | [[fmp-financial-statements]] |
| Current Ratio | Financial Ratios | [[fmp-metrics-ratios]] |
| P/E e P/B | Key Metrics, Quote | [[fmp-metrics-ratios]], [[fmp-quotes]] |
| Dividendi 20 anni | Income Statement (storico, limit=80) | [[fmp-financial-statements]] |

### Regole Buffett

| Metrica Buffett | Endpoint FMP | Concept |
|---|---|---|
| ROE | Financial Ratios TTM | [[fmp-metrics-ratios]] |
| ROIC | Key Metrics | [[fmp-metrics-ratios]] |
| Gross Margin, Net Margin | Financial Ratios | [[fmp-metrics-ratios]] |
| Free Cash Flow | Cash Flow Statement | [[fmp-financial-statements]] |
| Debito LT / Utile Netto | Balance Sheet + Income Statement | [[fmp-financial-statements]] |
| CapEx | Cash Flow Statement | [[fmp-financial-statements]] |
| DCF (riferimento consensus) | DCF endpoint | [[fmp-metrics-ratios]] |

[^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Key Metrics & TTM Key Metrics API]

### Analisi qualitativa SEC

Per la lettura dei 10-K/10-Q (Item 1, 1A, 7, 8, Note), FMP non fornisce il testo narrativo del documento SEC. I rendiconti finanziari strutturati (Item 8) sono invece coperti dagli endpoint Financial Statements. Per il testo narrativo (MD&A, Risk Factors), la fonte rimane direttamente EDGAR o il sito IR dell'azienda. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §2. Step Procedurali per l'Analisi di un 10-K / 10-Q]

## Gap residuo

FMP non espone dati narrativi SEC (MD&A, Item 1A). Questo limita la copertura dello Step 2 e Step 3 del [[sec-filings-analysis]] quando si usa solo FMP come fonte dati. Vedi `wiki/gaps.md` per il gap `vi-sec-narrative-gap`.

## Concetti correlati
[[margin-of-safety]]
[[graham-number]]
[[intrinsic-value]]
[[economic-moat]]
[[sec-filings-analysis]]
[[fmp-financial-statements]]
[[fmp-metrics-ratios]]
[[fmp-quotes]]

## Pagine collegate
[[fmp-api-overview]]
[[warren-buffett]]
[[benjamin-graham]]
[[vi-05-analisi-10k-10q-buffett]]
[[webapp-value-investing-spec]]
[[value-investing-rule-engine]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
