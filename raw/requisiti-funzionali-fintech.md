# Documento Funzionale — Iterazione Progetto Fintech

> **Scopo del documento:** descrivere in modo non ambiguo i nuovi requisiti funzionali e non funzionali, fornendo all'LLM contesto sufficiente per generare codice, task breakdown, ADR o test plan coerenti.
> **Stack di riferimento (assunto):** Frontend SPA (React/Angular/Vue) + Backend a servizi. Adattare le indicazioni allo stack effettivo del repository.

---

## 1. Contesto

Il progetto è un'applicazione fintech in evoluzione. Questa iterazione introduce miglioramenti trasversali su **osservabilità backend**, **esperienza utente in caso di errore**, **qualità e accessibilità della UI** e **sicurezza delle rotte frontend**. Nessuno dei requisiti introduce nuovi domini di business: si tratta di hardening e refinement.

---

## 2. Requisiti

### REQ-01 — Sistema di logging backend ottimizzato e human-readable

**Obiettivo:** rendere i log immediatamente comprensibili a uno sviluppatore in fase di triage, senza sacrificare la machine-readability necessaria per ingestion in sistemi di aggregazione.

**Requisiti funzionali:**
- Logging **strutturato in JSON** in produzione, **pretty-printed e colorato** in sviluppo locale.
- Ogni log deve contenere almeno: `timestamp` (ISO 8601 con timezone), `level`, `service`, `traceId`, `spanId`, `userId` (se autenticato e non PII-sensibile), `message`, `context` (oggetto con dati rilevanti).
- **Correlation ID** propagato lungo l'intera richiesta (HTTP header `X-Correlation-Id`, fallback con UUID v4 generato a ingresso).
- Livelli standard: `TRACE | DEBUG | INFO | WARN | ERROR | FATAL`. Livello configurabile via env var senza redeploy.
- Messaggi in **linguaggio naturale**, in inglese, con verbo all'inizio (es. `"Failed to process payment intent"` invece di `"PaymentService.process error"`).
- **Redazione automatica** dei campi sensibili: PAN, CVV, IBAN completo (mostrare ultime 4 cifre), password, token JWT, segreti API, email completa (mostrare solo dominio quando non strettamente necessario).
- Errori applicativi devono includere stack trace **solo da `ERROR` in su**, e mai esporre path filesystem del server in risposte client.
- Performance: il logging non deve aggiungere più di **2ms p99** alla latenza della richiesta. Usare logger async/buffered.

**Acceptance criteria:**
- Un log di errore deve permettere a uno sviluppatore di capire **cosa è successo, dove, per chi e in quale richiesta** senza aprire altri sistemi.
- I correlation ID sono presenti nel 100% dei log generati durante una richiesta HTTP.
- Nessun campo sensibile compare in chiaro nei log (verificato tramite test automatici su una lista di pattern).

---

### REQ-02 — Sistema di notifiche errori frontend ottimizzato e accessibile

**Obiettivo:** comunicare all'utente errori e stati problematici in modo chiaro, non bloccante quando possibile, e completamente accessibile.

**Requisiti funzionali:**
- Componente centralizzato `NotificationService` (toast/snackbar) con API tipizzata: `success | info | warning | error`.
- Mappatura **codici errore backend → messaggi user-friendly** localizzati. L'utente non vede mai stack trace o codici nudi (es. `500`); vede invece messaggi azionabili (es. *"Non siamo riusciti a completare il pagamento. Riprova tra qualche istante."*).
- Le notifiche di errore includono, quando disponibile, un **correlation ID** copiabile per facilitare il supporto.
- **Accessibilità (WCAG 2.2 AA):**
  - `role="status"` per messaggi informativi, `role="alert"` per errori, con `aria-live="polite"` o `assertive` coerente.
  - Contrasto colore ≥ 4.5:1 sul testo, ≥ 3:1 su icone e bordi.
  - Le notifiche non si chiudono automaticamente se contengono azioni; per quelle auto-dismiss, durata minima **6 secondi** (≥ 8s per testi lunghi) e pausa al focus/hover.
  - Tutte le notifiche sono **dismissibili da tastiera** (Esc chiude la più recente).
  - Non veicolare informazione **solo tramite colore** (icona + colore + testo).
- **Errori di form** mostrati inline accanto al campo + sintesi accessibile in cima al form al submit fallito (`aria-describedby` sui campi invalidi).
- Errori di rete: distinguere tra **offline**, **timeout**, **errore server**, **errore di validazione** con messaggi e CTA diverse (es. retry, ricarica, contatta supporto).

