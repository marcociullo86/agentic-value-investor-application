---
id: gaps
type: gaps
title: Wiki Gaps (feedback loop)
status: draft
created: 2026-05-20
sources: []
tags: [feedback-loop]
---
# Wiki Gaps — App Template Demo

Canale formale del feedback loop della wiki (vedi `PATTERN.md §10`).

- **Apertura**: append-only condiviso fra `product-manager`, `lead-architect`,
  `tpm`, `wiki-query`, dev-agent (v2.7).
- **Chiusura**: esclusiva del `wiki-keeper` (aggiunge riga `**Risolto:**`).

Vedi `.claude/skills/wiki-gap-protocol.md` per il formato canonico e il ciclo
di vita.

---

## Gap aperti

### 2026-05-20 — tpm-profile-snapshot-ttl

**Origine:** tpm @ generazione TSK (L4 fase 2)
**Gap:** ADR-004 specifica TTL 24h per `fmp_financial_snapshot` ma il commento su
`fmp_profile_snapshot` (prezzo corrente) indica "proposta: 1h" senza formalizzazione.
Il TSK-010 applica 1h come default conservativo, ma questo valore non è validato
dall'Arch tramite ADR.
**Sospetta fonte:** Arch (lead-architect) — potrebbe essere risolto con un'appendice
ad ADR-004 o un nuovo ADR.
**Impatto:** Nessun blocco allo sviluppo (1h è valore prudente). Il dev-agent applica
1h; se il valore reale dovesse divergere richiede un hotfix su `FmpCacheService`.
**Bloccante:** no. `pending_clarification` annotato in TSK-010.

---

### 2026-05-20 — tpm-watchlist-default-creation

**Origine:** tpm @ generazione TSK (L4 fase 2)
**Gap:** ADR e US-017 non specificano se la watchlist default debba essere creata
automaticamente al primo accesso utente (lazy on-first-GET) o al momento della
registrazione. TSK-029 applica lazy creation come default ragionevole.
**Sospetta fonte:** PM (product-manager) — decisione di prodotto minore.
**Impatto:** Comportamento di UX: se creata lazy, la pagina `/watchlist` di un nuovo
utente mostrerà lista vuota senza errori. Nessun impatto su funzionalità core.
**Bloccante:** no.

---

## 2026-05-20 10:00 — fmp-rate-limiting

**Origine:** wiki-keeper @ ingest FMP_Docs 1-8
**Gap:** Nessuna documentazione su rate limiting (richieste/minuto, quota giornaliera, HTTP 429) nei raw disponibili.
**Sospetta fonte:** documentazione ufficiale FMP (da aggiungere come raw)
**Impatto:** Il runbook fmp-api-quickstart non puo' documentare i limiti di frequenza; integrazioni produzione potrebbero andare in throttling senza avviso.

**Risolto parzialmente:** 2026-05-22 — La nuova documentazione stable (`raw/fmp_docs.md` + `raw/fmp_docs.json`, 263 endpoint) non documenta i rate limit in modo esplicito. Il gap residuo e' rinominato `fmp-stable-rate-limiting` (aperto sotto). La gestione 429 e' gia' implementata tramite Resilience4j RateLimiter + `FmpEventLogger.log429RateLimited` (TSK-011).
**TSK-068 (2026-05-22):** Re-verificati `raw/FMP_Docs_1`–`8` (grep: nessuna quota, 429, req/min). Runbook [[fmp-api-quickstart]] § Rate limiting documenta gap + riferimento ADR-016 solo come policy L4. **Stato: aperto.** Piano ingest: aggiungere raw da documentazione FMP ufficiale (pricing, limiti API, FAQ rate limit).

---

## 2026-05-20 10:00 — fmp-endpoint-base-urls

**Origine:** wiki-keeper @ ingest FMP_Docs 1-8
**Gap:** I raw non documentano gli URL base ufficiali degli endpoint (es. versione API v3/v4, path esatti). Il runbook usa URL esemplificativi basati su pattern comuni.
**Sospetta fonte:** documentazione ufficiale FMP (da aggiungere come raw)
**Impatto:** Il runbook fmp-api-quickstart contiene URL non verificati; i developer potrebbero usare path errati.

**Risolto:** 2026-05-22 — `raw/fmp_docs.md` + `raw/fmp_docs.json` documentano tutti i 263 endpoint stable con `endpoint_url` verificati (base URL `https://financialmodelingprep.com/stable/`). Il runbook [[fmp-api-quickstart]] e l'entity [[fmp-api]] citano gli URL esatti da questi raw.
**TSK-068 (2026-05-22):** Raw FMP_Docs senza host/path HTTP; runbook usa placeholder `{base}` + tabella nomi API citabili da raw; URL completi solo via ADR-016 (L4, non provider). **Stato: aperto.** Piano ingest: raw con URL base ufficiali FMP (es. pagine endpoint della doc online).

---

## 2026-05-20 10:00 — fmp-error-codes

**Origine:** wiki-keeper @ ingest FMP_Docs 1-8
**Gap:** Nessun codice di errore HTTP o formato di risposta di errore documentato nei raw.
**Sospetta fonte:** documentazione ufficiale FMP (da aggiungere come raw)
**Impatto:** Gestione degli errori nelle integrazioni non puo' essere documentata nel wiki.

