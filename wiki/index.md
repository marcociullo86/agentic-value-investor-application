---
id: index
type: index
title: Wiki Index
status: draft
created: 2026-05-20
updated: 2026-05-22 (TSK-068 US-029 fmp-api-quickstart operativo)
tags: [navigation]
---
# Wiki Index — App Template Demo

> Indice navigabile della knowledge base. Rigenerato dal `wiki-keeper` a ogni
> ingest (Fase 4 di `ingest-protocol`).

## Substrate (karpathy-style)

- **Sources** (`wiki/sources/`) — una pagina per documento `raw/` ingerito.
- **Concepts** (`wiki/concepts/`) — concetti di dominio.
- **Entities** (`wiki/entities/`) — persone, organizzazioni, prodotti.
- **Syntheses** (`wiki/syntheses/`) — risposte cross-source consolidate.
- **Runbooks** (`wiki/runbooks/`) — playbook operativi.
- **Incidents** (`wiki/incidents/`) — post-mortem append-only.

## Operational

- **Log** (`wiki/log.md`) — audit trail append-only.
- **Gaps** (`wiki/gaps.md`) — gap di knowledge base aperti / chiusi.
- **Query archive** (`wiki/query/`) — risposte salvate dal `wiki-query`.
- **Lint reports** (`wiki/lint/`) — report del `wiki-lint`.

## Aree tematiche

- **FMP API** — dominio tecnico: autenticazione, ricerca, dati finanziari, quotazioni, metriche via REST API.
- **Value Investing** — dominio analitico: framework Graham/Buffett, valutazione fondamentale, analisi bilanci SEC.
- **Product Spec** — specifica funzionale della WebApp di screening value investing: architettura, regole di business, calcolo valore intrinseco, UI.

---

## Pagine

### Sources (16)

#### FMP API (8)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[fmp-docs-1-auth-and-search]] | FMP_Docs_1_Auth_and_Search.txt | fmp, auth, search |
| [[fmp-docs-2-stock-directory]] | FMP_Docs_2_Stock_Directory.txt | fmp, directory, symbols |
| [[fmp-docs-3-company-info]] | FMP_Docs_3_Company_Info.txt | fmp, company, profile |
| [[fmp-docs-4-financial-statements]] | FMP_Docs_4_Financial_Statements.txt | fmp, financial-statements |
| [[fmp-docs-5-metrics-and-ratios]] | FMP_Docs_5_Metrics_and_Ratios.txt | fmp, metrics, ratios |
| [[fmp-docs-6-quotes-and-prices]] | FMP_Docs_6_Quotes_and_Prices.txt | fmp, quotes, realtime |
| [[fmp-docs-7-executives-and-compensation]] | FMP_Docs_7_Executives_and_Compensation.txt | fmp, executives, compensation |
| [[fmp-docs-8-news-and-estimates]] | FMP_Docs_8_News_and_Estimates.txt | fmp, news, estimates |

#### Value Investing (5)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[vi-01-principi-fondamentali]] | 01_Principi_Fondamentali_Value_Investing.md | value-investing, graham, margin-of-safety |
| [[vi-02-investitore-difensivo-intraprendente]] | 02_L_Investitore_Difensivo_vs_Intraprendente.md | value-investing, defensive-investor |
| [[vi-03-analisi-fondamentale-valutazione]] | 03_Analisi_Fondamentale_e_Valutazione.md | value-investing, graham-number, roe |
| [[vi-04-gestione-rischio-psicologia]] | 04_Gestione_Rischio_Psicologia_Integrazione.md | value-investing, behavioral-finance, moat |
| [[vi-05-analisi-10k-10q-buffett]] | 05_Analisi_10K_10Q_e_Regole_Buffett.md | value-investing, buffett, sec, 10-k |

#### Product Spec (3)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[vi-06-webapp-value-investing-fsd]] | 06_Documento_Funzionale_WebApp_Value_Investing.md | product-spec, rule-engine, kotlin, fmp, webapp |
| [[vi-07-risoluzione-q002-q003]] | 07_Risoluzione_Q002_Q003.md | product-spec, frontend, spa, react, nextjs, screener, gics, q002, q003 |
| [[vi-08-risoluzione-q001-owner-earnings]] | 08_Risoluzione_Q001_Owner_Earnings.md | product-spec, dcf, owner-earnings, buffett, capex, greenwald, q001 |

