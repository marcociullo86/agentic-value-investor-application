---
id: ADR-010
title: Authentication consolidation — registration, login/logout, session lifetime
status: accepted
created: 2026-05-22
deciders: [lead-architect, marco.ciullo]
---
# ADR-010 — Authentication consolidation: US-018 / US-019

## Contesto

Sprint 3 Track B (TSK-028/029/033/034/035) ha portato su `master` un'implementazione completa di registrazione, login, refresh e logout JWT-stateless coerente con [ADR-006](ADR-006-authentication.md): `AuthController`, `AuthService`, `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, Flyway `V001__create_users_and_auth.sql`.

Il product-manager ha poi promosso (run 2026-05-22, opzione C "full reconcile") le user story dichiarate **tecnologia-agnostiche** che formalizzano i requisiti di business sopra:

- **US-018** "Registrazione utente con email+password" — `management/kanban/EP-006-watchlist-utente/US-018-registrazione-utente/US-018.md`
- **US-019** "Login con sessione e logout" — `management/kanban/EP-006-watchlist-utente/US-019-login-logout/US-019.md`

Le US introducono semantica nuova non interamente coperta da ADR-006 (che si concentrava su stack e shape del token):

1. AC US-018 #2: "registrazione con email già esistente viene rifiutata con messaggio chiaro" → richiede mapping HTTP esplicito (oggi solo eccezione applicativa).
2. AC US-019 #2: "credenziali non valide ricevono un errore di autenticazione generico, senza differenziare email inesistente da password errata" → policy va formalizzata e testata, non solo presente come stringa hardcoded.
3. AC US-019 #6: "dopo la durata massima di inattività documentata, la sessione decade" → introduce il concetto di **inactivity timeout** sulla sessione, non solo di TTL assoluto sui token.

Standard normativi rilevanti citati nei raw e in `raw/tech_stack.md` §Standards verbatim (PATTERN §11): **JWT (RFC 7519/7515)**, **BCrypt**, **RFC 9457 Problem Details**, **JSON (RFC 8259)**. Nessun protocollo SSO esterno (SAML/OIDC/SPID/eIDAS) è citato → vincolo §11 non applica.

[^src: management/kanban/EP-006-watchlist-utente/US-018-registrazione-utente/US-018.md §Business Rules]
[^src: management/kanban/EP-006-watchlist-utente/US-019-login-logout/US-019.md §Business Rules]
[^src: raw/tech_stack.md §Standards verbatim (PATTERN §11)]

## Decisione

Confermare lo stack di ADR-006 (Spring Security + JJWT 0.12+ + BCrypt) ed **estendere** ADR-006 con tre policy formalizzate, una per ogni gap di cui sopra. Nessuna rottura di contratto rispetto allo stato as-is su `master`; questo ADR è "lock-in" + 3 chiarimenti.

### 1. Registrazione (US-018) — error mapping formale

| Caso | HTTP | Body |
|---|---|---|
| Successo | `201 Created` | `UserProfile { id, email, displayName, createdAt }` |
| Email sintatticamente non valida | `400 Bad Request` | Problem Details `type=https://api/errors/validation-failed` |
| Password sotto soglia (min 12 char) | `400 Bad Request` | come sopra, `detail` esplicita la regola violata |
| Email già registrata | `409 Conflict` | Problem Details `type=https://api/errors/email-already-registered`, `detail="Email already registered: <masked>"` (NB: l'email viene riflessa solo se il client l'ha appena inviata — non leak per enum-attack su `/register`) |

Vincolo aggiuntivo: la `password_hash` colonna è VARCHAR(72) per BCrypt cost 12 (60 char `$2a$12$...` + slack). [^src: design_&_architecture/data/er-diagram.md §users]

Hashing: `BCryptPasswordEncoder(strength=12)` (RFC verbatim §11 — BCrypt). [^src: raw/tech_stack.md §Standards verbatim]

### 2. Login/logout (US-019) — generic error policy

| Caso | HTTP | Body |
|---|---|---|
| Login successo | `200 OK` | `TokenPair { accessToken, refreshToken, expiresInSeconds }` |
| Email inesistente **oppure** password errata | `401 Unauthorized` | Problem Details `type=https://api/errors/invalid-credentials`, `detail="Invalid email or password"` (testo IDENTICO per i due casi, contract-test obbligatorio) |
| Logout (con refresh in body) | `204 No Content` | corpo vuoto; revoca server-side del refresh (rotazione `revoked_at`) |

