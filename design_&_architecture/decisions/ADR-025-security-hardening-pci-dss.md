---
id: ADR-025
title: "Security Hardening, Threat Model & PCI-DSS Non-Applicability (EP-018, risolve Q_005)"
status: accepted
created: 2026-05-26
updated: 2026-05-28
deciders: [lead-architect, simone.olivieri]
resolves: [Q_005]
---
# ADR-025 — Security Hardening, Threat Model & PCI-DSS Non-Applicability

## Contesto

EP-018 (4 storie: US-079..082) richiede l'implementazione delle protezioni di sicurezza trasversali prescritte da REQ-05: defense-in-depth, protezione contro attacchi web (CSP, CSRF, XSS), protezione identità e accesso (MFA, rate limiting, brute force, session fixation), e dichiarazione formale dello scope PCI-DSS. [^src: management/kanban/EP-018-hardening-sicurezza-compliance/EP-018.md §Obiettivo]

Q_005 (soft) richiede una dichiarazione esplicita: l'applicazione è un tool di screening azionario value investing che non tratta dati di carta di pagamento — PCI-DSS è verosimilmente "non applicabile" ma serve ADR formale. [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-082-scope-pci-dss-adr/US-082.md §Business Rules]

L'architettura auth attuale (ADR-006/010/024) usa JWT stateless con access token in-memory e refresh token in cookie httpOnly (ADR-024). Spring Security è già configurato con filter chain. [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-079-defense-in-depth/US-079.md §Business Rules]

## Decisione

### 1. Defense-in-depth (US-079)

Principi enforcement backend, indipendenti dal comportamento frontend:

| Principio | Implementazione |
|---|---|
| Validazione server-side | `@Valid` su tutti i `@RequestBody` DTO; Bean Validation (Jakarta) con constraints espliciti |
| Filtro dati per permessi | `@PreAuthorize` o filtro nel service layer: ogni query filtra per `userId` dal `SecurityContext` |
| No business logic critica solo client | Calcoli finanziari (Margin of Safety, Graham Number, DCF) eseguiti esclusivamente backend (Rule Engine) |
| Endpoint protetti | Ogni endpoint protetto rifiuta 401 (no token) / 403 (no permessi) indipendentemente dall'header di origine |

L'AuthGuard frontend (ADR-024 §1) è esplicitamente documentato come "feature di UX, non controllo di sicurezza". [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-079-defense-in-depth/US-079.md §Business Rules]

### 2. Content Security Policy (US-080)

Header HTTP `Content-Security-Policy` configurato a due livelli:

#### Backend (Spring Security)

```java
// SecurityConfig.java
http.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp
        .policyDirectives(
            "default-src 'self'; " +
            "script-src 'self'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "connect-src 'self'; " +
            "font-src 'self'; " +
            "frame-src 'none'; " +
            "object-src 'none'; " +
            "base-uri 'self'; " +
            "form-action 'self'"
        )
    )
);
```

#### Frontend (Next.js)

Next.js middleware aggiunge CSP con **nonce** per gli script inline generati dal framework:

```typescript
// middleware.ts — CSP header
const nonce = crypto.randomUUID();
const csp = [
  `default-src 'self'`,
  `script-src 'self' 'nonce-${nonce}'`,
  `style-src 'self' 'unsafe-inline'`,  // Tailwind utility styles
  `img-src 'self' data: https:`,
  `connect-src 'self'`,
  `font-src 'self'`,
  `frame-src 'none'`,
  `object-src 'none'`,
].join('; ');
```

`script-src` **senza** `'unsafe-inline'`: gli inline script Next.js usano il nonce. `style-src 'unsafe-inline'` è accettabile: CSS inline non è un vettore XSS significativo ed è necessario per Tailwind/Radix. [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/US-080.md §Business Rules]

### 3. CSRF protection (US-080 + coordinamento ADR-024)

Con la migrazione del refresh token a cookie httpOnly (ADR-024 §3), la protezione CSRF è necessaria per gli endpoint che ricevono il cookie automaticamente:

| Difesa | Meccanismo |
|---|---|
| **Primaria** | `SameSite=Strict` su tutti i cookie (blocca cross-origin in browser moderni) |
| **Defense-in-depth** | Header `X-CSRF-Token` richiesto su `POST /api/auth/refresh` e `POST /api/auth/logout` |

