---
type: source
sources: ["raw/FMP_Docs_7_Executives_and_Compensation.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, api, executives, compensation, governance]
---
# FMP Docs 7 — Company Executives & Compensation

> FMP fornisce dati sui dirigenti aziendali (nome, titolo, genere, anno di nascita), compensi dettagliati e benchmark retributivi per confronto tra pari.

## Contesto

Gli endpoint di questa sezione supportano l'analisi della governance aziendale, dai profili dei dirigenti ai pacchetti retributivi, fino al benchmarking settoriale. [^src: raw/FMP_Docs_7_Executives_and_Compensation.txt §Company Executives & Compensation]

## Endpoint

### Company Executives
Informazioni dettagliate sui dirigenti aziendali: nome, titolo, genere, anno di nascita. Parametri: `symbol`* (stringa), `active` (stringa, es. `true`). [^src: raw/FMP_Docs_7_Executives_and_Compensation.txt §Company Executives API]

### Executive Compensation
Dati completi di compensazione: stipendi, stock award e altri benefit (USA). Parametri: `symbol`* (stringa). [^src: raw/FMP_Docs_7_Executives_and_Compensation.txt §Executive Compensation API]

### Executive Compensation Benchmark
Confronto della retribuzione dei dirigenti rispetto ai pari settore. Parametri: `year` (numero). [^src: raw/FMP_Docs_7_Executives_and_Compensation.txt §Executive Compensation Benchmark API]

## Concetti correlati
[[fmp-executives]]

## Pagine collegate
[[fmp-api]]
[[fmp-docs-3-company-info]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
