---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-27
tags: [auth, auth-guard, frontend, token, session, security, fintech]
---
# AuthGuard sulle Sezioni Protette del Frontend

> L'AuthGuard frontend e una feature di UX, non un controllo di sicurezza: ogni endpoint backend protetto verifica autenticazione e autorizzazione server-side indipendentemente dal client.

## Contesto

REQ-04 del documento di iterazione fintech prescrive un AuthGuard centralizzato che intercetta la navigazione verso rotte protette, garantendo coerenza con i controlli backend. [^src: raw/requisiti-funzionali-fintech.md §REQ-04] REQ-05 §5.3 chiarisce che il frontend non e un trust boundary. [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

## Dettaglio

### Comportamenti dell'AuthGuard

| Stato utente | Azione |
|---|---|
| Non autenticato | Redirect a `/login` con `returnUrl` preservato in query string; dopo login, redirect alla rotta originale |
| Autenticato senza ruolo/permission richiesto | Redirect a `/403` (pagina dedicata, non un toast) |
| Sessione scaduta durante navigazione | Logout silente + redirect a login con messaggio informativo |

[^src: raw/requisiti-funzionali-fintech.md §REQ-04]

### Mappa rotte dichiarativa

Le rotte sono associate ai requisiti di autenticazione tramite metadata dichiarativi: `requiresAuth: true`, `roles: ['admin']`, `permissions: ['payments:read']`. [^src: raw/requisiti-funzionali-fintech.md §REQ-04]

### Ciclo di vita del token

Il **refresh token** viene rinnovato automaticamente prima della scadenza dell'access token (con margine di sicurezza di ~60s). Una coda gestisce le richieste in corso durante il refresh. [^src: raw/requisiti-funzionali-fintech.md §REQ-04]

### Storage dei token

REQ-05 §5.2 prescrive il default architetturale obbligatorio: [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

- **Access token in memoria** (stato applicativo, non persistito su disco); re-login dopo reload o rehydration via refresh token.
- **Refresh token in cookie `httpOnly Secure SameSite=Strict`**, gestito esclusivamente dal backend.
- **`localStorage` proibito** per token, identificativi di sessione, dati personali o finanziari.
- Access token a **vita breve** (<= 15 minuti); refresh token con **rotation** ad ogni uso e revoca server-side in caso di riuso sospetto.

### Idle e absolute timeout

Idle timeout configurabile (default 15 min) con prompt utente prima del logout automatico. Logout assoluto per sessioni superiori a N ore (configurabile). [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

### Logout

Il flusso di logout esegue in sequenza: revoca refresh token lato BE, cancellazione cookie, pulizia store e query cache, blocco navigazione history verso rotte protette. [^src: raw/requisiti-funzionali-fintech.md §REQ-04]

## Acceptance criteria

- Nessuna rotta protetta raggiungibile via URL diretto da utente non autenticato.
- Refresh F5 su rotta protetta da utente autenticato non causa flicker verso login.
- Test E2E coprono: accesso non autenticato, ruolo insufficiente, sessione scaduta, refresh token, logout.

## Concetti correlati

[[fintech-security-compliance]]

## Pagine collegate

[[webapp-architecture-vi]]
[[correlation-id-tracing]]
[[structured-logging-backend]]

## Aggiornamenti (v2026-05-27)

**EP-017 Protezione Rotte e Sessione — Sprint 14 completato (14/14 TSK done).**

Tutte le feature descritte in questa pagina sono ora implementate in `src/frontend/` e `src/backend/`:

- **Route map dichiarativa** (TSK-205): interfaccia `RouteConfig` e `ROUTE_MAP` con 11 rotte in `src/frontend/lib/auth/route-config.ts`; helper `getRouteConfig`, `isProtectedRoute`, `getRequiredRoles`. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-074-mappa-rotte-dichiarativa/TSK-205.md]
- **AuthGuard middleware Next.js** (TSK-206): middleware in `src/frontend/middleware.ts` con 4 stati (non autenticato → redirect login con `returnUrl`, autenticato senza ruolo → `/403`, sessione scaduta → logout + `/login?expired=true`, autenticato su `/login` → redirect `/`). [^src: management/kanban/EP-017-protezione-rotte-sessione/US-073-auth-guard-centralizzato/TSK-206.md]
- **Pagina /403 e banner sessione scaduta** (TSK-207): pagina "Accesso negato" accessibile e banner informativo con query param `?expired=true`. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-073-auth-guard-centralizzato/TSK-207.md]
- **Migrazione cookie httpOnly** (TSK-209, ADR-024): refresh token migrato da JSON body a cookie `httpOnly Secure SameSite=Strict Path=/api/auth`; `RefreshTokenCookieHelper.kt` centralizzato; `AccessTokenResponse` sostituisce `TokenPairResponse` (deprecated); login emette `{ accessToken, expiresInSeconds }` + Set-Cookie; refresh con rotation; logout revoca + cancella cookie + 204. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/TSK-209.md]
- **OpenAPI cleanup** (TSK-210): rimosso `refreshToken` da response schema, documentato Set-Cookie header. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/TSK-210.md]
- **Rehydration F5 trasparente** (TSK-211): `AuthProvider.tsx` bootstrap rehydration al mount (POST `/api/auth/refresh` con cookie); `AuthGuard.tsx` attende rehydration prima di valutare auth; cookie hint `isAuthenticated` (non-httpOnly) per middleware Edge. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/TSK-211.md]
- **Token refresh pre-expiry con mutex** (TSK-213): timer pre-expiry 60s, refresh automatico silenzioso; `token-refresh-mutex.ts` singleton, richieste concorrenti coalescono su stessa Promise; 401 interceptor in `client.ts`. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-076-rinnovo-automatico-token/TSK-213.md]
- **Idle/absolute timeout con prompt accessibile** (TSK-215): idle timeout 15min (`NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES`), prompt modale countdown 60s; absolute timeout 8h (`NEXT_PUBLIC_ABSOLUTE_TIMEOUT_HOURS`); `role="alertdialog"`, focus trap, Escape key. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-077-timeout-inattivita-assoluto/TSK-215.md]
- **Logout completo con back-button blocking** (TSK-217): sequenza revoca BE (best-effort) → clear store → SWR cache wipe → sessionStorage cleanup → `history.replaceState` + `router.push('/login')`; resiliente anche se revoca fallisce. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-078-flusso-logout-completo/TSK-217.md]

**Gap aperto:** `fe-middleware-static-export-conflict` — il middleware Next.js (TSK-206) non funziona con `output: 'export'`; necessaria valutazione alternativa per deploy statico.

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-017 / [US-073](../../management/kanban/EP-017-protezione-rotte-sessione/US-073-auth-guard-centralizzato/US-073.md) — Guardia autenticazione centralizzata
- EP-017 / [US-074](../../management/kanban/EP-017-protezione-rotte-sessione/US-074-mappa-rotte-dichiarativa/US-074.md) — Mappa rotte dichiarativa
- EP-017 / [US-075](../../management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/US-075.md) — Migrazione storage credenziali
- EP-017 / [US-076](../../management/kanban/EP-017-protezione-rotte-sessione/US-076-rinnovo-automatico-token/US-076.md) — Rinnovo automatico token con coda richieste
- EP-017 / [US-077](../../management/kanban/EP-017-protezione-rotte-sessione/US-077-timeout-inattivita-assoluto/US-077.md) — Timeout inattività e assoluto con prompt
- EP-017 / [US-078](../../management/kanban/EP-017-protezione-rotte-sessione/US-078-flusso-logout-completo/US-078.md) — Flusso logout completo