Implementazione: Spring Security `CsrfTokenRepository` con cookie CSRF (`XSRF-TOKEN`) leggibile da JavaScript + header `X-CSRF-Token` validato server-side. CSRF abilitato **solo per gli endpoint cookie-based** (`/api/auth/refresh`, `/api/auth/logout`). Gli altri endpoint API usano `Authorization: Bearer` (non cookie) → CSRF non necessario (protezione intrinseca dagli header custom). [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/US-080.md §Business Rules]

### 4. MFA TOTP (US-081)

#### Flusso di enrollment

1. Utente autenticato chiama `POST /api/auth/mfa/enroll` → backend genera secret TOTP (RFC 6238), restituisce `{ secret, qrCodeUri, recoveryCodes[] }`.
2. Utente scansiona QR code con app authenticator (Google Authenticator, Authy, etc.).
3. Utente invia `POST /api/auth/mfa/verify` con `{ totpCode }` → backend verifica il codice TOTP → se valido: MFA abilitato (`mfa_secrets.enabled = true`).

#### Flusso di login con MFA

1. `POST /api/auth/login` con `{ email, password }`:
   - Se MFA **non** abilitato: risposta standard (access token + Set-Cookie refresh).
   - Se MFA abilitato: `200 OK` con body `{ mfaRequired: true, mfaToken: "temp-jwt-5min" }`. Nessun access token né refresh cookie emesso.
2. `POST /api/auth/mfa/challenge` con `{ mfaToken, totpCode }` → se valido: risposta standard (access token + Set-Cookie refresh).

#### Recovery codes

8 codici one-time, generati all'enrollment, hash BCrypt in DB. Utilizzabili via `POST /api/auth/mfa/recovery` come alternativa al TOTP code. Ogni codice è monouso (revocato dopo utilizzo). [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/US-081.md §Business Rules]

#### Componenti

| Componente | Layer | Ruolo |
|---|---|---|
| `TotpService` | BE | Generazione secret, verifica TOTP (libreria `dev.samstevens.totp`) |
| `MfaController` | BE | Endpoint enrollment, verify, challenge, recovery |
| `MfaEnrollmentPage` | FE | UI per QR code + verifica + display recovery codes |
| `MfaChallengeForm` | FE | Form input TOTP code durante login |

### 5. Rate limiting & brute force protection (US-081)

#### Rate limiting

Implementato con Resilience4j `RateLimiter` (già nello stack) o Spring filter custom:

| Endpoint | Limite per IP | Limite per account | Azione al superamento |
|---|---|---|---|
| `POST /api/auth/login` | 10 req/5 min | 5 req/5 min | 429 Too Many Requests con `Retry-After` header |
| `POST /api/auth/register` | 5 req/5 min | — | 429 |
| `POST /api/auth/password-reset` | 3 req/5 min | 3 req/5 min | 429 |

I limiti sono configurabili tramite `application.yml` (`app.security.rate-limiting.*`).

#### Brute force (progressive lockout)

| Tentativi falliti | Azione |
|---|---|
| 1-4 | Login fallito con messaggio generico (ADR-010 §2) |
| 5+ (in 5 min, stesso account) | Ritardo progressivo: 2^(n-5) secondi (cap 60s) |
| 10+ (in 5 min, stesso IP) | CAPTCHA richiesto nel form di login |
| 20+ (in 15 min, stesso account) | Account lockout temporaneo (30 min, auto-unlock) |

Il lockout è gestito dalla tabella `login_attempts` (§8). [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/US-081.md §Business Rules]

#### CAPTCHA

Raccomandazione: **Cloudflare Turnstile** (gratuito, privacy-friendly, GDPR-compatibile). Alternativa: hCaptcha. Integrazione: widget JS nel frontend, verifica server-side via API. La scelta specifica è un'implementazione detail del fe-dev/be-dev.

#### HIBP integration

`HibpClient` (servizio Spring) verifica la password in fase di registrazione e cambio password tramite l'API HIBP k-anonymity (invio solo primi 5 char SHA-1, nessuna password inviata in chiaro). Se la password compare in un breach noto: registrazione/cambio rifiutato con messaggio user-friendly. [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/US-081.md §Business Rules]

### 6. Session fixation protection (US-081)

Rigenerazione session ID (= nuovo refresh token + cookie) in due momenti:

