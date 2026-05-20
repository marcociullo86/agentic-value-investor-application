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

---

## 2026-05-20 10:00 — fmp-endpoint-base-urls

**Origine:** wiki-keeper @ ingest FMP_Docs 1-8
**Gap:** I raw non documentano gli URL base ufficiali degli endpoint (es. versione API v3/v4, path esatti). Il runbook usa URL esemplificativi basati su pattern comuni.
**Sospetta fonte:** documentazione ufficiale FMP (da aggiungere come raw)
**Impatto:** Il runbook fmp-api-quickstart contiene URL non verificati; i developer potrebbero usare path errati.

---

## 2026-05-20 10:00 — fmp-error-codes

**Origine:** wiki-keeper @ ingest FMP_Docs 1-8
**Gap:** Nessun codice di errore HTTP o formato di risposta di errore documentato nei raw.
**Sospetta fonte:** documentazione ufficiale FMP (da aggiungere come raw)
**Impatto:** Gestione degli errori nelle integrazioni non puo' essere documentata nel wiki.

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
