---
type: synthesis
sources: ["raw/FMP_Docs_1_Auth_and_Search.txt", "raw/FMP_Docs_2_Stock_Directory.txt", "raw/FMP_Docs_3_Company_Info.txt", "raw/FMP_Docs_4_Financial_Statements.txt", "raw/FMP_Docs_5_Metrics_and_Ratios.txt", "raw/FMP_Docs_6_Quotes_and_Prices.txt", "raw/FMP_Docs_7_Executives_and_Compensation.txt", "raw/FMP_Docs_8_News_and_Estimates.txt"]
status: draft
created: 2026-05-20
updated: 2026-05-20
tags: [fmp, overview, synthesis, api-design]
---
# Panoramica API FMP — Sintesi Cross-Source

> FMP API e' una piattaforma REST per dati finanziari che copre 8 domini funzionali con autenticazione unificata via API key; ogni dominio ha endpoint indipendenti con parametri ortogonali.

## Pattern Architetturale Comune

Tutti gli endpoint FMP condividono un pattern uniforme: [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Authorization]

1. **Autenticazione**: API key obbligatoria (header o query param)
2. **Parametro primario**: tipicamente `symbol`* (stringa) come identificatore principale
3. **Paginazione**: `page` + `limit` per endpoint di lista
4. **Periodo**: `period` + `limit` per serie storiche
5. **Range temporale**: `from` + `to` per filtri su date

## Mappa dei Domini

| Dominio | Scope | Copertura |
|---------|-------|-----------|
| Auth & Search | Identificazione strumenti | Globale + USA |
| Stock Directory | Catalogo completo | Globale + USA |
| Company Info | Dati fondamentali aziendali | Globale + USA |
| Financial Statements | Rendiconti contabili | Globale |
| Metrics & Ratios | Analisi quantitativa | Globale |
| Quotes & Prices | Prezzi real-time | Globale (azioni, crypto, forex, indici) |
| Executives | Governance e compensi | Globale (profili) + USA (compensi) |
| News & Estimates | Informativo e sentiment | Globale + USA (SEC) |

## Identificatori Supportati

FMP supporta quattro identificatori per i titoli azionari: ticker `symbol` (universale), `cik` (SEC/USA), `cusip` (Nord America), `isin` (standard ISO 6166 globale). [^src: raw/FMP_Docs_1_Auth_and_Search.txt §Company Search]

## TTM come Modalita' Trasversale

Gli endpoint TTM (Trailing Twelve Months) sono disponibili per Financial Statements, Key Metrics, Financial Ratios ed Enterprise Value, offrendo una vista normalizzata sulla stagionalita'. [^src: raw/FMP_Docs_4_Financial_Statements.txt §TTM Financial Statements]

## Gap identificati

Nessun endpoint di rate limiting o error code documentato nei raw disponibili. Per dettagli su limiti di frequenza e codici di errore HTTP, vedere `wiki/gaps.md`.

## Concetti correlati
[[fmp-auth]]
[[fmp-search]]
[[fmp-financial-statements]]
[[fmp-metrics-ratios]]
[[fmp-quotes]]

## Pagine collegate
[[fmp-api]]
[[fmp-api-quickstart]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