**Generic error policy** è un AC verificabile via contract-test: una singola stringa `"Invalid email or password"` viene emessa sia quando l'utente non esiste sia quando la password è errata. Il `JwtService` non vede mai `BadCredentialsException`, è `AuthService` a unificarla.

L'access token **non è revocabile** (stateless JWT). È per disegno: TTL breve (15 min) + refresh rotation = trade-off accettato. Logout efficace = revoca refresh + invalidazione client-side (zustand store cancella `accessToken` in memoria).

### 3. Session inactivity (US-019 AC#6) — sliding refresh

US-019 AC#6 richiede "scadenza per inattività". Lo stack as-is ha solo TTL assoluto sui token, non un concetto di "inactivity". Soluzione:

| Token | TTL | Politica |
|---|---|---|
| **Access** | 15 minuti **assoluti** dall'`iat` | invariato vs ADR-006 |
| **Refresh** | **7 giorni sliding** dall'ultimo `/refresh` di successo, con **cap assoluto 30 giorni** dalla prima emissione (login) | nuovo |

**Algoritmo `/api/auth/refresh`:**

1. Carica `refresh_tokens` by `token_value`. Se assente → `401 invalid-refresh`.
2. Se `revoked_at IS NOT NULL` → `401 invalid-refresh`.
3. Se `expires_at < now()` → `401 invalid-refresh` (inactivity → user ri-autentica).
4. Carica `first_issued_at = users.first_session_at_for(refresh.user_id)` — colonna nuova `refresh_tokens.first_issued_at TIMESTAMPTZ NOT NULL` (vedi Flyway sotto). Se `first_issued_at + 30d < now()` → `401 invalid-refresh` (cap assoluto raggiunto, ri-autentica).
5. Marca il refresh corrente `revoked_at = now()` (rotation).
6. Emetti nuovo access + nuovo refresh: `expires_at = now() + 7d`, `first_issued_at = previous.first_issued_at` (catena preservata).

**Login** (US-019 AC#1) emette refresh con `expires_at = now() + 7d`, `first_issued_at = now()`.

**Migration richiesta:** `V009__add_first_issued_at_to_refresh_tokens.sql`:

```sql
ALTER TABLE refresh_tokens ADD COLUMN first_issued_at TIMESTAMPTZ;
UPDATE refresh_tokens SET first_issued_at = expires_at - INTERVAL '30 days' WHERE first_issued_at IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN first_issued_at SET NOT NULL;
```

Configurazione esposta:
```yaml
app.jwt.access-ttl-minutes: 15
app.jwt.refresh-sliding-ttl-days: 7
app.jwt.refresh-absolute-cap-days: 30
```

### 4. Vincoli di sicurezza confermati

- BCrypt cost 12 (verbatim §11). [^src: raw/tech_stack.md §Standards verbatim]
- HS256 signing, secret ≥ 256 bit caricato da env `JWT_SIGNING_SECRET`. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/security/JwtService.kt §signingKey]
- CSRF disabilitato perché stateless + non-cookie auth (header `Authorization: Bearer`). [^src: design_&_architecture/decisions/ADR-006-authentication.md §Conseguenze]
- `SessionCreationPolicy.STATELESS` (Spring Security). [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/security/SecurityConfig.kt §securityFilterChain]
- 401 e 403 in formato `application/problem+json` (RFC 9457). [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Convenzioni]

## Motivazioni

1. **Compatibilità as-is**: l'implementazione Sprint 3 è già su `master` e funziona; un nuovo ADR di estensione costa meno di un superseding di ADR-006 ed evita rework del codice di TSK-033/034.
2. **Inactivity timeout sliding**: 7 giorni è la baseline industry-standard per webapp con dati finanziari personali (analogamente a banking apps). Il cap assoluto 30 giorni mitiga il rischio di refresh-token-eterno in caso di leak persistente (rotazione c'è, ma il client compromesso può tenerlo vivo all'infinito senza un cap).
3. **Generic error policy enforced by contract-test**: l'AC US-019#2 è verificabile staticamente solo confrontando i due body. Va in regression suite.
4. **Email duplicate → 409**: convenzione REST standard (RFC 9110 §15.5.10 Conflict). Allineato con `endpoints-overview.md` `/api/auth/register` 409 già presente.
5. **No SSO**: stessa giustificazione di ADR-006 §Motivazioni — nessun raw cita SAML/OIDC; over-engineering per MVP. Lasciato gap aperto `arch-auth-provider-choice` per R2 enterprise.

