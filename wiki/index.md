---
id: index
type: index
title: Wiki Index
status: draft
created: 2026-05-20
updated: 2026-05-22 (v2026-05-22 FMP stable migration + ingest L'Investitore Intelligente B. Graham)
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

- **FMP API** — dominio tecnico: API stable (base URL `https://financialmodelingprep.com/stable/`), 263 endpoint, autenticazione, ricerca, dati finanziari, quotazioni, metriche. *(Migrazione da v3 completata 2026-05-22)*
- **Value Investing** — dominio analitico: framework Graham/Buffett, valutazione fondamentale, analisi bilanci SEC.
- **Product Spec** — specifica funzionale della WebApp di screening value investing: architettura, regole di business, calcolo valore intrinseco, UI.

---

## Pagine

### Sources (9)

#### FMP API (1)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[fmp-docs]] | fmp_docs.md + fmp_docs.json (263 endpoint stable) | fmp, stable, api |

#### Value Investing (6)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[vi-01-principi-fondamentali]] | 01_Principi_Fondamentali_Value_Investing.md | value-investing, graham, margin-of-safety |
| [[vi-02-investitore-difensivo-intraprendente]] | 02_L_Investitore_Difensivo_vs_Intraprendente.md | value-investing, defensive-investor |
| [[vi-03-analisi-fondamentale-valutazione]] | 03_Analisi_Fondamentale_e_Valutazione.md | value-investing, graham-number, roe |
| [[vi-04-gestione-rischio-psicologia]] | 04_Gestione_Rischio_Psicologia_Integrazione.md | value-investing, behavioral-finance, moat |
| [[vi-05-analisi-10k-10q-buffett]] | 05_Analisi_10K_10Q_e_Regole_Buffett.md | value-investing, buffett, sec, 10-k |
| [[intelligent-investor]] | raw/investitore intelligente.txt (B. Graham, ed. it. 2020) | value-investing, graham, mr-market, margin-of-safety, defensive-investor |

#### Product Spec (3)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[vi-06-webapp-value-investing-fsd]] | 06_Documento_Funzionale_WebApp_Value_Investing.md | product-spec, rule-engine, kotlin, fmp, webapp |
| [[vi-07-risoluzione-q002-q003]] | 07_Risoluzione_Q002_Q003.md | product-spec, frontend, spa, react, nextjs, screener, gics, q002, q003 |
| [[vi-08-risoluzione-q001-owner-earnings]] | 08_Risoluzione_Q001_Owner_Earnings.md | product-spec, dcf, owner-earnings, buffett, capex, greenwald, q001 |

### Concepts (22)

#### FMP API stable (13)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-company-search]] | Ricerca titoli: search-symbol, search-name, CIK, CUSIP, ISIN (stable) |
| [[fmp-company-information]] | Profili aziendali, market cap, screener parametrico (stable) |
| [[fmp-financial-statements-stable]] | Income, balance sheet, cash flow, TTM — 263 endpoint (stable) |
| [[fmp-key-metrics-ratios]] | ROE, ROIC, BVPS, ratios, DCF pre-calcolato (stable) |
| [[fmp-stock-lists]] | Catalogo simboli, ETF list, available-traded (stable) |
| [[fmp-quotes-stable]] | Quotazioni real-time, batch, storico OHLCV (stable) |
| [[fmp-executives-insiders]] | Dirigenti, insider trading, compensi (stable) |
| [[fmp-news-media]] | News, articoli FMP, press releases (stable) |
| [[fmp-market-performance]] | Sector performance, gainers, losers (stable) |
| [[fmp-commodities]] | Materie prime: oro, petrolio, argento (stable) |
| [[fmp-cryptocurrency]] | Criptovalute: BTC, ETH (stable) |
| [[fmp-forex]] | Tassi di cambio valutari (stable) |
| [[fmp-etfs-funds]] | ETF e fondi comuni: info, holdings (stable) |

#### Value Investing (14)

| Pagina | Descrizione |
|--------|-------------|
| [[margin-of-safety]] | Differenza tra valore intrinseco e prezzo: difesa da errori e imprevisti |
| [[mr-market]] | Allegoria Graham Cap.8: socio maniaco-depressivo — parabola originale + analisi |
| [[economic-moat]] | Vantaggio competitivo durevole (4 forme: asset immateriali, switching cost, network, costo) |
| [[intrinsic-value]] | Valore reale del business calcolato su flussi di cassa futuri (DCF, Owner Earnings) |
| [[graham-number]] | Formula di valutazione rapida e 7 criteri filtro per portafoglio difensivo |
| [[behavioral-finance]] | Bias cognitivi (avversione perdita, herding) e contromisure meccaniche |
| [[defensive-vs-enterprising-investor]] | Due profili Graham: difensivo (ETF, ribilanciamento) vs intraprendente (analisi attiva) |
| [[sec-filings-analysis]] | Metodologia 5-step per analisi 10-K/10-Q (business, rischi, MD&A, rendiconti, note) |
| [[investment-vs-speculation]] | Definizione canonica Graham: investimento = analisi + sicurezza capitale + rendimento adeguato |
| [[seven-criteria-defensive-stock-selection]] | I 7 filtri Cap.14 per il portafoglio difensivo — soglie e mapping FMP |
| [[net-net-stocks]] | Acquisto sotto 2/3 NCAV: strategia cigar-butt Graham; rarita' nei mercati sviluppati 2026 |
| [[market-fluctuations-graham]] | Cap.8: volatilita' non e' rischio, il mercato serve — non comanda |
| [[inflation-investing-graham]] | Cap.2: azioni con pricing power come hedge parziale all'inflazione |
| [[superinvestors-graham-doddsville]] | Appendice 1 / Buffett 1984: prova empirica — 9 fondi Graham sovraperformano il mercato |

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
| [[fmp-api]] | Financial Modeling Prep — provider REST API stable (263 endpoint, base URL `/stable/`) |
| [[benjamin-graham]] | Padre fondatore del value investing — biografia, Graham-Newman, allievi, contributi (aggiornato v2026-05-22) |
| [[warren-buffett]] | Evoluisce Graham con moat, cerchio di competenza, Owner Earnings (aggiornato v2026-05-22) |

### Syntheses (4)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-api-overview]] | Panoramica architetturale cross-source dell'API FMP stable (263 endpoint, 13 sezioni) |
| [[value-investing-fmp-integration]] | Mappa metrica value investing → endpoint FMP stable; invariante ADR-004 |
| [[webapp-value-investing-spec]] | Specifica cross-domain: requisiti funzionali → regole Rule Engine → endpoint FMP → architettura |
| [[graham-investing-philosophy]] | Sintesi cross-domain: 5 strati del framework Graham, genealogia Graham→Buffett, evidenza Doddsville |

### Runbooks (6)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-api-quickstart]] | Guida operativa FMP stable: auth, search, profile, statements, key-metrics, screener |
| [[sec-10k-10q-analysis-playbook]] | Playbook 7-step per analisi 10-K/10-Q con metodo Buffett e FMP API |
| [[value-investing-rule-engine-runbook]] | Implementazione step-by-step del Rule Engine: acquisizione FMP, validazione regole, DCF, MoS |
| [[runbook-openapi-contract-check]] | Troubleshooting contract-check: Boot 3.5, PatternParseException, MockMvc vs OpenAPIService |
| [[defensive-investor-checklist]] | 7 criteri Graham Cap.14 step-by-step con mapping FMP stable e WebApp signals |
| [[enterprising-investor-checklist]] | Criteri Graham Cap.15 step-by-step: liquidita', P/E≤9, net-net NCAV con FMP stable |

