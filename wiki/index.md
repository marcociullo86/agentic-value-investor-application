---
id: index
type: index
title: Wiki Index
status: draft
created: 2026-05-20
updated: 2026-05-22 (v2026-05-22 FMP stable migration + ingest L'Investitore Intelligente B. Graham)
updated: 2026-05-22 (TSK-068 US-029 fmp-api-quickstart operativo)
updated: 2026-05-25 (gap-close run: +2 concept EP-011, +1 extend analysis-api-pipeline, +1 note value-investor-bot-architecture)
updated: 2026-05-25 (gap-close massivo 9 gap + ingest fmp_mcp-server.txt: +1 concept fmp-mcp-integration, +1 source fmp-mcp-server)
updated: 2026-05-26 (indice riallineato: +6 concept agent.py, +1 synthesis, fix conteggi, dedup runbook)
updated: 2026-05-26 (ingest requisiti-funzionali-fintech.md: +1 source, +6 concept, +1 synthesis, +1 runbook)
updated: 2026-05-27 (+1 incident run test FE locale)
updated: 2026-05-27 (doc-sync factory v2.13 + CQRL enabled: +1 concept agentic-factory-v213, +1 sezione Factory/Tooling)
updated: 2026-05-28 (reconcile L1-L4 post EP-019 CQRL bonifica: update agentic-factory-v213 outcome 224/224 pass, incident allineato TSK-239 done)
updated: 2026-05-29 (+1 incident F5-logout CSRF bugfix; update auth-guard-frontend + fintech-security-compliance)
updated: 2026-06-05 (ingest Murphy 1999 OCR: +1 source reale (sostituisce placeholder), +7 concept TA, +1 entity john-murphy; gap murphy-technical-analysis-extraction-failure chiuso)
updated: 2026-06-05 (+3 syntheses TA timing: ta-entry-timing-stock-detail, ta-stop-placement-position-sizing, ta-vs-vi-decision-layer — layer advisory timing per pagina dettaglio titolo, caso COPART)
tags: [navigation]
---
# Wiki Index — App Template Demo

> Indice navigabile della knowledge base. Rigenerato dal `wiki-keeper` a ogni
> ingest (Fase 4 di `ingest-protocol`).

## Convenzione `domain:` (v2026-06-05)

Ogni pagina di contenuto della wiki porta un campo frontmatter `domain:` con uno di tre valori, accompagnato da un tag coerente:

| Valore | Tag | Uso |
|---|---|---|
| `value-investing` | `vi-domain` | Pagine Graham/Buffett: intrinsic value, margin of safety, economic moat, owner earnings, graham number, DCF, rule-engine VI, net-net, mr-market, superinvestors, behavioral finance VI, SEC filings analysis, clone investing 13-F. Filtrare: cercare `vi-domain` nei tag o `domain: value-investing` nel frontmatter. |
| `technical-analysis-trading` | `ta-domain` | Pagine Elder/Murphy: analisi tecnica, triple screen, impulse system, risk management trader, psicologia trading. Filtrare: `ta-domain` o `domain: technical-analysis-trading`. |
| `platform` | `platform-domain` | Infrastruttura e meta: FMP API (tutti i fmp-* concept ed entity), agentic-factory, parallel-scheduler, correlation-id-tracing, structured-logging, openapi-contract-check, material-design, auth-guard-frontend, fintech-security-compliance, pgvector, arctic-embed, analysis-api-pipeline, webapp-architecture-vi (engineering), value-investor-bot-architecture, runbook migrazioni, CQRL. Filtrare: `platform-domain` o `domain: platform`. |

Regola di filtro rapido: quando una feature value-investing necessita di contesto teorico, filtrare `domain: value-investing`; per contesto infrastrutturale filtrare `domain: platform`; per TA/trading filtrare `domain: technical-analysis-trading`. Le pagine meta-operative (`lint/`, `incidents/`, `log.md`, `gaps.md`, `index.md`) non portano `domain:`.

---

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
- **Fintech Hardening** — iterazione trasversale: logging strutturato, notifiche errori, accessibilita M3/WCAG, AuthGuard, sicurezza/compliance fintech.

---

## Pagine

### Sources (13)

#### FMP API (2)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[fmp-docs]] | fmp_docs.md + fmp_docs.json (263 endpoint stable) | fmp, stable, api |
| [[fmp-mcp-server]] | fmp_mcp-server.txt (annuncio MCP Server FMP) | fmp, mcp, model-context-protocol, llm |

#### Technical Analysis / Trading (1)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[murphy-technical-analysis-financial-markets-1999]] | raw/2026-06-05-technical-analysis-financial-markets-1999.txt (J.J. Murphy, 1999, OCR) | technical-analysis, trading, ta-domain |

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

#### Fintech Hardening (1)

| Pagina | Documento sorgente | Tag |
|--------|--------------------|-----|
| [[requisiti-funzionali-fintech]] | requisiti-funzionali-fintech.md | fintech, hardening, logging, accessibility, security, auth-guard, material-design-3 |

### Concepts (54)

#### FMP API stable (14)

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
| [[fmp-mcp-integration]] | FMP MCP Server: cos'è MCP, vantaggi vs REST adapter, casi d'uso LLM agent, considerazione architetturale |

#### Value Investing (19)

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
| [[dcf-discount-rate-policy]] | Scelta del discount rate DCF: r=4.5% (Buffett risk-free) vs r=9.5% (WACC standard CFA) — impatto su valore intrinseco |
| [[owner-earnings-formula-variants]] | Tre varianti Owner Earnings: formula Buffett 1986, metodo Greenwald maintenance CapEx, semplificazione agent.py |
| [[munger-inversion-rag]] | Analisi qualitativa 10-K/10-Q con principio inversione Munger: 10 query RAG su SEC EDGAR per rischi catastrofici |
| [[panic-buy-vs-value-trap-detection]] | Discriminazione panic buy (business solido + panico temporaneo) vs value trap (declino strutturale) via drawdown + news sentiment LLM |
| [[clone-investing-13f-overlay]] | Tecnica Pabrai/Spier: overlay 13-F trimestrali SEC EDGAR da fondi value (Berkshire, Pershing Square) per universo pre-approvato |

#### Product Spec / EP-011 (7)

| Pagina | Descrizione |
|--------|-------------|
| [[value-investing-rule-engine]] | Motore regole quantitativo: ROE/ROIC/Margin/CurrentRatio/CapEx + DCF + MoS traffic light |
| [[webapp-architecture-vi]] | Architettura 3-layer: Next.js SPA, Spring Boot 3.5 backend, PostgreSQL; endpoint Sprint 2 su `master` |
| [[analysis-api-pipeline]] | `GET /api/analysis/{ticker}` (13 signals: 7 Buffett + 6 Graham + Graham Number + DCF + MoS) + `GET /api/analysis/{ticker}/deep` (EP-011 deep analysis, payload esteso, invoke_llm policy) |
| [[openapi-contract-check]] | springdoc 2.8.16 (webmvc-api), MockMvc `/api/openapi.json`, gate CI `contract-check` |
| [[pgvector-vector-store]] | Vector store EP-011: schema `filing_chunks`, HNSW (m=16, ef=64), chunking 6000/400 char, query similarity pgvector |
| [[arctic-embed-l-v2]] | Modello embedding EP-011: `Qwen/Qwen3-Embedding-0.6B` (1024-dim, 32K ctx, MTEB ~64.6); A/B test via `embeddings.model.name`; Arctic Embed L v2.0 come fallback |
| [[value-investor-bot-architecture]] | Agent.py v2.6.1: architettura LangGraph multi-agente (screener → SEC RAG → news sentiment → DCF → verdetto), prototipo Python delle funzionalità EP-010/011/012 |

#### Technical Analysis / Trading — Murphy (8)

| Pagina | Descrizione |
|--------|-------------|
| [[technical-analysis-trading-domain]] | Mappa di separazione dominio TA/value investing: differenze, punti di contatto, avvisi per dev-agent |
| [[dow-theory]] | 6 principi Dow: market discounts all, tre trend, tre fasi, conferma indici, volume, trend presunto fino a inversione |
| [[trend-trendlines-support-resistance]] | Definizioni di trend (up/down/sideways), support/resistance, role reversal, trendlines, channels, retracements, gaps |
| [[chart-patterns-reversal-continuation]] | Reversal: head&shoulders, double/triple top/bottom, saucers, spikes. Continuation: triangoli, flags, pennants, wedge, rectangle. Candlestick patterns. |
| [[moving-averages-ta]] | SMA/EMA/WMA, dual crossover system, Bollinger Bands, weekly rule, adaptive MA, cicli e armonici |
| [[oscillators-momentum-rsi]] | Momentum, ROC, MACD+histogram, RSI, Stochastics, Williams %R, CCI, contrary opinion |
| [[volume-open-interest]] | OBV, regole volume/open-interest per futures, COT report (commercials), blowoffs e selling climaxes |
| [[intermarket-analysis-murphy]] | Dollar→Commodities→Bonds→Stocks (catena inflazione/deflazione), sector rotation, relative strength analysis |

#### Factory / Tooling (2)

| Pagina | Descrizione |
|--------|-------------|
| [[agentic-factory-v213]] | Factory llm-wiki++ v2.13: layer L1-L5 + CQRL, multi-adapter (claude+cursor), `/review`, `/repo-sync`, `factory.config.yaml` annotato. CQRL abilitato 2026-05-27. EP-019 bonifica Sprint 16: 224/224 TSK pass, 0 reject. |
| [[parallel-scheduler]] | DAG-driven dispatch: `depends_on`, `code_path`, wave parallele (PATTERN §18, v2.11). |

#### Fintech Hardening (6)

| Pagina | Descrizione |
|--------|-------------|
| [[structured-logging-backend]] | REQ-01: logging JSON strutturato, correlation ID, redazione PII, performance 2ms p99 |
| [[frontend-error-notifications]] | REQ-02: NotificationService centralizzato, error mapping, WCAG 2.2 AA, correlation ID copiabile |
| [[material-design-3-accessibility]] | REQ-03: design token M3, light/dark theme, shape system, motion, WCAG 2.2 AA baseline |
| [[auth-guard-frontend]] | REQ-04: protezione rotte, roles/permissions, token lifecycle, refresh automatico, logout |
| [[fintech-security-compliance]] | REQ-05: PII policy, token storage, defense-in-depth, PCI-DSS condizionale, threat model, security events |
| [[correlation-id-tracing]] | Cross-cutting: propagazione X-Correlation-Id end-to-end backend-frontend |

### Entities (5)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-api]] | Financial Modeling Prep — provider REST API stable (263 endpoint, base URL `/stable/`) |
| [[benjamin-graham]] | Padre fondatore del value investing — biografia, Graham-Newman, allievi, contributi (aggiornato v2026-05-22) |
| [[warren-buffett]] | Evoluisce Graham con moat, cerchio di competenza, Owner Earnings (aggiornato v2026-05-22) |
| [[alexander-elder]] | Trader e psichiatra — autore "The New Trading for a Living" (2014); Triple Screen System, psicologia trading |
| [[john-murphy]] | Technical analyst — autore "Technical Analysis of the Financial Markets" (1999); fondatore analisi intermarket |

### Syntheses (9)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-api-overview]] | Panoramica architetturale cross-source dell'API FMP stable (263 endpoint, 13 sezioni) |
| [[value-investing-fmp-integration]] | Mappa metrica value investing → endpoint FMP stable; invariante ADR-004 |
| [[webapp-value-investing-spec]] | Specifica cross-domain: requisiti funzionali → regole Rule Engine → endpoint FMP → architettura |
| [[graham-investing-philosophy]] | Sintesi cross-domain: 5 strati del framework Graham, genealogia Graham→Buffett, evidenza Doddsville |
| [[graham-modern-bot-methodologies]] | Sintesi cross-domain Graham 1973 ↔ pratiche moderne 2026 ↔ agent.py ↔ Rule Engine Kotlin: convergenze, divergenze, scelte metodologiche |
| [[fintech-hardening-requirements-map]] | Mappa cross-domain: 5 REQ iterazione fintech, dipendenze inter-REQ, impatto sull'architettura esistente |
| [[ta-entry-timing-stock-detail]] | Timing dell'entry su un titolo promosso dal verdetto VI: Triple Screen Elder, SMA200 + RSI (badge advisory esistenti), support/resistance Murphy per il livello d'ingresso |
| [[ta-stop-placement-position-sizing]] | Stop placement ancorato a struttura (Murphy) + 2% Rule e 6% Rule Elder: caso COPART, relazione ampiezza stop / dimensione posizione |
| [[ta-vs-vi-decision-layer]] | TA come layer di timing advisory che completa il verdetto VI: "cosa" (fondamentale) vs "quando" (tecnico), rischi di sovra-affidarsi alla TA, lente di valore applicata |

#### Technical Analysis / Trading — Syntheses (3)

| Pagina | Descrizione |
|--------|-------------|
| [[ta-entry-timing-stock-detail]] | Quando comprare: Triple Screen applicato ai badge advisory dell'app, pullback in trend favorevole |
| [[ta-stop-placement-position-sizing]] | Dove mettere lo stop: struttura vs percentuale; sizing 2% Rule; caso COPART |
| [[ta-vs-vi-decision-layer]] | TA advisory vs verdetto VI: due layer ortogonali, confine dominio, rischi whipsaw |

### Runbooks (8)

| Pagina | Descrizione |
|--------|-------------|
| [[fmp-api-quickstart]] | Guida operativa FMP stable: auth, search, profile, statements, key-metrics, screener; sezioni rate limit, errori HTTP, ADR-016 ref |
| [[sec-10k-10q-analysis-playbook]] | Playbook 7-step per analisi 10-K/10-Q con metodo Buffett e FMP API |
| [[value-investing-rule-engine-runbook]] | Implementazione step-by-step del Rule Engine: acquisizione FMP, validazione regole, DCF, MoS |
| [[runbook-openapi-contract-check]] | Troubleshooting contract-check: Boot 3.5, PatternParseException, MockMvc vs OpenAPIService |
| [[defensive-investor-checklist]] | 7 criteri Graham Cap.14 step-by-step con mapping FMP stable e WebApp signals |
| [[enterprising-investor-checklist]] | Criteri Graham Cap.15 step-by-step: liquidita', P/E≤9, net-net NCAV con FMP stable |
| [[pii-redaction-checklist]] | PII redaction step-by-step: pattern centralizzati, PiiRedactionEncoder, leak detection CI, GDPR retention |
| [[code-quality-review-runbook]] | CQRL operativo: roadmap 9 fasi, loop control (max_iterations=3, no-progress, regression detection), strategie batching (all-in-one / severity-tiered / split-by-area) |

### Incidents (2)

| Pagina | Descrizione |
|--------|-------------|
| [[2026-05-27-local-fe-test-run]] | Run Vitest/Playwright locale: 434/434 unit pass; E2E Playwright 30/30 pass post TSK-239 (keyboard seed + deep-analysis assert fix); cutover-smoke skip (no staging creds). |
| [[2026-05-29-f5-logout-csrf]] | Logout su F5: protezione CSRF su `/api/auth/refresh` cablata a metà (cookie `XSRF-TOKEN` mai emesso + header `X-CSRF-Token` mai inviato → 403). Fix BE `CsrfCookieFilter` + FE axios XSRF config; verificato end-to-end su stack Podman. |