## Alternative considerate

- **Inactivity assoluta sul refresh (no sliding)**: più semplice ma costringe l'utente a ri-autenticare a giorno fisso anche se attivo → UX peggiore per analisi di lungo respiro. Scartato.
- **Sliding sul refresh senza cap assoluto**: rischio leak permanente. Scartato.
- **Logout server-side dell'access via blacklist Redis**: invalida la natura stateless di JWT, richiede infrastruttura extra. Scartato per MVP; rivalutabile in R2 se compliance richiede revoca immediata.
- **Refresh in `httpOnly` cookie vs body JSON**: ADR-006 menzionava cookie come "preferito". L'implementazione `master` usa body JSON. Confermo body JSON per coerenza con la SPA stateless (zustand store), accetto il trade-off XSS (mitigato da CSP futuro). **Gap aperto**: `arch-refresh-token-storage-fe` (vedi `wiki/gaps.md` proposta sotto).

## Conseguenze

- **Codice toccato**:
  - `AuthService.login()`/`refresh()` aggiornati con `first_issued_at` (cap assoluto) e sliding.
  - `RefreshToken` entity + repository: nuovo campo `firstIssuedAt`.
  - `GlobalExceptionHandler` (nuovo o esistente): mappa `EmailAlreadyRegisteredException` → 409 Problem Details.
  - Contract test: equivalenza di body per "email inesistente" vs "password errata" su `/api/auth/login`.
  - `application.yml`: nuove proprietà `refresh-sliding-ttl-days`, `refresh-absolute-cap-days`.
- **Schema**: nuova Flyway `V009__add_first_issued_at_to_refresh_tokens.sql`.
- **OpenAPI**: aggiornamento `/api/auth/register` per 409 documentato; nessun nuovo path.
- **Frontend**: FE deve esibire messaggio "Sessione scaduta, accedi di nuovo" su 401 da endpoint protetti, scatenando logout client-side (Zustand `useAuthStore.clear()`).
- **US sbloccate**: US-018 e US-019 implementabili senza ulteriori ADR.
- **Test E2E Playwright**: verificare il path inactivity (necessario time-travel via Clock mockabile in test, non end-to-end browser).

## Tracciabilità US → AC → policy

| US | AC | Policy/Comportamento |
|---|---|---|
| US-018 | #1 form register | `POST /api/auth/register` body `{email, password, displayName?}` |
| US-018 | #2 email duplicata rifiutata | 409 Problem Details `email-already-registered` |
| US-018 | #3 email sintatticamente invalida | 400 Problem Details `validation-failed` (Jakarta `@Email`) |
| US-018 | #4 account immediatamente utilizzabile | nessun flag verifica email → `/login` funziona subito |
| US-018 | #5 password mai in chiaro | `BCryptPasswordEncoder(12)`, hash 60-char persistito |
| US-018 | #6 persistenza cross-sessione | `users` UUID immutabile, FK target da `watchlists`/`dcf_method_override` |
| US-019 | #1 login emette sessione | 200 con `TokenPair` |
| US-019 | #2 errore generico | testo unico `Invalid email or password` (contract-test) |
| US-019 | #3 risorse personali gated | `SecurityConfig.requestMatchers(...).authenticated()` |
| US-019 | #4 isolamento per-utente | filtro `principal.userId` in `WatchlistService`/`DcfOverrideService`/`MoatChecklistService` |
| US-019 | #5 logout esplicito | `POST /api/auth/logout` revoca refresh |
| US-019 | #6 inactivity → ri-login | sliding 7d + cap 30d su `refresh_tokens` |

## Pagine collegate

- [ADR-006](ADR-006-authentication.md) — auth foundation (questo ADR estende, non supersedes)
- [ADR-007](ADR-007-api-contract.md) — Problem Details RFC 9457
- [data/er-diagram.md](../data/er-diagram.md) — schema `refresh_tokens` (aggiornare con `first_issued_at`)
- [api/openapi.yaml](../api/openapi.yaml) — `/api/auth/*` paths
- [[webapp-architecture-vi]]
- [[webapp-value-investing-spec]]
- US-018, US-019, US-017 (watchlist personale dipendente)