1. **Dopo login riuscito**: il refresh token emesso al login è un nuovo token (non riuso di precedenti). ✓ Già coperto dalla rotation ADR-010/024.
2. **Dopo elevazione privilegi** (es. admin action): se un endpoint richiede conferma identità, il backend rigenera un nuovo refresh token cookie post-conferma.

Spring Security: `SessionFixationProtection.migrateSession()` (default in Spring Security, confermato attivo). [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/US-081.md §Business Rules]

### 7. Alert new device/IP (US-081)

Al login riuscito, il `SecurityEventLogger` (ADR-021 §6) logga l'IP e il User-Agent. Se l'IP o il device fingerprint non è nella history recente dell'utente (confronto con ultimi 5 login in `login_attempts`), l'evento viene flaggato `newDevice=true`. L'invio di alert email è un'estensione futura (richiede servizio email transazionale, non nello scope MVP). Per il MVP: flag nel log + evento di sicurezza. [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-081-protezione-identita-accesso/US-081.md §Business Rules]

### 8. Risoluzione Q_005: PCI-DSS — Non Applicabile

**Dichiarazione formale**: l'applicazione "Agentic Value Investor" è un tool di screening azionario per value investing. [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-082-scope-pci-dss-adr/US-082.md §Business Rules]

**Non tratta dati di carta di pagamento:**
- Nessun flusso di pagamento è implementato o pianificato.
- Nessun campo PAN, CVV, expiry date è presente nello schema DB.
- Nessun provider di pagamento (Stripe, Adyen, etc.) è integrato.
- Le uniche API esterne sono FMP (dati finanziari di mercato) e Anthropic (LLM).
- Gli utenti non inseriscono dati finanziari personali (solo credenziali di accesso e preferenze di analisi).

**Conseguenza**: i vincoli PCI-DSS §5.4 di REQ-05 (tokenization, iframe provider certificato, flusso dati carta documentato) **non si applicano**. Se in futuro l'applicazione dovesse integrare pagamenti, sarà necessario un nuovo ADR dedicato con valutazione SAQ e scoping PCI-DSS.

**Q_005 risolta**: questo ADR è la dichiarazione formale richiesta.

## Schema DB

### Tabella `mfa_secrets`

```sql
-- V026__create_mfa_secrets.sql (TSK-225; V018 = filing_analysis)
CREATE TABLE mfa_secrets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    totp_secret_encrypted VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT false,
    enabled_at TIMESTAMPTZ,
    recovery_codes_hash TEXT,  -- JSON array di hash BCrypt dei recovery codes
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Tabella `login_attempts`

```sql
-- V025__create_login_attempts.sql (TSK-226; V019 = deep_analysis_event_log)
CREATE TABLE login_attempts (
    id BIGSERIAL PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL,
    account_email VARCHAR(255),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(100),
    user_agent VARCHAR(500)
);

CREATE INDEX idx_login_attempts_ip_recent
    ON login_attempts (ip_address, attempted_at DESC);

CREATE INDEX idx_login_attempts_email_recent
    ON login_attempts (account_email, attempted_at DESC)
    WHERE account_email IS NOT NULL;
```

Retention: pulizia automatica record > 90 giorni (scheduled task Spring `@Scheduled`).

## API Endpoints (nuovi)

| Method | Path | Auth | Descrizione |
|---|---|---|---|
| `POST` | `/api/auth/mfa/enroll` | Bearer required | Genera secret TOTP + QR URI + recovery codes |
| `POST` | `/api/auth/mfa/verify` | Bearer required | Verifica codice TOTP per attivare MFA |
| `POST` | `/api/auth/mfa/challenge` | mfaToken required | Verifica TOTP durante login MFA |
| `POST` | `/api/auth/mfa/recovery` | mfaToken required | Usa recovery code per login MFA |
| `DELETE` | `/api/auth/mfa` | Bearer required | Disabilita MFA (richiede conferma password) |

Risposte in formato RFC 9457 ProblemDetail per errori (ADR-007/012).

## Componenti

| Componente | Layer | Package/Path |
|---|---|---|
| `SecurityHeadersConfig` | BE | `config` — CSP, X-Frame-Options, etc. |
| `CsrfTokenConfig` | BE | `config` — CSRF per endpoint cookie-based |
| `RateLimitingFilter` | BE | `security/filter` |
| `BruteForceProtectionService` | BE | `service` |
| `TotpService` | BE | `service` |
| `MfaController` | BE | `controller` |
| `HibpClient` | BE | `client` |
| `LoginAttemptRepository` | BE | `repository` |
| `MfaSecretRepository` | BE | `repository` |
| `MfaEnrollmentPage` | FE | `app/profile/mfa/page.tsx` |
| `MfaChallengeForm` | FE | `components/auth/mfa-challenge-form.tsx` |

## Configurazione

```yaml
app:
  security:
    csp:
      enabled: true
      report-only: false  # true per test iniziale senza enforcement
    csrf:
      enabled: true
    rate-limiting:
      login:
        per-ip: 10       # req per 5 min
        per-account: 5    # req per 5 min
      register:
        per-ip: 5
      password-reset:
        per-ip: 3
        per-account: 3
    brute-force:
      lockout-threshold: 20
      lockout-duration-minutes: 30
      captcha-threshold: 10
    mfa:
      issuer: "ValueInvestor"
      totp-period-seconds: 30
      recovery-codes-count: 8
    hibp:
      enabled: true
      api-url: "https://api.pwnedpasswords.com/range/"