### Concepts (19)

#### FMP API (9)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-auth]] | Autenticazione via API key (header / query param) |
| [[fmp-search]] | Ricerca titoli per simbolo, nome, CIK, CUSIP/ISIN, screener |
| [[fmp-stock-directory]] | Catalogo simboli, CIK, ETF, tassonomie |
| [[fmp-company-info]] | Profili aziendali, market cap, float, M&A |
| [[fmp-financial-statements]] | Income, balance sheet, cash flow, TTM |
| [[fmp-metrics-ratios]] | Key metrics, ratios, DCF, EV, Altman/Piotroski |
| [[fmp-quotes]] | Quotazioni real-time, batch, premarket/aftermarket, crypto/forex |
| [[fmp-executives]] | Dirigenti, compensi, benchmark retributivo |
| [[fmp-news-estimates]] | Notizie, corporate action, analisti, SEC, ESG |

#### Value Investing (8)

| Pagina | Descrizione |
|--------|-------------|
| [[margin-of-safety]] | Differenza tra valore intrinseco e prezzo: difesa da errori e imprevisti |
| [[mr-market]] | Allegoria Graham: il mercato maniaco-depressivo da sfruttare, non subire |
| [[economic-moat]] | Vantaggio competitivo durevole (4 forme: asset immateriali, switching cost, network, costo) |
| [[intrinsic-value]] | Valore reale del business calcolato su flussi di cassa futuri (DCF, Owner Earnings) |
| [[graham-number]] | Formula di valutazione rapida e 7 criteri filtro per portafoglio difensivo |
| [[behavioral-finance]] | Bias cognitivi (avversione perdita, herding) e contromisure meccaniche |
| [[defensive-vs-enterprising-investor]] | Due profili Graham: difensivo (ETF, ribilanciamento) vs intraprendente (analisi attiva) |
| [[sec-filings-analysis]] | Metodologia 5-step per analisi 10-K/10-Q (business, rischi, MD&A, rendiconti, note) |

#### Product Spec (4)

| Pagina | Descrizione |
|--------|-------------|
| [[value-investing-rule-engine]] | Motore regole quantitativo: ROE/ROIC/Margin/CurrentRatio/CapEx + DCF + MoS traffic light |
| [[webapp-architecture-vi]] | Architettura 3-layer: Next.js SPA, Spring Boot 3.5 backend, PostgreSQL; endpoint Sprint 2 su `master` |
| [[analysis-api-pipeline]] | `GET /api/analysis/{ticker}`: 7 signals + Graham + DCF + MoS + persistenza |
| [[openapi-contract-check]] | springdoc 2.8.16 (webmvc-api), MockMvc `/api/openapi.json`, gate CI `contract-check` |

### Entities (3)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-api]] | Financial Modeling Prep — provider REST API dati finanziari |
| [[benjamin-graham]] | Padre fondatore del value investing (Mr. Market, Margin of Safety, Graham Number) |
| [[warren-buffett]] | Evoluisce Graham con moat, cerchio di competenza, Owner Earnings |

### Syntheses (3)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-api-overview]] | Panoramica architetturale cross-source dell'API FMP |
| [[value-investing-fmp-integration]] | Mappa metrica value investing → endpoint FMP API (cross-domain) |
| [[webapp-value-investing-spec]] | Specifica cross-domain: requisiti funzionali → regole Rule Engine → endpoint FMP → architettura |

### Runbooks (4)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-api-quickstart]] | Integrazione FMP: quickstart, rate limit (gap), URL base (gap), errori HTTP (gap), ADR-016 ref |
| [[sec-10k-10q-analysis-playbook]] | Playbook 7-step per analisi 10-K/10-Q con metodo Buffett e FMP API |
| [[value-investing-rule-engine-runbook]] | Implementazione step-by-step del Rule Engine: acquisizione FMP, validazione regole, DCF, MoS |
| [[runbook-openapi-contract-check]] | Troubleshooting contract-check: Boot 3.5, PatternParseException, MockMvc vs OpenAPIService |
