---
id: ADR-024
title: Session Lifecycle & Credential Storage Hardening (EP-017)
status: proposed
created: 2026-05-26
deciders: [lead-architect]
supersedes_scope: "ADR-010 §Alternative 'Refresh in httpOnly cookie vs body JSON' e §4 punto CSRF — ora adottato cookie httpOnly per refresh token"
---
# ADR-024 — Session Lifecycle & Credential Storage Hardening

## Contesto

EP-017 (6 storie: US-073..078) richiede un AuthGuard centralizzato, migrazione storage credenziali a pattern sicuro (access token in memoria, refresh token in cookie httpOnly), refresh automatico con coda richieste, idle/absolute timeout e logout completo. [^src: management/kanban/EP-017-protezione-rotte-sessione/EP-017.md §Obiettivo]

L'architettura auth corrente (ADR-006 + ADR-010) usa:
- Access token JWT 15min in memoria Zustand ✓ (invariato)
- Refresh token inviato e ricevuto nel **body JSON** delle richieste `/api/auth/login` e `/api/auth/refresh`
- CSRF disabilitato perché "stateless + non-cookie auth"
- Inactivity timeout: sliding 7d + cap assoluto 30d sul refresh token

REQ-05 §5.2 prescrive un default architetturale diverso: refresh token in **cookie `httpOnly Secure SameSite=Strict`**, localStorage proibito per dati sensibili. Questa migrazione cambia il modello di autenticazione e richiede l'abilitazione di CSRF protection per gli endpoint che usano il cookie. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/US-075.md §Business Rules]

## Decisione

### 1. AuthGuard (Next.js middleware)

Implementato come `middleware.ts` di Next.js (App Router). Intercetta le navigazioni e applica le regole della route map dichiarativa (§2):

| Stato utente | Azione |
|---|---|
| Non autenticato → rotta protetta | Redirect a `/login?returnUrl={originalPath}` |
| Autenticato senza ruolo richiesto | Redirect a `/403` (pagina dedicata, non toast) |
| Sessione scaduta durante navigazione | Logout silente → redirect a `/login` con messaggio "La sessione è scaduta" |
| Autenticato → rotta login/register | Redirect a `/` (o returnUrl se presente) |

L'AuthGuard è una **feature di UX**, non un controllo di sicurezza: ogni endpoint backend verifica autenticazione e autorizzazione server-side indipendentemente dal client (defense-in-depth, ADR-025 §1). [^src: management/kanban/EP-017-protezione-rotte-sessione/US-073-auth-guard-centralizzato/US-073.md §Business Rules]

**Verifica auth in middleware:** il middleware legge lo stato di autenticazione dal cookie di sessione applicativo (un cookie non-httpOnly con flag `isAuthenticated=true`, distinto dal refresh token httpOnly). Questo cookie è un hint per il middleware — la verità resta sul server.

### 2. Mappa rotte dichiarativa

File `lib/auth/route-config.ts` con configurazione tipizzata:

```typescript
interface RouteConfig {
  path: string;
  requiresAuth: boolean;
  roles?: string[];
  permissions?: string[];
}

const routes: RouteConfig[] = [
  { path: '/login', requiresAuth: false },
  { path: '/register', requiresAuth: false },
  { path: '/', requiresAuth: false },
  { path: '/analysis', requiresAuth: false },
  { path: '/screener', requiresAuth: false },
  { path: '/watchlist', requiresAuth: true },
  { path: '/moat', requiresAuth: true },
  { path: '/profile', requiresAuth: true },
  { path: '/admin', requiresAuth: true, roles: ['admin'] },
  // ...
];
```

L'AuthGuard legge questa configurazione. L'aggiunta di una nuova rotta protetta richiede solo un nuovo entry — nessuna logica custom nell'AuthGuard. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-074-mappa-rotte-dichiarativa/US-074.md §Business Rules]

### 3. Migrazione storage credenziali

**Questo è il cambiamento architetturale principale. Supersede ADR-010 §Alternative "Refresh in httpOnly cookie vs body JSON".**

| Token | Storage attuale (ADR-010) | Storage nuovo (ADR-024) |
|---|---|---|
| **Access token** | Zustand in-memory ✓ | Zustand in-memory ✓ (invariato) |
| **Refresh token** | Body JSON request/response | **Cookie `httpOnly Secure SameSite=Strict Path=/api/auth`** |

#### Backend — Modifiche AuthController

