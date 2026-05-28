---
id: ADR-026
title: Frontend AuthGuard in produzione con static export (no middleware runtime)
status: accepted
created: 2026-05-28
deciders: [lead-architect]
---
# ADR-026 — Frontend AuthGuard in produzione con static export (no middleware runtime)

## Contesto

L'architettura di deploy corrente mantiene `output: 'export'` per il frontend Next.js, servito come bundle statico dal backend Spring Boot, senza runtime Node/Edge in produzione. [^src: management/kanban/EP-007-hardening-produzione/US-023-analisi-ticker-arbitrario-deploy-statico/US-023.md §Descrizione]

US-073 richiede AuthGuard centralizzato con redirect `returnUrl`, gestione sessione scaduta e rotta `/403`, ma l'implementazione attuale in `middleware.ts` Next.js risulta operativa solo in `next dev` quando non c'e' export statico. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-073-auth-guard-centralizzato/US-073.md §Business Rules]

US-046 in origine modellava la deep analysis come path dinamico `/analysis/{ticker}/deep`; il runtime statico e' stato stabilizzato con rotta query-param (`/analysis/deep?ticker=`), quindi il problema residuo non e' piu' la deep route ma il guard middleware in produzione. [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-046-frontend-tab-deep-analysis/US-046.md §Business Rules]

## Decisione

### Opzione raccomandata: **B — mantenere static export + AuthGuard client-side di produzione**

Si mantiene `output: 'export'` come vincolo architetturale di produzione e si migra il comportamento di guard da `middleware.ts` a un layer client-side esplicito (provider/hook/layout guard) applicato alle route protette.

La semantica funzionale resta quella di US-073:

- non autenticato su rotta protetta -> redirect a `/login?returnUrl=...`
- autenticato senza ruolo -> redirect a `/403`
- sessione scaduta -> logout silente + redirect login con stato esplicito
- endpoint backend sempre autoritativi per auth/authz (defense-in-depth)

`middleware.ts` resta supportato **solo per sviluppo locale** (parita' di comportamento in `next dev`, CSP nonce per-request in ambiente dev), ma non rappresenta piu' il meccanismo atteso in produzione.

## Valutazione opzioni (A/B/C)

| Opzione | Esito | Motivazione |
|---|---|---|
| A — Rimuovere `output: 'export'` (SSR runtime) | Scartata | Rompe baseline deploy monolite statico, aumenta complessita' operativa e superficie runtime senza requisito business che lo giustifichi nel perimetro corrente. |
| B — Tenere export + AuthGuard client-side | **Scelta** | Preserva ADR deploy, risolve comportamento UX in produzione, mantiene backend come security boundary reale, minimizza blast radius. |
| C — Formalizzare middleware dev-only | Parziale | Utile come nota tecnica, ma insufficiente da sola: lascia gap UX permanente in produzione su US-073. |

## Conseguenze

1. **Produzione coerente con static export:** niente dipendenza da runtime Next middleware/Edge.
2. **AuthGuard esplicito nel codice FE runtime:** logica di redirect e role check vive in componenti client-side testabili.
3. **Sicurezza invariata:** il backend continua a imporre auth/authz indipendentemente dal frontend.
4. **CSP:** in produzione resta la policy lato backend; il nonce per-request in middleware resta limitato al dev finche' non si introduce runtime server FE.

## Migration steps (minimi)

1. Introdurre `ClientAuthGuard` riusabile (hook/provider) che implementa la matrice decisionale US-073.
2. Applicare il guard nei layout/pagine protette (`/analysis`, `/analysis/deep`, `/watchlist`, `/top-picks`, aree admin/profilo).
3. Conservare `returnUrl` e redirect post-login con test E2E dedicati.
4. Allineare `route-config`/mappa rotte per evitare divergenze tra rotte pubbliche/protette.
5. Ridurre `middleware.ts` a ruolo dev-only documentato (senza assumerlo attivo in prod).
6. Aggiornare Playwright smoke/auth flow per validare comportamento in build statica servita dal backend.

## Impatti su ADR esistenti

### ADR-009 (Deployment)

Nessun supersede del modello di deploy: `output: 'export'` e serving statico dal backend restano invariati. ADR-026 e' una specializzazione del comportamento auth FE dentro quel vincolo.

### ADR-013 (Routing analisi static export)

ADR-013 resta valido e rinforzato: il passaggio a query-param (`/analysis?ticker=`, `/analysis/deep?ticker=`) evita route dinamiche incompatibili con export statico. ADR-026 estende la stessa linea architetturale al tema AuthGuard.

### ADR-025 (Security hardening)

ADR-025 gia' dichiara l'AuthGuard frontend come UX e non security boundary. ADR-026 rende questa affermazione operativa in produzione sostituendo la dipendenza implicita dal middleware con un guard client-side compatibile con static export.

## Task operativi minimi (proposta TPM)

1. **FE:** estrarre `ClientAuthGuard` + `useAuthGuard` da applicare alle route protette.
2. **FE:** aggiornare pagine/login flow per `returnUrl` e redirect coerente post-auth.
3. **FE+QA:** test E2E su build statica (non solo `next dev`) per `unauth -> login -> returnUrl`, `forbidden -> /403`, `sessionExpired`.
4. **FE:** hardening documentale di `middleware.ts` come dev-only fallback.

## Relazioni

- Correlato a gap aperti: `fe-middleware-static-export-conflict`, `fe-deep-analysis-static-export-conflict`.
- Da accettare formalmente prima della taskizzazione L4 fase 2 (TPM).
