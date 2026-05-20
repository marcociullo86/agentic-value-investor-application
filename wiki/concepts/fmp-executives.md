---
type: concept
sources: ["raw/FMP_Docs_7_Executives_and_Compensation.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, executives, compensation, governance, esg]
---
# Dirigenti e Compensi FMP

> FMP espone dati dettagliati sui dirigenti aziendali e i loro pacchetti retributivi, con capacità di benchmark settoriale per anno.

## Dettaglio

### Profili Dirigenti
Company Executives API restituisce per ogni dirigente: nome, titolo, genere, anno di nascita. Il parametro `active: true` filtra solo i dirigenti attualmente in carica, escludendo gli ex-dirigenti. [^src: raw/FMP_Docs_7_Executives_and_Compensation.txt §Company Executives API]

### Compensi
Executive Compensation API fornisce la struttura retributiva completa per i dirigenti USA: stipendio base, bonus, stock award, option award e altri benefit. Disponibile per simbolo. [^src: raw/FMP_Docs_7_Executives_and_Compensation.txt §Executive Compensation API]

### Benchmark Retributivo
Executive Compensation Benchmark permette di confrontare i compensi dei dirigenti con i peer di settore per un determinato anno. Utile per analisi di governance e valutazione dell'allineamento tra compensi e performance. [^src: raw/FMP_Docs_7_Executives_and_Compensation.txt §Executive Compensation Benchmark API]

## Nota sulla copertura geografica
Gli endpoint di compensazione sono limitati al mercato USA (indicazione USA Flag nel raw). Il profilo dirigenti è globale. [^src: raw/FMP_Docs_7_Executives_and_Compensation.txt §Company Executives & Compensation]

## Concetti correlati
[[fmp-company-info]]
[[fmp-news-estimates]]

## Pagine collegate
[[fmp-docs-7-executives-and-compensation]]
[[fmp-api]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