**Acceptance criteria:**
- Audit con axe-core / Lighthouse: zero violazioni critiche relative alle notifiche.
- Test con screen reader (NVDA/VoiceOver) confermano annuncio corretto.
- Nessun errore raw del backend raggiunge l'utente finale.

---

### REQ-03 — Refinement UI: accessibilità e Material Design 3

**Obiettivo:** allineare la UI ai nuovi standard di Material Design (M3) e ai principi di accessibilità senza riscrivere l'app.

**Requisiti funzionali:**
- Adozione del **design token system** di Material 3: colori derivati da un seed color tramite schema dinamico (`primary`, `secondary`, `tertiary`, `surface`, `surface-container`, etc.), tipografia in scala M3 (`display`, `headline`, `title`, `body`, `label`).
- Supporto **light/dark theme** con switch utente persistente; rispetto di `prefers-color-scheme` come default.
- Componenti aggiornati alle linee guida M3: **bottoni** (filled, tonal, elevated, outlined, text), **chips**, **navigation bar / rail**, **FAB**, **cards**, **dialogs**.
- **Shape system:** corner radius coerenti via token (`shape.small`, `shape.medium`, `shape.large`).
- **Stati interattivi:** hover, focus, pressed, dragged tramite state layers M3 (overlay con opacità coerente, non colori hardcoded).
- **Motion:** transizioni con easing M3 (`emphasized`, `standard`) e durate dei token; rispetto di `prefers-reduced-motion` (disabilitare/ridurre animazioni decorative).

**Accessibilità (WCAG 2.2 AA come baseline):**
- Contrasto testo ≥ 4.5:1, testo grande ≥ 3:1, UI components ≥ 3:1.
- **Target touch** ≥ 24×24 CSS px (idealmente 48×48 su mobile), con spacing adeguato.
- **Focus visibile** sempre, non rimuovere outline senza fornire alternativa equivalente (success criterion 2.4.11/2.4.13 di WCAG 2.2).
- Tutte le interazioni completabili **da sola tastiera**; ordine di tab logico.
- Heading hierarchy corretta (un solo `h1` per vista, no salti di livello).
- Form labels esplicite (`<label for>` o `aria-label`), error message collegati via `aria-describedby`.
- Immagini con `alt` semantico; icone decorative con `aria-hidden="true"`.
- Supporto zoom fino a **200%** senza loss of content o funzionalità.

**Acceptance criteria:**
- Punteggio Lighthouse Accessibility ≥ 95 su tutte le viste principali.
- Audit axe-core senza issue di severità *serious* o *critical*.
- Verifica manuale con tastiera e screen reader sui flussi critici (login, dashboard, transazione, profilo).

---

### REQ-04 — AuthGuard sulle sezioni protette del frontend

**Obiettivo:** garantire che le sezioni che richiedono autenticazione (e, dove pertinente, specifici ruoli/permessi) siano inaccessibili a utenti non autorizzati lato client, in modo coerente con i controlli backend.

**Requisiti funzionali:**
- Definire un **AuthGuard** centralizzato che intercetti la navigazione verso rotte protette.
- Comportamenti:
  - Utente **non autenticato** → redirect a `/login` con `returnUrl` preservato in query string; dopo login → redirect alla rotta originale.
  - Utente **autenticato ma senza il ruolo/permission richiesto** → redirect a `/403` (pagina dedicata, non un toast).
  - **Sessione scaduta** rilevata durante navigazione → logout silente + redirect a login con messaggio informativo.
- **Mappa rotte → requisiti** dichiarativa (es. metadata `requiresAuth: true`, `roles: ['admin']`, `permissions: ['payments:read']`).
- Refresh token automatico **prima** della scadenza dell'access token (con margine di sicurezza, es. 60s); coda delle richieste in caso di refresh in corso.
- Token **mai persistiti in `localStorage`** se contengono dati sensibili: preferire `httpOnly cookie` (gestito dal BE) o, in alternativa motivata, `sessionStorage` + protezione XSS rigorosa.
- **Logout** invalida sessione lato BE, pulisce stato client (store, cache query), e impedisce navigazione "indietro" verso rotte protette.
- L'AuthGuard è **complementare**, non sostitutivo, dei controlli backend: ogni endpoint protetto continua a verificare i permessi server-side.