```

## Motivazioni

1. **REQ-05 è prescrittivo**: la threat model baseline è documentata con tabella mitigazioni puntuali. Ogni mitigazione ha una storia dedicata con AC verificabili.
2. **TOTP come MFA minimo**: basso costo implementativo (libreria `dev.samstevens.totp` ~100KB), ampiamente supportato da app authenticator. WebAuthn è un'evoluzione futura (R2).
3. **Resilience4j per rate limiting**: già nello stack (ADR-004); riuso naturale senza nuove dipendenze per il pattern base. Per rate limiting avanzato (per-account), un filter custom con `login_attempts` è più flessibile.
4. **HIBP k-anonymity**: nessuna password inviata in chiaro; solo prefisso SHA-1 a 5 char. Privacy-preserving.
5. **CSP nonce pattern**: Next.js 16 supporta nativamente il nonce per inline scripts. Evita `'unsafe-inline'` su script-src.

## Alternative considerate

| Alternativa | Esito |
|---|---|
| WebAuthn come MFA primario | Complessità superiore (registrazione credenziali hardware/biometriche); richiede browser support verification. TOTP è più universale. Rivalutabile in R2. |
| WAF esterno (Cloudflare, AWS WAF) per CSP/rate limiting | Over-engineering per MVP; richiede infrastruttura esterna. La CSP e il rate limiting applicativi sono sufficienti. |
| reCAPTCHA v3 (Google) | Privacy concern GDPR per app fintech europea. Cloudflare Turnstile è preferibile. |
| Redis per rate limiting | Introduce dipendenza infrastrutturale. `login_attempts` in PostgreSQL è sufficiente per il volume MVP. Rivalutabile se il rate limiting deve scalare. |
| Session server-side per session fixation | Incompatibile con architettura stateless JWT. La rotation del refresh token cookie è il meccanismo equivalente. |

## Conseguenze

- **Q_005 risolta**: PCI-DSS formalmente "Non Applicabile" con motivazioni documentate.
- **US-079..082**: tutte implementabili con i componenti descritti.
- **Schema DB**: 2 nuove tabelle (`mfa_secrets`, `login_attempts`) + 2 migration Flyway.
- **API**: 5 nuovi endpoint MFA. OpenAPI da aggiornare.
- **Dipendenze npm**: Cloudflare Turnstile widget (o equivalente CAPTCHA).
- **Dipendenze Maven**: `dev.samstevens.totp` (TOTP), nessuna altra dipendenza significativa.
- **Coordinamento ADR-024**: CSRF protection per endpoint cookie-based.
- **Coordinamento ADR-021**: tutti gli eventi di sicurezza (login failure, MFA, 403, lockout) loggati con formato strutturato.
- **Gap `fintech-pci-dss-scope`** in `wiki/gaps.md`: risolvibile dopo accettazione di questo ADR.

## Pagine collegate

- [ADR-024](ADR-024-session-lifecycle-credential-storage.md) — session lifecycle (coordinamento CSRF, token storage)
- [ADR-021](ADR-021-structured-logging-pii-redaction.md) — security events logging (coordinamento)
- [ADR-006](ADR-006-authentication.md) — auth foundation
- [ADR-010](ADR-010-auth-consolidation.md) — auth consolidation
- [ADR-007](ADR-007-api-contract.md) — API contract, error format RFC 9457