**`POST /api/auth/login`** (success):
- Response body: `{ accessToken: string, expiresInSeconds: number }` (refresh token NON nel body)
- Response header: `Set-Cookie: refresh_token={value}; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800`
- Cookie `Path=/api/auth` limita l'invio automatico ai soli endpoint auth.

**`POST /api/auth/refresh`**:
- Legge il refresh token dal cookie (non dal body).
- Response body: `{ accessToken: string, expiresInSeconds: number }`
- Response header: `Set-Cookie: refresh_token={new_value}; ...` (rotation, come da ADR-010 §3)

**`POST /api/auth/logout`**:
- Revoca refresh token in DB (`revoked_at = now()`).
- Response header: `Set-Cookie: refresh_token=; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=0` (cancella cookie)
- Response: `204 No Content`.

#### Vincoli di sicurezza

- Access token vita breve <= 15 minuti (invariato da ADR-006/010).
- Refresh token rotation ad ogni uso (invariato da ADR-010 §3).
- Revoca server-side in caso di riuso di refresh token già ruotato (invariato).
- **localStorage proibito** per token, session ID, dati personali o finanziari.
- **CSRF protection** abilitata per `/api/auth/refresh` e `/api/auth/logout` (vedi ADR-025 §2) — necessaria ora che il refresh token è in cookie. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/US-075.md §Business Rules]

#### Frontend — Rehydration

Al page reload (F5) su rotta protetta con utente autenticato:
1. Il Zustand store è vuoto (in-memory token perso).
2. Il componente di bootstrap tenta `POST /api/auth/refresh` (il browser allega automaticamente il cookie httpOnly).
3. Se refresh riuscito: access token ricevuto → store aggiornato → pagina renderizza. Nessun flicker verso `/login`.
4. Se refresh fallito (token scaduto/revocato): redirect a `/login` con messaggio "La sessione è scaduta". [^src: management/kanban/EP-017-protezione-rotte-sessione/US-075-migrazione-storage-credenziali/US-075.md §Acceptance Criteria]

### 4. Refresh automatico con coda richieste

Hook `useTokenRefresh`:

1. **Timer pre-expiry**: ~60s prima della scadenza dell'access token, trigger automatico di refresh.
2. **Mutex pattern**: durante il refresh, le richieste HTTP in corso vengono messe in coda (Promise pending). Un solo refresh alla volta.
3. **Rilascio coda**: a refresh completato, tutte le richieste in coda riprendono con il nuovo access token.
4. **Fallback**: se il refresh fallisce → logout flow (§6) → redirect a `/login`.
5. **Trasparente**: l'utente non percepisce il refresh (nessun loader, nessun redirect). [^src: management/kanban/EP-017-protezione-rotte-sessione/US-076-rinnovo-automatico-token/US-076.md §Business Rules]

### 5. Idle + absolute timeout

Componente `IdleTimeoutProvider`:

| Tipo | Default | Comportamento |
|---|---|---|
| **Idle timeout** | 15 minuti | Timer si resetta ad ogni interazione utente (mousemove, keydown, click, scroll). Al scadere: mostra prompt modale. |
| **Prompt timeout** | 60 secondi | Se l'utente non clicca "Estendi sessione" entro 60s: logout automatico. |
| **Absolute timeout** | 8 ore | Dopo N ore dall'inizio della sessione, logout indipendentemente dall'attività. |

Valori configurabili tramite env vars (`NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES`, `NEXT_PUBLIC_ABSOLUTE_TIMEOUT_HOURS`) senza rebuild. Il prompt è accessibile: annunciato da screen reader, navigabile da tastiera. [^src: management/kanban/EP-017-protezione-rotte-sessione/US-077-timeout-inattivita-assoluto/US-077.md §Business Rules]

### 6. Flusso logout completo

Hook `useLogout`, sequenza:

1. `POST /api/auth/logout` (revoca refresh token BE). Se fallisce: log errore, prosegui comunque.
2. Cookie refresh cancellato server-side (Max-Age=0 nella response).
3. Zustand store auth: clear (access token, user data).
4. SWR cache: `mutate(() => true, undefined, { revalidate: false })` (pulizia globale).
5. `window.history.replaceState(null, '', '/login')` + `router.push('/login')` (blocco back button).
6. Post-logout: back button non rientra in area protetta (history rewritten).

[^src: management/kanban/EP-017-protezione-rotte-sessione/US-078-flusso-logout-completo/US-078.md §Business Rules]

### 7. Pagina 403

Componente `app/403/page.tsx` dedicato: messaggio "Non hai i permessi per accedere a questa pagina", link a dashboard, nessuno stack trace o dettaglio tecnico.