**Acceptance criteria:**
- Nessuna rotta protetta è raggiungibile via URL diretto da utente non autenticato.
- Refresh F5 su rotta protetta da utente autenticato non causa flicker verso login.
- Test E2E coprono: accesso non autenticato, ruolo insufficiente, sessione scaduta, refresh token, logout.

---

### REQ-05 — Sicurezza, privacy e compliance fintech-specific

**Obiettivo:** consolidare i vincoli di sicurezza e compliance che attraversano gli altri requisiti, esplicitando le scelte di default attese in un contesto fintech. Questo requisito **estende** REQ-01 e REQ-04: in caso di conflitto, prevalgono le indicazioni di REQ-05.

#### 5.1 Data privacy nei log (estende REQ-01)

- Lista dei campi "sensibili" gestita **centralmente come configurazione**, non hardcoded nei singoli logger; aggiornabile senza redeploy.
- Pattern di redazione applicati **ricorsivamente** a oggetti nested e array, anche dentro stringhe (es. JSON serializzato in un messaggio).
- Regole di redazione minime:
  - **PAN:** mostrare solo BIN (prime 6) + last 4, oscurare il resto. Mai loggare PAN completo, in nessun livello.
  - **CVV / CVC:** mai loggati, in nessuna forma, in nessun livello.
  - **IBAN:** mostrare paese (2 char) + last 4. In `DEBUG` ammessa visualizzazione estesa solo in ambienti non-prod.
  - **Email:** in `INFO` solo dominio; in `DEBUG` ammessa email completa in non-prod.
  - **JWT, API key, refresh token, segreti, password (anche hashed):** mai loggati.
  - **IP address:** anonimizzazione dell'ultimo ottetto (IPv4) / ultimi 80 bit (IPv6) quando non necessari ad analisi di sicurezza.
- **Test di leak detection** in CI: batteria di pattern (regex PAN, JWT, IBAN, etc.) eseguita su un set di log generati durante i test; il build fallisce in caso di match.
- **GDPR retention:** i log contenenti dati personali rispettano una retention policy esplicita (es. 30 giorni operativi, 1 anno per security events), con cancellazione automatica documentata.
- Diritto all'oblio: capacità di rimuovere o pseudonimizzare i log riconducibili a uno specifico utente su richiesta.

#### 5.2 Storage credenziali frontend (estende REQ-04)

- **Default architetturale obbligatorio:**
  - **Access token in memoria** (stato applicativo, non persistito su disco). Si accetta il re-login dopo reload come trade-off, oppure rehydration via refresh token.
  - **Refresh token in cookie `httpOnly Secure SameSite=Strict`**, gestito esclusivamente dal backend.
- **`localStorage` proibito** per token, identificativi di sessione, dati personali o finanziari (vettore primario per furti via XSS).
- **`sessionStorage`** ammesso solo per dati non sensibili (es. preferenze UI di sessione) e con motivazione esplicita in code review.
- Access token a **vita breve** (≤ 15 minuti); refresh token con **rotation** ad ogni uso e **revoca server-side** in caso di riuso sospetto.
- **Idle timeout** configurabile (default 15 min) con prompt utente prima del logout automatico; logout assoluto per sessioni > N ore (configurabile).
- Logout esegue: revoca refresh token lato BE → cancellazione cookie → pulizia store/query cache → blocco history navigation verso rotte protette.

#### 5.3 Defense in depth — il frontend non è un trust boundary

- L'AuthGuard frontend è una **feature di UX**, non un controllo di sicurezza. Ogni endpoint backend protetto **deve** verificare autenticazione e autorizzazione server-side, indipendentemente da quanto fatto sul client.
- Il backend filtra i dati in base ai permessi dell'utente: il frontend **non deve mai ricevere** dati che l'utente non ha diritto di vedere, anche se poi sceglie di non mostrarli.
- Validazione input **sempre presente lato server**, indipendentemente da quella client.
- Nessuna business logic critica (calcolo commissioni, limiti di transazione, eligibility) viene replicata sul client come fonte di verità: il client è una vista, il server è l'autorità.

#### 5.4 Scope PCI-DSS (condizionale)

Applicabile **solo se** l'applicazione tratta dati di carta di pagamento. In tal caso:

- **PAN completo e CVV non transitano mai** per server applicativi né log dell'organizzazione.
- Uso di **tokenization** tramite provider certificato PCI-DSS (Stripe, Adyen, Checkout.com o equivalente).
- Form di inserimento carta implementati tramite **iframe / elements del provider**, per mantenere il frontend fuori dallo scope SAQ A-EP / D.
- Documentare il **flusso dei dati di carta** in un diagramma allegato all'architettura, con perimetro PCI evidenziato.
- Se questa condizione non si applica, dichiararlo esplicitamente nell'ADR di sicurezza.

