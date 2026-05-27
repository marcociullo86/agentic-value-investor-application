---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-28
tags: [security, compliance, pii, pci-dss, threat-model, gdpr, csrf, xss, fintech]
---
# Sicurezza, Privacy e Compliance Fintech

> Il frontend non e un trust boundary: ogni endpoint backend protetto verifica autenticazione e autorizzazione server-side indipendentemente da quanto fatto sul client.

## Contesto

REQ-05 del documento di iterazione fintech consolida i vincoli di sicurezza e compliance trasversali agli altri requisiti, estendendo REQ-01 (logging) e REQ-04 (auth guard). In caso di conflitto, prevalgono le indicazioni di REQ-05. [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

## Dettaglio

### 5.1 — Data privacy nei log

La lista dei campi sensibili e gestita **centralmente come configurazione**, non hardcoded nei singoli logger; aggiornabile senza redeploy. I pattern di redazione sono applicati ricorsivamente a oggetti nested, array, e stringhe contenenti JSON serializzato. [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

Regole di redazione minime:

| Campo | Regola |
|---|---|
| PAN | Solo BIN (prime 6) + ultime 4; mai PAN completo |
| CVV/CVC | Mai loggato in nessuna forma |
| IBAN | Paese (2 char) + ultime 4; in DEBUG ammessa estesa solo in non-prod |
| Email | In INFO solo dominio; in DEBUG ammessa completa in non-prod |
| JWT, API key, refresh token, segreti, password | Mai loggati |
| IP address | Anonimizzazione ultimo ottetto IPv4 / ultimi 80 bit IPv6 |

Vedi [[pii-redaction-checklist]] per la procedura implementativa e di verifica.

### Test di leak detection in CI

Una batteria di pattern regex (PAN, JWT, IBAN, etc.) viene eseguita in CI su log generati durante i test; il build fallisce al primo match. [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

### GDPR retention

I log contenenti dati personali rispettano una retention policy esplicita: 30 giorni operativi, 1 anno per security events, con cancellazione automatica documentata. Capacita di rimozione o pseudonimizzazione dei log riconducibili a uno specifico utente su richiesta (diritto all'oblio). [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

### 5.2 — Storage credenziali frontend

Vedi [[auth-guard-frontend]] per il dettaglio completo. In sintesi: access token in memoria, refresh token in cookie `httpOnly Secure SameSite=Strict`, localStorage proibito per dati sensibili, access token vita breve <= 15 minuti, refresh token rotation con revoca su riuso sospetto. [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

### 5.3 — Defense in depth

Principi cardine: [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

- L'AuthGuard frontend e una feature di UX, non un controllo di sicurezza.
- Il backend filtra i dati in base ai permessi dell'utente: il frontend non riceve mai dati che l'utente non ha diritto di vedere.
- Validazione input sempre presente lato server, indipendentemente da quella client.
- Nessuna business logic critica (calcolo commissioni, limiti di transazione, eligibility) replicata sul client come fonte di verita.

### 5.4 — Scope PCI-DSS (condizionale)

**Stato progetto (2026-05-28): PCI-DSS non applicabile** — dichiarazione formale in ADR-025 §8 (`status: accepted`, risolve Q_005). L'applicazione è un tool di screening azionario value investing: nessun flusso pagamento, nessun campo PAN/CVV nello schema DB, nessun provider Stripe/Adyen/Checkout.com. [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §8]

Applicabile solo se l'applicazione tratta dati di carta di pagamento. [^src: raw/requisiti-funzionali-fintech.md §REQ-05] In tal caso:

- PAN completo e CVV non transitano mai per server applicativi ne log.
- Uso di tokenization tramite provider certificato PCI-DSS (Stripe, Adyen, Checkout.com).
- Form di inserimento carta implementati tramite iframe/elements del provider per mantenere il frontend fuori dallo scope SAQ A-EP / D.
- Documentare il flusso dei dati di carta in un diagramma con perimetro PCI evidenziato.
- Se non applicabile, dichiararlo esplicitamente nell'ADR di sicurezza (soddisfatto da ADR-025).

### 5.5 — Threat model di baseline

| Minaccia | Mitigazione attesa |
|---|---|
| XSS | CSP rigorosa (no `unsafe-inline` su script), sanitizzazione output, framework con escape di default |
| CSRF | Cookie `SameSite=Strict` + token CSRF su richieste state-changing |
| Account takeover | MFA (TOTP minimo, idealmente WebAuthn), alert email su login da nuovo device/IP, rate limiting su login e password reset |
| Token theft | Access token a vita breve, refresh token rotation, revoca attiva alla detection di riuso |
| Brute force / credential stuffing | Rate limiting per IP e per account, lockout progressivo, CAPTCHA dopo soglia, integrazione HIBP |
| Session fixation | Rigenerazione session ID dopo login e dopo elevazione privilegi |

[^src: raw/requisiti-funzionali-fintech.md §REQ-05]

### 5.6 — Security events da loggare

I seguenti eventi sono loggati con livello INFO (success) o WARN/ERROR (failure), sempre con userId e contesto: [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

- Login success / failure (con motivo: credenziali errate, account bloccato, MFA fallita)
- Password change, password reset request/completion
- MFA enable / disable / fallback usage
- Permission / role grant o revoke
- Operazioni finanziarie rilevanti (creazione transazione, modifica beneficiario, cambio limiti)
- Accesso fallito a risorse protette (403)

## Acceptance criteria

- Scansione automatica in CI di leak segreti/PII nei log; build rosso al primo match.
- Pen test confermano che gli endpoint protetti rifiutano richieste non autorizzate indipendentemente dal frontend.
- ADR esplicito su: scelta storage token, scope PCI-DSS, threat model.
- Nessun token o credenziale rilevabile in localStorage ne nei log durante audit manuale.

## Concetti correlati

[[auth-guard-frontend]]
[[structured-logging-backend]]
[[correlation-id-tracing]]

## Pagine collegate

[[pii-redaction-checklist]]
[[webapp-architecture-vi]]
[[fintech-hardening-requirements-map]]

## Aggiornamenti (v2026-05-27)

**Migrazione storage credenziali completata (EP-017 / US-075, ADR-024).**

La §5.2 (Storage credenziali frontend) e ora implementata:

- Refresh token migrato a cookie `httpOnly Secure SameSite=Strict Path=/api/auth` (TSK-209).
- Access token in memoria (Zustand store), mai persistito; rehydration al mount via refresh cookie (TSK-211).
- `localStorage` non utilizzato per alcun token o dato sensibile.
- Token rotation attiva ad ogni refresh; revoca server-side implementata.
- OpenAPI aggiornata: rimosso `refreshToken` da response body (TSK-210).

[^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/TSK-209.md]

Dettaglio completo: [[auth-guard-frontend]] §Aggiornamenti (v2026-05-27).

## Aggiornamenti (v2026-05-27) — EP-018 Sprint 15 Wave 1

Implementazione parziale US-079 / US-080 / US-081 (5 TSK, code review e QA ancora pendenti):

| Area | TSK | Stato L5 |
|------|-----|----------|
| Defense-in-depth (§5.3) | TSK-219 | `@EnableMethodSecurity`, `/admin/**` → `ROLE_ADMIN`, `DefenseInDepthIT` (401/403/isolamento) |
| CSP / XSS (§5.5) | TSK-221 | `SecurityHeadersConfig` — `Content-Security-Policy` con `script-src 'self'` (no `unsafe-inline`) |
| HIBP / credential stuffing (§5.5) | TSK-231 | `HibpClient` k-anonymity su register; degradazione graceful se API down; disabilitato in profilo `test` |
| Schema DB MFA | TSK-225 | Flyway **`V026__create_mfa_secrets.sql`** (V018 occupato da `filing_analysis`) |
| Schema DB brute-force | TSK-226 | Flyway **`V025__create_login_attempts.sql`** (V019 occupato da `deep_analysis_event_log`) |

[^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §Schema DB]
[^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-079-defense-in-depth/TSK-219.md]
[^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/TSK-221.md]
[^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-231.md]

## Aggiornamenti (v2026-05-27) — EP-018 Sprint 15 Wave 2

Implementazione US-079 / US-080 / US-081 (5 TSK `done` in kanban; **CI verde** su `4c7ca73`; code review umana ancora pendente):

| Area | TSK | Stato L5 |
|------|-----|----------|
| QA defense-in-depth (§5.3) | TSK-220 | `DefenseInDepthIT` esteso (401/403/400, isolamento cross-user); Vitest guard statico FE (nessuna formula Graham/MoS/DCF client-only) |
| CSP / XSS FE (§5.5) | TSK-222 | Middleware Next.js: `Content-Security-Policy` con `script-src 'nonce-…'` (strict); in `next dev` policy rilassata per HMR (`unsafe-inline`/`unsafe-eval`, `connect-src` ws); anti-FOUC in `/theme-init.js`; gap [[gaps#fe-middleware-static-export-conflict]] con `output: 'export'` |
| CSRF cookie-based (§5.5) | TSK-223 | `CsrfTokenConfig`: cookie `XSRF-TOKEN` + header `X-CSRF-Token` su POST `/api/auth/refresh` e `/api/auth/logout`; SameSite=Strict; Bearer routes escluse |
| MFA TOTP core (§5.5) | TSK-227 | `TotpService` (secret, otpauth URI, TOTP ±1, 8 recovery BCrypt, AES-GCM per `totp_secret_encrypted`); `MfaSecretRepository`/controller in TSK-228 |
| Brute force / stuffing (§5.5) | TSK-229 | `RateLimitingFilter` + `AuthRateLimitService` su login/register/password-reset; conteggi `login_attempts` **per endpoint** (`rate_limit_probe:LOGIN|REGISTER|…`); body POST preservato (`RepeatableReadHttpServletRequest`); 429 + `Retry-After`; esiti auth reali in TSK-230 |

[^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-079-defense-in-depth/TSK-220.md]
[^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/TSK-222.md]
[^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/TSK-223.md]
[^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-227.md]
[^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/TSK-229.md]

### Stabilizzazione CI (post-Wave 2)

Dopo `feat(ep-018)` (`341610b`), la pipeline `ci` è stata riallineata con fix incrementali fino a **`4c7ca73`** (run #131 tutti job success):

| Fix | Commit | Dettaglio |
|-----|--------|-----------|
| Compile + IT isolation | `22cad86` | `CsrfTokenConfig` API Spring 6.5; `login_attemptRepository.deleteAll()` in IT auth; mockk in unit test |
| Rate limit path/body | `c20a303`, `3788852` | `requestURI` in filter; body JSON ripetibile per register/login |
| Conteggi per endpoint | `d4cd23a` | probe `failure_reason` scoped; HIBP suffix test |
| Playwright + profilo test | `4c7ca73` | CSP dev-mode per HMR; `app.fmp.base-url` valido; `fmpWebClient` disabilitato se `app.fmp.mock=true` |

[^src: .github/workflows/ci.yml]

**Wave 3 prossima:** integrazione MFA end-to-end (TSK-228), audit log tentativi auth (TSK-230), QA CSP+CSRF (TSK-224), client CSRF header su refresh/logout FE.

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-014 / [US-060](../../management/kanban/EP-014-logging-strutturato-observability/US-060-redazione-pii-log/US-060.md) — Redazione automatica PII nei log
- EP-014 / [US-061](../../management/kanban/EP-014-logging-strutturato-observability/US-061-leak-detection-ci/US-061.md) — Test leak detection PII in CI
- EP-014 / [US-062](../../management/kanban/EP-014-logging-strutturato-observability/US-062-security-events-logging/US-062.md) — Logging eventi di sicurezza
- EP-014 / [US-063](../../management/kanban/EP-014-logging-strutturato-observability/US-063-gdpr-retention-policy/US-063.md) — Policy retention log GDPR
- EP-017 / [US-075](../../management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/US-075.md) — Migrazione storage credenziali
- EP-018 / [US-079](../../management/kanban/EP-018-hardening-sicurezza-compliance/US-079-defense-in-depth/US-079.md) — Enforcement defense-in-depth
- EP-018 / [US-080](../../management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/US-080.md) — Protezione contro attacchi web
- EP-018 / [US-081](../../management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/US-081.md) — Protezione identità e accesso
- EP-018 / [US-082](../../management/kanban/EP-018-hardening-sicurezza-compliance/US-082-scope-pci-dss-adr/US-082.md) — Dichiarazione scope PCI-DSS e ADR sicurezza