## Componenti

| Componente | Layer | Path suggerito |
|---|---|---|
| `middleware.ts` (AuthGuard) | FE | root |
| `route-config.ts` | FE | `lib/auth/route-config.ts` |
| `useTokenRefresh` | FE | `hooks/use-token-refresh.ts` |
| `IdleTimeoutProvider` | FE | `components/auth/idle-timeout-provider.tsx` |
| `useLogout` | FE | `hooks/use-logout.ts` |
| `403/page.tsx` | FE | `app/403/page.tsx` |
| `AuthController` (aggiornato) | BE | `controller` — login/refresh/logout cookie-based |
| `SecurityConfig` (aggiornato) | BE | `config` — CSRF per /api/auth/* |

### Schema DB

Nessuna nuova tabella. `refresh_tokens` (ADR-006/010) resta invariata. La differenza è solo nel transport: il token value è ora nel cookie anziché nel body.

### API Changes

| Endpoint | Cambiamento |
|---|---|
| `POST /api/auth/login` | Response body senza `refreshToken`; refresh in `Set-Cookie` |
| `POST /api/auth/refresh` | Legge refresh da cookie; response body senza `refreshToken`; nuovo refresh in `Set-Cookie` |
| `POST /api/auth/logout` | Cancella cookie; `204 No Content` |

**OpenAPI**: aggiornare schema response di login e refresh (rimuovere `refreshToken` dal body).

## Motivazioni

1. **REQ-05 §5.2 è prescrittivo**: "access token in memoria, refresh token in cookie httpOnly Secure SameSite=Strict, localStorage proibito". La compliance non è opzionale.
2. **Riduzione superficie XSS**: un attacco XSS non può leggere il refresh token (httpOnly) né l'access token (solo in-memory, non persistito). Il rischio residuo è limitato all'uso dell'access token in-memory durante la finestra di 15min.
3. **`SameSite=Strict` + `Path=/api/auth`**: il cookie viene inviato solo su richieste same-origin verso `/api/auth/*`, minimizzando l'esposizione.
4. **Backward compatibility**: la migrazione non richiede nuove tabelle DB. Il refresh token è lo stesso valore, solo il transport cambia.
5. **Rehydration trasparente**: F5 su rotta protetta → refresh via cookie → nessun flicker. UX superiore al pattern body JSON (dove il refresh token si perde con il reload e l'utente deve riautenticarsi).

## Alternative considerate

| Alternativa | Esito |
|---|---|
| Mantenere refresh in body JSON (ADR-010 as-is) | Viola REQ-05 §5.2. Scartato. |
| BFF (Backend-for-Frontend) pattern | Introduce un layer proxy aggiuntivo; over-engineering per MVP con un solo client. Rivalutabile in R2 con mobile app. |
| Session cookie server-side (no JWT) | Incompatibile con architettura stateless JWT (ADR-006). Richiederebbe session affinity. Scartato. |
| Auth middleware server-side (Next.js) | Next.js middleware non ha accesso ai cookie httpOnly in App Router client-side. Il pattern ibrido (middleware per hint + client per verifica) è il trade-off scelto. |

## Conseguenze

- **ADR-010 §Alternative e §4 CSRF**: superseded da questo ADR per la parte refresh token transport e CSRF.
- **ADR-006**: non superseded — lo stack auth (Spring Security + JJWT + BCrypt) resta invariato.
- **US-073..078**: tutte implementabili con i componenti descritti.
- **EP-018 US-081**: session fixation protection si integra con il nuovo cookie (rigenerazione session ID = nuovo refresh token cookie post-login).
- **OpenAPI**: aggiornare `/api/auth/login` e `/api/auth/refresh` response schema.
- **Test E2E**: Playwright deve gestire cookie httpOnly nei test (non accessibili via JS, ma verificabili via response headers).
- **Gap `arch-refresh-token-storage-fe`** in `wiki/gaps.md`: risolvibile dopo accettazione di questo ADR.

## Pagine collegate

- [ADR-006](ADR-006-authentication.md) — auth foundation (invariato)
- [ADR-010](ADR-010-auth-consolidation.md) — auth consolidation (questo ADR supersede §refresh transport e §CSRF)
- [ADR-025](ADR-025-security-hardening-pci-dss.md) — CSRF protection per cookie endpoints (coordinamento)
- [ADR-021](ADR-021-structured-logging-pii-redaction.md) — security events logging (login/logout)