#### 5.5 Threat model di baseline

Lista minima di minacce considerate e relative mitigazioni attese:

| Minaccia | Mitigazione attesa |
|---|---|
| **XSS** | CSP rigorosa (no `unsafe-inline` su script), sanitizzazione output, divieto di `dangerouslySetInnerHTML` / `innerHTML` con input non sanitizzato, framework con escape di default |
| **CSRF** | Cookie `SameSite=Strict` + token CSRF su richieste state-changing quando si usano cookie di sessione |
| **Account takeover** | MFA disponibile (TOTP minimo, idealmente WebAuthn), alert email su login da nuovo device/IP, rate limiting su login e password reset |
| **Token theft** | Access token a vita breve, refresh token rotation, revoca attiva alla detection di riuso |
| **Brute force / credential stuffing** | Rate limiting per IP e per account, lockout progressivo, CAPTCHA dopo soglia, integrazione con database di credenziali compromesse (es. HIBP) |
| **Session fixation** | Rigenerazione session ID dopo login e dopo elevazione privilegi |

#### 5.6 Security events da loggare (estende REQ-01)

I seguenti eventi sono loggati con livello `INFO` (success) o `WARN`/`ERROR` (failure), sempre con `userId` (o tentativo di identificazione) e contesto:

- Login success / failure (con motivo: credenziali errate, account bloccato, MFA fallita).
- Password change, password reset request, password reset completion.
- MFA enable / disable / fallback usage.
- Permission / role grant o revoke.
- Operazioni finanziarie rilevanti (creazione transazione, modifica beneficiario, cambio limiti).
- Accesso fallito a risorse protette (403).

**Acceptance criteria di REQ-05:**

- Scansione automatica in CI di leak di segreti/PII nei log su dataset di test; build rosso al primo match.
- Pen test (interno o esterno, almeno una volta per release maggiore) confermano che gli endpoint protetti rifiutano richieste non autorizzate **indipendentemente dal comportamento del frontend**.
- ADR esplicito che documenta: scelta di storage dei token, scope PCI-DSS, threat model di riferimento.
- Nessun token o credenziale rilevabile in `localStorage` né nei log durante audit manuale dei flussi principali.

---

## 3. Requisiti non funzionali trasversali

- **Sicurezza / privacy / compliance:** vedi **REQ-05** (sezione autoritativa). Qui si ricorda solo che non deve esserci regressione su CSP, CORS e security headers (HSTS, X-Content-Type-Options, Referrer-Policy, Permissions-Policy).
- **Internazionalizzazione:** tutti i messaggi (notifiche, errori, UI, log destinati all'utente) passano dal layer i18n. Niente stringhe hardcoded. I log applicativi tecnici restano in inglese.
- **Testing:** coverage minima 80% sui moduli toccati; test E2E per i flussi auth-guarded, le notifiche errore e i path PII-sensitive del logging.
- **Documentazione:** aggiornamento di README e ADR per le scelte rilevanti — almeno: strategia di storage token, libreria di logging scelta, threat model, scope PCI-DSS (anche solo per dichiararlo non applicabile).
- **Stack-awareness:** quando si delegano task all'LLM partendo da questo documento, specificare sempre lo stack reale (es. *NestJS + Pino + class-validator*, *React 18 + MUI v6 + TanStack Query*, *Angular 18 + Angular Material*, etc.). Risposte generate senza stack noto vanno trattate come pseudocodice.

---

## 4. Out of scope

- Refactor del dominio di business.
- Migrazione del framework frontend.
- Introduzione di nuovi servizi backend.
- Redesign visuale completo (questa è un'iterazione di refinement, non un rebrand).

---

## 5. Output attesi dall'LLM

A seconda del prompt successivo, l'LLM dovrebbe poter produrre:
1. **Task breakdown** per sprint planning, con stime relative.
2. **Codice di esempio** o scaffolding (logger config, AuthGuard, NotificationService, theme tokens).
3. **Checklist di review** per PR che toccano questi ambiti.
4. **Test plan** (unit, integration, E2E, accessibilità).
5. **ADR** per le decisioni architetturali rilevanti.

Quando si richiede codice, specificare sempre **stack effettivo** (es. NestJS + Pino, React + MUI v6, Angular + Angular Material 18, etc.) per evitare risposte generiche.
