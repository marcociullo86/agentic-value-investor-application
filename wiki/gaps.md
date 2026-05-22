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
