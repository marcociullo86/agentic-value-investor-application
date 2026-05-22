---
type: concept
sources: ["raw/03_Analisi_Fondamentale_e_Valutazione.md", "raw/05_Analisi_10K_10Q_e_Regole_Buffett.md"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [value-investing, intrinsic-value, dcf, owner-earnings, roic, roe, graham]
---
# Valore Intrinseco (Intrinsic Value)

> Il valore reale di un business calcolato sui flussi di cassa futuri attualizzati, indipendente dal prezzo di mercato: la misura assoluta di riferimento per il value investor.

## Contesto

Il valore intrinseco e' il denominatore del [[margin-of-safety]]: il prezzo di mercato si confronta con esso per stabilire se esiste un'opportunita' di acquisto. Graham lo approssima con il [[graham-number]]; Buffett lo calcola tramite Owner Earnings e DCF. [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §4. Il Margine di Sicurezza (Margin of Safety)]

## Dettaglio

### Approccio Graham: Numero di Graham

Per le azioni difensive, Graham usa la radice quadrata del prodotto `22.5 x EPS x Book Value per Share` come stima rapida del prezzo massimo sostenibile. Vedi [[graham-number]] per la formula completa. [^src: raw/03_Analisi_Fondamentale_e_Valutazione.md §2. Il Numero di Graham e Metriche Avanzate]

### Approccio Buffett: Owner Earnings e DCF

Buffett definisce gli Owner Earnings come una forma modificata del Free Cash Flow che riflette la vera cassa generabile per gli azionisti. Il processo di valutazione segue tre passi: [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §4. Il Margine di Sicurezza (Margin of Safety)]

1. Stima degli Owner Earnings storici e futuri.
2. Attualizzazione con un tasso di sconto appropriato (DCF).
3. Applicazione di uno sconto finale (25-30%) come margine di sicurezza.

### Metriche proxy della qualita' del valore

Un valore intrinseco elevato e stabile e' supportato da: [^src: raw/05_Analisi_10K_10Q_e_Regole_Buffett.md §3C. Le Regole Finanziarie Quantitative]

- **ROE > 15%** costante (senza leva eccessiva).
- **ROIC > 12-15%**: misura la vera redditività incluso il debito.
- **Free Cash Flow** positivo e crescente (non assorbito da magazzino o crediti inesigibili).

Questi dati si ottengono via [[fmp-key-metrics-ratios]] (DCF endpoint, Key Metrics TTM) e [[fmp-financial-statements-stable]] (Cash Flow Statement).

### Il DCF FMP come riferimento

L'endpoint DCF di FMP restituisce un valore intrinseco calcolato da FMP stesso, utile come riferimento di consensus ma non come valutazione proprietaria sostitutiva. [^src: raw/FMP_Docs_5_Metrics_and_Ratios.txt §Discounted Cash Flow (DCF) API]

## Concetti correlati
[[margin-of-safety]]
[[graham-number]]
[[economic-moat]]
[[fmp-key-metrics-ratios]]
[[fmp-financial-statements-stable]]
[[investment-vs-speculation]]
[[market-fluctuations-graham]]

## Pagine collegate
[[vi-03-analisi-fondamentale-valutazione]]
[[vi-05-analisi-10k-10q-buffett]]
[[intelligent-investor]]
[[warren-buffett]]
[[benjamin-graham]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