**Risolto parzialmente:** 2026-05-22 — La nuova doc stable non documenta esplicitamente i codici di errore. Comportamento osservato e documentato nel runbook [[fmp-api-quickstart]] (Step 5): 200 con `[]` per ticker non trovato, 429 per rate limit, 5xx per errori server, 401 per API key invalida. Nessun formato JSON di errore specificato nella doc ufficiale. Gap residuo su formato strutturato degli errori: to-be-rechecked-against-new-docs (nessuna nuova informazione nei raw stable).
**TSK-068 (2026-05-22):** Nessun codice HTTP né formato errore nei raw FMP_Docs 1–8; runbook § Errori HTTP elenca gap; mapping adapter solo in ADR-016 (L4). **Stato: aperto.** Piano ingest: raw sezione errori / troubleshooting FMP.

---

## 2026-05-20 12:00 — vi-sec-narrative-gap

**Origine:** wiki-keeper @ ingest value-investing 01-05
**Gap:** FMP API non espone il testo narrativo dei report SEC (Item 1 Business, Item 1A Risk Factors, Item 7 MD&A, Note al Bilancio). La [[sec-filings-analysis]] richiede accesso diretto a EDGAR per gli Step 1, 2, 3 e 5. La synthesis [[value-investing-fmp-integration]] segnala questo limite ma non offre alternativa tecnica documentata.
**Sospetta fonte:** integrazione con SEC EDGAR API (https://efts.sec.gov/LATEST/search-index) o provider terzi (Polygon.io SEC filings, Intrinio) da aggiungere come raw
**Impatto:** Il playbook [[sec-10k-10q-analysis-playbook]] non puo' essere completamente automatizzato con soli endpoint FMP; gli Step 1-3 e Step 5 richiedono intervento manuale su EDGAR.

**Risolto parzialmente:** 2026-05-23 — `wiki/concepts/munger-inversion-rag.md` + `wiki/concepts/value-investor-bot-architecture.md` documentano la metodologia di porting: download HTML diretto da SEC EDGAR via `finalLink` (endpoint FMP `/stable/sec-filings-search/symbol`), chunking FAISS RAG, analisi Munger inversion. La metodologia esiste e funziona in agent.py v2.6.1. Rimane gap implementativo lato Kotlin (EP-011 lo colmera' con US-041 + TSK relativi). Chiusura completa riservata al completamento di EP-011.

---

## 2026-05-20 14:00 — vi-webapp-owner-earnings-formula

**Origine:** wiki-keeper @ ingest 06_Documento_Funzionale_WebApp_Value_Investing.md
**Gap:** La FSD (RF4) fa riferimento al calcolo DCF basato su "Free Cash Flow o Owner Earnings" ma non dettaglia la formula esatta degli Owner Earnings (Utile Netto + Ammortamenti - CapEx di mantenimento - variazioni capitale circolante). I raw 01-05 descrivono il concetto ma non forniscono la formula implementativa puntuale usata nel Rule Engine.
**Sospetta fonte:** documentazione interna del progetto (decision doc tecnico) o raw aggiuntivo con specifiche di implementazione del motore DCF
**Impatto:** Il runbook [[value-investing-rule-engine-runbook]] (Step 3b) e il concept [[value-investing-rule-engine]] non possono documentare la formula precisa degli Owner Earnings; l'implementazione Kotlin potrebbe divergere dal concetto teorico.

**Risolto:** 2026-05-20 — [[vi-08-risoluzione-q001-owner-earnings]] (Q_001 chiusa; formula Greenwald Metodo 1 come primario, Metodo 3 come fallback per settori capital-intensive)

---

## 2026-05-20 14:00 — vi-webapp-spa-framework-decision

**Origine:** wiki-keeper @ ingest 06_Documento_Funzionale_WebApp_Value_Investing.md
**Gap:** La FSD indica il frontend come SPA con candidati React, Vue.js o Angular, ma non registra una decisione definitiva sul framework. Il wiki non puo' documentare lo stack frontend effettivo della WebApp Value Investing.
**Sospetta fonte:** ADR (Architecture Decision Record) o documento di design tecnico da aggiungere come raw; oppure decisione da prendere e registrare come ADR nel progetto.
**Impatto:** Il concept [[webapp-architecture-vi]] e la synthesis [[webapp-value-investing-spec]] riportano il framework come "non ancora selezionato"; il be-dev/fe-dev non possono iniziare implementazione frontend senza questa decisione.

**Risolto:** 2026-05-20 — [[vi-07-risoluzione-q002-q003]] (Q_002 chiusa; ADR: React + Next.js SPA/SSG; state management Zustand/Redux Toolkit da finalizzare; US-014/015/016 sbloccate)

---

## 2026-05-20 16:00 — vi-webapp-screener-criteria

**Origine:** product-manager @ scrittura EP-001 / US-002
**Gap:** RF1 della FSD descrive uno "Screener di mercato con filtri su capitalizzazione e settore" senza dettagliare le fasce di market cap (es. small/mid/large) né la lista chiusa dei settori industriali. Il wiki non documenta i criteri operativi del screener parametrico.
**Sospetta fonte:** raffinamento prodotto (PM-side) o riferimento a tassonomia settoriale standard (GICS) da aggiungere come raw; oppure decisione da formalizzare in spec dedicata.
**Impatto:** US-002 (Screener parametrico) non può essere implementata con AC oggettivi senza i criteri di soglia. Aperta Q_003 (soft) in `management/questions.md`; US-002 resta `ready` con `pending_clarification: [Q_003]`.

**Risolto:** 2026-05-20 — [[vi-07-risoluzione-q002-q003]] (Q_003 chiusa; 5 fasce market cap $50M–>$200B, 11 settori GICS, filtro "Exclude Hard-to-Predict Sectors" aggiunto come requisito; EP-001 pienamente sbloccata)

---

## 2026-05-20 18:00 — arch-auth-provider-choice

**Origine:** lead-architect @ scrittura ADR-006 (Authentication)
**Gap:** La FSD e i raw non documentano se l'autenticazione utente debba usare un provider esterno (OIDC: Auth0, Keycloak, Okta) o un sistema locale. ADR-006 ha scelto JWT locale + Spring Security come default MVP-appropriato, ma una decisione esplicita di prodotto su SSO enterprise (R2+) richiederebbe un raw dedicato.
**Sospetta fonte:** decisione di prodotto (PM-side) o raw aggiuntivo con requisiti di compliance (es. SOC2, ISO27001) se l'app sara' esposta a clienti enterprise.
**Impatto:** Non blocca il MVP (R1.0/R1.1). Diventa rilevante se in R2 si introducono integrazioni B2B che richiedono SSO. ADR-006 cita questo gap come decisione di "evoluzione possibile". Bloccante: no.

---

## 2026-05-20 18:00 — arch-deployment-target

**Origine:** lead-architect @ scrittura ADR-009 (Deployment)
**Gap:** La FSD non specifica il target di runtime/deploy effettivo: cloud provider (AWS/GCP/Azure), modalita' (managed container service, k8s, VM), sizing, backup policy, retention log. ADR-009 fissa il baseline Docker monorepo runtime-agnostico, ma per il cutover R1.0 servira' una decisione concreta.
**Sospetta fonte:** decisione operativa (DevOps/PM-side) da formalizzare prima del cutover R1.0; eventuale raw "operations-runbook" dedicato.
**Impatto:** Non blocca lo sviluppo (Docker image self-contained e' deploy-target-agnostica). Blocca il cutover di produzione: serve definire backup PostgreSQL, retention `fmp_api_event_log`, scaling. Bloccante: no (per sviluppo R1.0); sì pre-cutover.

---

## 2026-05-20 19:00 — arch-adr-version-sync

**Origine:** tech-scout @ promote raw/tech_stack.md
**Gap:** `raw/tech_stack.md` adottato il 2026-05-20 contiene versioni 2026 (Kotlin 2.2, React 19 + Next.js 16.x, PostgreSQL 17) mentre gli ADR-001/002/003 documentano versioni inferiori (React 18, Kotlin 1.9, PostgreSQL 16). PATTERN §7 r.10 dà priorità a `raw/tech_stack.md` per i dev-agent, quindi non cè rischio operativo, ma la divergenza archivistica va sanata.
**Sospetta fonte:** lead-architect — rilascio di ADR-001-v2, ADR-002-v2, ADR-003-v2 (o update non-distruttivo §7 r.7 sui correnti).
**Impatto:** Solo documentale. I dev-agent useranno le versioni di `raw/tech_stack.md`. Bloccante: no.

### 2026-05-22 — be-problemdetail-flatten

**Origine:** claude @ ci-stabilize Sprint 3 PR #1
**Gap:** Spring 6.x (incluso Spring Boot 3.5.0 con Spring Framework 6.2.7) serializza `org.springframework.http.ProblemDetail` con gli extension members annidati sotto la chiave `properties` invece che come fratelli di `type`/`title`/`status`/`detail`/`instance`. Esempio body attuale:
```
{"type":"...","title":"...","status":404,"properties":{"ticker":"AAPL","timestamp":"..."}}
```
ADR-007 §Error format dichiara RFC 9457 §3.2 (extensions al top-level). Quattro tentativi di flatten (commits b385926 Jackson mixin con @JsonAnyGetter; 873b9e6 StdSerializer + @JsonComponent; e8a0880 modulesToInstall vs modules; 20f846b serializerByType su Jackson2ObjectMapperBuilder) sono tutti landati correttamente ma zero effetto sul body in CI — Spring usa un path serializzazione specifico per `application/problem+json` che bypassa l'ObjectMapper customizer.
**Sospetta fonte:** custom `HttpMessageConverter` per `application/problem+json` registrato in `WebMvcConfigurer` che bypassa la pipeline Jackson default; oppure aggiornamento a Spring Boot >=3.5.x con il fix per #25801 quando disponibile.
**Impatto:** I client che si conformano strettamente a RFC 9457 §3.2 (extensions come top-level fields) leggeranno `ticker` solo sotto `properties.ticker`. Per ora tutti i caller noti (FE proprio + test) sanno entrambe le forme. Test BE (AnalysisControllerIT + SearchControllerIT) assertano `$.properties.ticker`. Non bloccante per il MVP. Bloccante: no.

### 2026-05-22 — fe-swr-peer-r19

**Origine:** claude @ ci-stabilize Sprint 3 PR #1
**Gap:** `swr@2.2.5` dichiara peer range `react@"^16.11.0 || ^17.0.0 || ^18.0.0"`, ma il progetto pinna `react@19.0.0` (raw/tech_stack.md baseline). `npm install` fallisce con ERESOLVE senza `--legacy-peer-deps`. Pattern attualmente applicato in 4 punti: `.github/workflows/ci.yml` (fe-test + fe-e2e + fe-e2e-realbe), `src/docker/Dockerfile` (fe-build stage), `.github/workflows/contract-check.yml` (FE OpenAPI types).
**Sospetta fonte:** monitoraggio rilasci `swr` su npm/GitHub; bumpare quando una release widens il peer range includendo react 19, rimuovendo i 5 `--legacy-peer-deps`.
**Impatto:** I `--legacy-peer-deps` rilassano la risoluzione delle dep, lasciando teoricamente possibili incompatibilita runtime nascoste. In pratica swr 2.x funziona con react 19 (nessun regressione osservata in vitest/Playwright/runtime FE). Non bloccante. Bloccante: no.

### 2026-05-22 — fe-static-export-tickers

**Origine:** claude @ ci-stabilize Sprint 3 PR #1
**Gap:** `src/frontend/next.config.js` impone `output: 'export'` (statico), che richiede `generateStaticParams()` su tutte le route dinamiche. `app/analysis/[ticker]/page.tsx` adesso espone un set hardcoded di 8 ticker (AAPL, MSFT, GOOGL, AMZN, META, NVDA, TSLA, BRK.B) — copre l'E2E (AAPL) e i piu comuni demo ticker ma non e una soluzione di produzione: visitare `/analysis/{ticker}` per qualunque altro simbolo restituisce 404. Il moat-checklist (`app/moat/page.tsx`) ha gia evitato il problema usando query param (`/moat?ticker=AAPL`).
**Sospetta fonte:** decisione architetturale (lead-architect) — alternative: (a) feed di build-time dal database stocks (richiede prerender step), (b) refactor /analysis/[ticker] -> /analysis?ticker=... (uniforma con /moat), (c) dropping `output: 'export'` per un runtime SSR (cambia deployment ADR-009).
**Impatto:** Limita il deployment statico a una whitelist di ticker. Track A puo perfezionare il modello in Sprint successivo. Bloccante per MVP: no (la lista copre i ticker piu rilevanti).

---

## 2026-05-22 10:00 — fmp-stable-rate-limiting

**Origine:** wiki-keeper @ ingest fmp_docs.md + fmp_docs.json (migrazione stable)
**Gap:** La documentazione FMP stable (`raw/fmp_docs.md`, `raw/fmp_docs.json`, 263 endpoint) non specifica i limiti di frequenza: richieste/minuto per piano, quota giornaliera, comportamento esatto dell'HTTP 429. Il gap `fmp-rate-limiting` del 2026-05-20 (basato su v3) e' stato trasferito su stable: le nuove fonti non lo risolvono.
**Sospetta fonte:** sezione "Pricing" o "Cycle Times" del sito FMP (non inclusa nei raw estratti) — da aggiungere come raw dedicato se necessario per dimensionare il RateLimiterRegistry.
**Impatto:** Il `FmpResilienceConfig` usa 30 richieste/min come configurazione conservativa (TSK-011). Se il piano FMP effettivo ha limiti diversi, il RateLimiterRegistry deve essere aggiornato. La gestione HTTP 429 e' gia' implementata (`log429RateLimited`, Circuit Breaker). Bloccante: no per MVP.

---

## 2026-05-22 10:00 — fmp-stable-analyst-estimates

**Origine:** wiki-keeper @ ingest fmp_docs.md + fmp_docs.json (migrazione stable)
**Gap:** La vecchia sezione FMP v3 "Estimates" includeva stime degli analisti (consensus EPS, revenue estimates, price target EPS forward). Nella nuova documentazione stable la sezione "News & Media" copre solo news/press release. Non e' chiaro se le stime analisti siano presenti in un'altra sezione stable non estratta nei raw o se siano state rimosse dall'API stable.
**Sospetta fonte:** documentazione FMP stable sezione "Analyst Estimates" / "Earnings Calendar" (potenzialmente presenti nel sito ma non nei raw estratti).
**Impatto:** Il rule engine MVP non usa le stime analisti (focus su dati storici oggettivi). Se future feature richiedono consensus estimates o price target, questo gap dovra' essere risolto prima dell'implementazione. Bloccante: no per MVP.

---

## 2026-05-22 10:00 — fmp-stable-adapter-migration

**Origine:** wiki-keeper @ ingest fmp_docs.md + fmp_docs.json (migrazione stable)
**Gap:** La documentazione wiki descrive la migrazione necessaria da v3 a stable per `FmpAdapterRestClient` (path URL, parametri, DTO). Nessun TSK e' stato ancora creato per eseguire questa migrazione nel codice. L'adapter attuale (TSK-009) usa ancora path v3.
**Sospetta fonte:** be-dev — richiede un TSK dedicato "Migrate FmpAdapterRestClient to /stable endpoints".
**Impatto:** Finche' l'adapter non e' migrato, il backend chiama endpoint v3 dismessi (EOL 2025-08-31) — se FMP mantiene temporaneamente v3 attiva, funziona; se v3 restituisce errori, il sistema non funziona in produzione. La wiki documenta i path corretti in [[fmp-api-quickstart]] e [[fmp-api-overview]]. Bloccante: si, per deployment post-2025-08-31. TSK da aprire urgente.

**Aggiornamento 2026-05-22 — tpm @ generazione TSK-050:** TSK-050 creato sotto EP-002/US-021-manutenzione-fmp-stable (Sprint 5). Il gap e' ora tracciato come `todo` in kanban. Chiusura formale riservata a wiki-keeper dopo completamento TSK-050.

---

## 2026-05-22 20:00 — graham-bond-formulas-modern-regime

**Origine:** wiki-keeper @ ingest raw/investitore intelligente.txt
**Gap:** Il Capitolo 2 de L'Investitore Intelligente descrive la protezione dall'inflazione con obbligazioni a tasso fisso (contesto 1973: regime inflattivo USA). Il wiki non documenta come Graham applica le stesse formule di valutazione obbligazionaria (cedola vs rendimento, duration) al regime dei tassi 2023-2026 (tassi reali positivi dopo 15 anni di ZIRP). Il concetto [[inflation-investing-graham]] tratta solo la parte azionaria.
**Sospetta fonte:** aggiornamento del Capitolo 2 (commenti Zweig 2003 gia' citano i TIPS ma il testo italiano potrebbe non essere esaustivo) o raw aggiuntivo su asset allocation obbligazionaria moderna.
**Impatto:** Il runbook [[defensive-investor-checklist]] non documenta la componente obbligazionaria del portafoglio difensivo. Per il MVP attuale (focus su screening azionario) non e' bloccante. Bloccante: no.

---

## 2026-05-22 20:00 — net-net-implementation-gap

**Origine:** wiki-keeper @ ingest raw/investitore intelligente.txt
**Gap:** Il criterio net-net (prezzo < 2/3 NCAV) e' documentato in [[net-net-stocks]] e [[enterprising-investor-checklist]] ma non e' implementato come `ruleId` nel [[value-investing-rule-engine]]. I dati FMP necessari (totalCurrentAssets, totalLiabilities, sharesOutstanding) sono disponibili via [[fmp-financial-statements-stable]] ma nessuna regola li aggrega. Il Rule Engine MVP si concentra sui criteri Buffett (ROE, ROIC, Margin, etc.) che sono piu' applicabili ai mercati 2026 dove le net-net sono rare.
**Sospetta fonte:** decisione di product (PM) su priorita' MVP. Potrebbe essere aggiunto come US in EP-003 o EP-005 in Sprint futuri.
**Impatto:** L'investitore intraprendente Graham che vuole usare la WebApp per trovare net-net stocks deve usare la checklist manuale ([[enterprising-investor-checklist]] Step 7) senza segnale automatico. Bloccante: no per MVP.

---

## 2026-05-23 — wiki-promote-sec-edgar-adapter-spec

**Origine:** product-manager @ scrittura EP-011 / US-038
**Gap:** Non esiste una pagina wiki concept dedicata all'adapter SEC EDGAR (interface `SecEdgarAdapter`, rate-limit fair-access ≤ 10 req/s, header User-Agent identificativo + email, cache CIK→ticker TTL 30gg). Il flusso funzionale `wiki/concepts/sec-filings-analysis.md` documenta l'accesso ai filing ma non la spec dell'adapter applicativo. La spec di riferimento attuale è il prototipo Python `agent.py:355-371` (non in wiki/).
**Sospetta fonte:** lead-architect (ADR adapter SEC) + wiki-keeper (promozione a concept dedicato `sec-edgar-adapter`).
**Impatto:** US-038 cita `wiki/concepts/sec-filings-analysis.md` come riferimento approssimato; la pending_clarification è marcata in US-038 ma non blocca la storia (il be-dev può procedere con la spec dal codice Python). Bloccante: no.

**Risolto:** 2026-05-23 — `wiki/concepts/clone-investing-13f-overlay.md` documenta l'intera spec dell'adapter SEC EDGAR: endpoint URL, User-Agent policy (`ValueInvestorBot research@valueinvestorbot.com`), rate limit (6-7 req/sec, `SEC_RATE_LIMIT_S=0.15`), caching TTL 30gg, emergency holdings fallback, algoritmo normalizzazione 4-step `nameOfIssuer → ticker` (validato 97.4%). `wiki/concepts/munger-inversion-rag.md` documenta il flusso di download filing via `finalLink`. La spec e' ora completa per US-038 e il be-dev puo' procedere senza riferirsi al codice Python.

---

## 2026-05-23 — wiki-promote-pgvector-concept

**Origine:** product-manager @ scrittura EP-011 / US-040
**Gap:** Non esiste una pagina wiki concept dedicata al vector store pgvector (estensione PostgreSQL 17, schema `filing_chunks` con embedding 1024-dim, indice HNSW, parametri chunking). La pagina `wiki/concepts/analysis-api-pipeline.md` documenta la pipeline end-to-end ma non lo strato di persistenza vettoriale. Decisione di prodotto: vector store = pgvector (confermata dall'utente il 2026-05-23).
**Sospetta fonte:** wiki-keeper (promozione a concept dedicato `pgvector-vector-store`) + eventuale ADR di lead-architect su scelta pgvector vs FAISS/external.
**Impatto:** US-040 cita `wiki/concepts/analysis-api-pipeline.md` come riferimento più vicino. La pending_clarification è marcata in US-040 ma non blocca la storia. Bloccante: no.

---

## 2026-05-23 — wiki-promote-arctic-embed-spec

**Origine:** product-manager @ scrittura EP-011 / US-040
**Gap:** Non esiste una pagina wiki concept dedicata al modello di embedding scelto (`Snowflake/snowflake-arctic-embed-l-v2.0`, 1024 dim, 8192 token context, MTEB ~67, caricamento locale via HuggingFace, configurabile in `application.yaml` come `embeddings.model.name` per A/B test futuro con Qwen3-Embedding-4B). Decisione di prodotto confermata dall'utente il 2026-05-23.
**Sospetta fonte:** wiki-keeper (concept dedicato `arctic-embed-l-v2`) + eventuale ADR di lead-architect su scelta embedding model.
**Impatto:** US-040 nomina il modello nelle Business Rules con tutti i parametri; la pending_clarification è marcata ma non blocca la storia. Bloccante: no.

---

## 2026-05-23 — wiki-promote-fmp-dividend-history

**Origine:** product-manager @ scrittura EP-010 / US-037
**Gap:** L'endpoint FMP `/stable/historical-price-full/stock_dividend` (storico dividendi 20+ anni) non è documentato nella wiki — manca da `wiki/runbooks/fmp-api-quickstart.md` e da `wiki/concepts/fmp-financial-statements-stable.md`. US-037 (Criterio Graham 4 — Continuità Dividendi 20 anni) richiede l'estensione di `FmpAdapter` con un nuovo metodo `getDividendHistory(ticker, years=20)`.
**Sospetta fonte:** wiki-keeper (ingest endpoint dividend history dalla doc FMP stable già nei raw) o aggiornamento puntuale di `wiki/runbooks/fmp-api-quickstart.md`.
**Impatto:** US-037 marca pending_clarification ma non blocca: il be-dev può recuperare lo spec dell'endpoint dai raw FMP stable. Bloccante: no.

**Risolto:** 2026-05-24 — TSK-083 ha verificato il vero endpoint /stable contro `raw/fmp_docs.md:8997-9020` (Dividends Company API): l'endpoint reale è **`GET /stable/dividends?symbol={ticker}`** (NON `/stable/historical-price-full/stock_dividend`, che era pattern `/api/v3` legacy deprecato 2025-08-31). Implementato `FmpAdapter.getDividendHistory(ticker): List<DividendRecord>` con DTO 9 field nullable (date/recordDate/paymentDate/declarationDate/dividend/adjDividend/yield/frequency). Cache via `FmpCacheService.getOrFetch(endpoint="dividends", TTL=24h)` con whitelist `dividends` aggiunta al CHECK constraint `fmp_fin_snap_endpoint_chk` (migration V011). Estesa anche tabella dedicata `fmp_dividend_history_snapshot` (V010) disponibile per analytics future. Concept page dedicata `wiki/concepts/fmp-dividend-history.md` può essere aggiunta in futuro via wiki-keeper se richiesta separata.

---

## 2026-05-23 — wiki-extend-analysis-api-pipeline-deep

**Origine:** product-manager @ scrittura EP-011 / US-045
**Gap:** La pagina `wiki/concepts/analysis-api-pipeline.md` documenta solo l'endpoint `/api/analysis/{ticker}` standard. L'endpoint `/api/analysis/{ticker}/deep` di EP-011 introduce un payload molto più ampio (verdict_payload, deep_analysis_report, sentiment_summary, price_action_snapshot, filings_used) che non è ancora descritto nel concept.
**Sospetta fonte:** wiki-keeper — estensione della pagina esistente (o nuova pagina `analysis-api-pipeline-deep`) al completamento di US-045.
**Impatto:** US-045 ha tutti i Business Rules necessari per implementare l'endpoint, ma la documentazione architetturale wiki resterà non allineata fino all'aggiornamento. Bloccante: no.

---

## 2026-05-23 — wiki-promote-universe-screener-spec

**Origine:** product-manager @ scrittura EP-012 / US-047
**Gap:** Il servizio `UniverseScreenerService` (orchestratore FMP company-screener + 13-F overlay SEC EDGAR + news scout LLM su universo NASDAQ+NYSE) non ha una pagina wiki concept dedicata. La logica di riferimento è codificata nel prototipo Python `agent.py:744-988`. Le pagine attuali `wiki/runbooks/defensive-investor-checklist.md` e `wiki/concepts/superinvestors-graham-doddsville.md` documentano la teoria ma non l'orchestrazione tecnica.
**Sospetta fonte:** wiki-keeper (promozione a concept dedicato `universe-screener-service`) + spec da `agent.py`.
**Impatto:** US-047 ha tutti i Business Rules nella storia; la pending_clarification è marcata ma non blocca lo sviluppo. Bloccante: no.

**Risolto:** 2026-05-23 — `wiki/concepts/value-investor-bot-architecture.md` documenta l'intera orchestrazione tecnica del `node_screener`: 4 segnali aggregati (13-F SEC EDGAR, quant FMP, news LLM scout, settori Buffett), endpoint FMP utilizzati, costanti configurabili (`UNIVERSO_FINALE_MAX_TICKET_NUMBER=30`), modalita' ibrida ticker manuali + screener, blacklist settoriale. `wiki/concepts/clone-investing-13f-overlay.md` approfondisce il Segnale 1 (13-F). La spec e' completa per US-047.

---

## 2026-05-23 — tpm-embeddings-sidecar-vs-djl

**Origine:** tpm @ scrittura TSK EP-010/011/012
**Gap:** Decisione architetturale non formalizzata in ADR: sidecar Python FastAPI per embeddings (sentence-transformers + Snowflake Arctic Embed L v2.0) versus djl-huggingface JVM-nativo. Il sidecar Python è più semplice e riusa direttamente `sentence-transformers` come `agent.py`, ma aggiunge un container extra al deployment; djl-huggingface semplificherebbe il deployment ma ha supporto limitato per modelli HuggingFace recenti e performance JVM potenzialmente inferiori per inferenza CPU.
**Sospetta fonte:** lead-architect (ADR dedicato sull'architettura di inferenza embedding).
**Impatto:** TSK-099 applica default = sidecar Python come da indicazione utente. Non bloccante per lo sviluppo (il sidecar è implementabile indipendentemente). Se il lead-architect decide per djl-huggingface, TSK-099 va rework ma TSK-100 (EmbeddingService HTTP client) rimane valido (solo cambia il target HTTP). Bloccante: no. `pending_clarification` annotato in TSK-099.

**ADR-018 proposto:** 2026-05-23 — `design_&_architecture/decisions/ADR-018-embeddings-inference-architecture.md` (status `proposed`) risolve il gap **per design** adottando Opzione A (sidecar Python FastAPI con `sentence-transformers` + `Snowflake/snowflake-arctic-embed-l-v2.0`): container Docker `embeddings-sidecar` orchestrato via Docker Compose, endpoint `POST /embed` (1024-dim, normalize L2), backend Kotlin consumer `EmbeddingService` interface + `EmbeddingRestClient` impl con Resilience4j chain `RateLimiter → CircuitBreaker → Retry → HTTP`, observability via `embeddings_api_event_log` + Micrometer, configurabilità A/B test via `embeddings.model.name`. Pattern coerente con ADR-004 (FMP adapter) e ADR-017 (Anthropic adapter). Conferma del default TSK-099. Chiusura formale del gap riservata a wiki-keeper dopo accettazione ADR-018 da parte dell'utente.

---

## 2026-05-23 — tpm-anthropic-sdk-jvm-version

**Origine:** tpm @ scrittura TSK EP-010/011/012
**Gap:** Verificare la disponibilità ufficiale di `com.anthropic:anthropic-java` SDK su Maven Central al 2026-05-23. Il knowledge cutoff dell'agent è agosto 2025: al momento di scrittura il SDK ufficiale Anthropic Java era in beta (GitHub `anthropics/anthropic-sdk-java`). Se non pubblicato su Maven Central, il fallback è un HTTP client Kotlin diretto verso `https://api.anthropic.com/v1/messages` con header `x-api-key` + `anthropic-version: 2023-06-01` (spec OpenAPI Anthropic pubblica).
**Sospetta fonte:** lead-architect — verifica disponibilità SDK + decisione fallback.
**Impatto:** TSK-104 implementa il bean `AnthropicClient` con entrambe le opzioni (SDK ufficiale se disponibile, HTTP diretto come fallback). Non bloccante: l'interfaccia `AnthropicClient` è identica indipendentemente dall'implementazione sottostante. Bloccante: no. `pending_clarification` annotato in TSK-104.

**ADR-017 proposto:** 2026-05-23 — `design_&_architecture/decisions/ADR-017-anthropic-sdk-jvm.md` (status `proposed`) risolve il gap **per design** adottando Opzione C (adapter behind interface `AnthropicClient`): implementazione primaria `AnthropicRestClient` (HTTP diretto via Spring `RestClient` + header `anthropic-version: 2023-06-01`) sempre attiva; implementazione opzionale `AnthropicSdkClient` attivabile via property `anthropic.client.impl=sdk` + `@ConditionalOnClass` quando il SDK ufficiale sarà su Maven Central. Zero impatto sui caller (US-041/042/047) al momento dello switch. Chiusura formale del gap riservata a wiki-keeper dopo (a) accettazione ADR-017 da parte dell'utente e (b) verifica effettiva disponibilità Maven Central del SDK ufficiale.

---

## 2026-05-23 — agent-py-roe-lookback-policy

**Origine:** wiki-keeper @ ingest agent.py + method analysis 2026-05-23
**Gap:** agent.py v2.6.1 usa ROE medio 5 anni (`roe_medio_5y`); il Rule Engine Kotlin usa ROE 10 anni (`ROE_10Y_AVG`). Non esiste un ADR che formalizzi quale lookback sia corretto per la WebApp e in quale contesto ciascuno debba essere usato. Il raw analitico identifica il trade-off: 5y favorisce turnaround e growth-value, 10y favorisce stabilita' (allineato Graham).
**Sospetta fonte:** lead-architect (ADR-005-rule-engine-design appendice, o nuovo ADR) + product-manager (decisione se esporre entrambi in Deep Analysis EP-011).
**Impatto:** Il report Deep Analysis (EP-011) potrebbe voler esporre entrambi i lookback (5y e 10y) come segnali distinti, ma non esiste ancora la specifica. Non blocca l'MVP (ROE_10Y_AVG gia' implementato). Bloccante: no.

---

## 2026-05-23 — agent-py-current-ratio-routing-gap

**Origine:** wiki-keeper @ ingest agent.py + method analysis 2026-05-23
**Gap:** In agent.py v2.6.1, il `current_ratio` viene calcolato in `node_estrai_dati` e salvato nelle metriche, ma non viene usato come gate nel routing `munger_decision`. Di conseguenza, un'azienda con Current Ratio < 1.5 (Criterio 2 Graham non soddisfatto) puo' comunque ricevere verdetto `APPROVATO` se supera i check ROE+D/E+MoS. Il Rule Engine Kotlin lo gestisce correttamente (segnale RED). Questo e' un bug metodologico latente in agent.py che non deve essere replicato nel porting Kotlin EP-011.
**Sospetta fonte:** decisione di design agent.py (forse intenzionale — il Criterio 2 Graham non era una priorita' del prototipo).
**Impatto:** Solo sul prototipo Python (non sul Rule Engine Kotlin che ha gia' il fix). Documentato come avviso per il porting Deep Analysis EP-011. Bloccante: no.

---

## 2026-05-23 — tpm-llm-cost-budget-r2

**Origine:** tpm @ scrittura TSK EP-010/011/012
**Gap:** Costo stimato Anthropic Claude Opus 4.7 per ticker: ~$0.10-0.15 (10 query Munger + 1 sintesi su ~150k token totali input/output per ticker 10-K medio). Per il batch notturno di EP-012 (30 ticker top-picks × run giornaliera): ~$3-4.50/giorno = ~$90-135/mese. Aggiungendo la classificazione news (US-042) e il news scout (US-048): potenziale +$20-40/mese. Budget mensile totale stimato R2: **$110-175/mese**. Conferma budget richiesta prima del go-live di EP-011/012.
**Sospetta fonte:** product-manager / utente (conferma budget LLM per R2).
**Impatto:** Se il budget non è approvato, le US-041 (Munger LLM), US-042 (news sentiment), US-047 (news scout) devono essere riconsiderate con modelli meno costosi (es. Claude Haiku o Claude Sonnet). I TSK corrispondenti (TSK-104, TSK-105, TSK-107, TSK-109, TSK-111, TSK-128) hanno `pending_clarification` implicita. Bloccante: no per sviluppo; sì per go-live EP-011/012 in produzione.

**ADR-019 proposto:** 2026-05-23 — `design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md` (status `proposed`) risolve il gap **per design** definendo il meccanismo di containment: (a) budget cap mensile configurabile (default proposto **$150/mese**, range realistico stimato a regime $50-150/mese con cache attiva), (b) telemetria obbligatoria via tabelle DB `llm_cost_counter` (aggregato mensile UPSERT atomico) + `llm_call_log` (1 row/chiamata, retention 90gg) + metriche Micrometer, (c) kill-switch automatico al 90% del cap con comportamento degraded (cache hit serve la risposta; cache miss → HTTP 503 `LLM_BUDGET_EXCEEDED` ProblemDetail), (d) reset mensile cron `0 0 0 1 * *` UTC, (e) override admin via env var `LLM_BUDGET_KILL_SWITCH_ENABLED=false`, (f) endpoint admin `GET /admin/llm-cost` per audit. Tre TSK proposti da aggiungere a EP-011 (TSK-XXX-A be `LlmCostCounterService`, TSK-XXX-B db migration `V0XX__llm_cost_tracking`, TSK-XXX-C be `LlmCallLogger` AOP + admin endpoint). La chiusura formale del gap resta riservata a wiki-keeper dopo (a) accettazione utente di ADR-019, (b) conferma esplicita del valore numerico di `llm.budget.monthly-cap-usd` ($150 proposto è un default lead-architect, non una decisione di prodotto definitiva).

---

## 2026-05-23 — wiki-fmp-key-metrics-stable-rename

**Origine:** tpm @ scrittura TSK EP-007 fase 2 hotfix 2026-05-23
**Gap:** La wiki `wiki/concepts/fmp-key-metrics-ratios.md` documenta i campi ROE e ROIC per l'endpoint `/stable/key-metrics` usando i nomi logici `roe` e `roic` (che corrispondono ai nomi Kotlin del DTO), ma il payload reale confermato in `raw/fmp_docs.json:1176` usa i nomi JSON `returnOnEquity` e `returnOnInvestedCapital`. La wiki non riflette questa differenza tra nome JSON del payload e nome Kotlin del campo DTO. Un lettore della wiki che legga "campo `roe`" potrebbe aspettarsi che la chiave JSON sia letteralmente `"roe"`, causando confusione in future manutenzioni del mapping. La verità tecnica sta nel raw (`raw/fmp_docs.json:1176`); il codice corretto è documentato in TSK-148 (fix `@JsonProperty`). La wiki va aggiornata con una nota `## Aggiornamenti (v2026-05-23)` che chiarisce: il payload `/stable/key-metrics` usa `"returnOnEquity"` e `"returnOnInvestedCapital"` come chiavi JSON, mappate ai campi Kotlin `roe` e `roic` via `@JsonProperty` (rinaming introdotto dalla migrazione v3 → stable, TSK-072/TSK-148).
**Sospetta fonte:** wiki-keeper — aggiornamento puntuale di `wiki/concepts/fmp-key-metrics-ratios.md` §Response shape key-metrics (campi critici).
**Impatto:** Non bloccante per il fix US-053 (la verità sta nel raw; TSK-148 corregge il codice indipendentemente dall'aggiornamento wiki). Documentale — evita confusione per futuri manutentori. Bloccante: no.
**TSK correlati:** TSK-148 (fix codice), TSK-149 (test deserializzazione con nomi reali).

**Risolto:** 2026-05-23 — aggiornato fmp-key-metrics-ratios.md §"Aggiornamenti v2026-05-23". Documentati: 6 field rinominati v3→stable con tabella @JsonProperty, ~18 field assenti da /stable/key-metrics (spostati in /stable/ratios) con nota su null silenzioso e fallback derivato, caso speciale bookValuePerShare con formula BVPS = totalStockholdersEquity / weightedAverageShsOutDil (commit bdb2d3e). Gap documentale chiuso.
