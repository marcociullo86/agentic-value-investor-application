---
id: ADR-006
title: Authentication & user model — JWT stateless + Spring Security
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-006 — Authentication: JWT stateless + Spring Security + BCrypt

## Contesto

EP-006 (Watchlist personale, US-017) richiede identita' utente persistente: "La watchlist e' personale (associata all'identita' utente)" [^src: management/kanban/EP-006-watchlist-utente/US-017-gestione-watchlist/US-017.md §Business Rules]. La checklist Moat (US-016) richiede persistenza per `(user, ticker)`. La FSD non specifica protocollo o provider di autenticazione. Nessuno standard normativo (SAML/OIDC/SPID/eIDAS) e' citato nei raw: la regola §11 PATTERN.md non vincola la scelta verbatim.

## Decisione

Autenticazione **JWT stateless locale** con **Spring Security 6** e password hashate con **BCrypt** (cost 12).

### Architettura

| Componente | Ruolo |
|---|---|
| `AuthController` | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh` |
| `JwtService` | Issue + validate JWT (libreria `jjwt 0.12+`) |
| `UserDetailsServiceImpl` | Carica `User` da PostgreSQL (`users` table) |
| `JwtAuthenticationFilter` | Filter Spring che valida `Authorization: Bearer <token>` su endpoint protetti |
| `SecurityConfig` | Configurazione `SecurityFilterChain` |

### Schema utenti (vedi [data/er-diagram.md](../data/er-diagram.md))

```sql
users (
  id UUID PK,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(72) NOT NULL,    -- BCrypt
  display_name VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at TIMESTAMPTZ
)
```

### Token

- **Access token**: JWT firmato HS256, claim minimi (`sub=user.id`, `email`, `iat`, `exp`), TTL **15 minuti**.
- **Refresh token**: opaco (UUID v4), persistito in `refresh_tokens` con `expires_at` (TTL 30 giorni), revocabile lato server. Endpoint `/api/auth/refresh` scambia refresh -> nuovo access.
- **Secret**: variabile ambiente `JWT_SIGNING_SECRET` (min 256 bit). Mai committata.

### Endpoint policy

| Endpoint | Auth required |
|---|---|
| `POST /api/auth/register`, `/login`, `/refresh` | no |
| `GET /api/analysis/{ticker}`, `GET /api/screener`, `GET /api/search` | no (analisi pubblica) |
| `GET/POST/DELETE /api/watchlist/**` | si' (US-017) |
| `GET/POST /api/moat-checklist/**` | si' (US-016) |
| `POST /api/dcf-overrides` | si' (US-012 override utente) |

Analisi anonime restano disponibili: US-001..016 non richiedono login. EP-006 (US-017) e' l'unica feature gated.

## Motivazioni

1. **MVP-appropriate**: JWT locale evita dipendenza da provider esterno (Auth0, Cognito) in R1.0/R1.1.
2. **Stateless**: nessuna session affinity richiesta -> deploy semplificato (vedi [ADR-009](ADR-009-deployment-target.md)).
3. **Spring Security maturo**: filter chain ben documentata, BCrypt nativo.
4. **Refresh token revocabile**: mitiga rischio token leak (rotazione lato DB).
5. **Nessun vincolo normativo dai raw**: non e' richiesto OIDC/SAML/SPID/eIDAS — la regola §7 r.10 + §11 PATTERN.md non si applica.

## Alternative considerate

- **OIDC con provider esterno (Auth0, Keycloak)**: over-engineering per MVP; valutabile in R2 per SSO enterprise.
- **Session cookie + CSRF token**: incompatibile con architettura SPA Next.js + API separata (CORS + credentials complicati).
- **Magic link / passwordless**: superfluo per il caso d'uso; aumenta superficie email transactional.

## Conseguenze

- US-017 (watchlist personale): completamente sbloccata.
- US-016 (Moat checklist): persistenza `(user, ticker)` ora possibile.
- US-012 (DCF override): possibile salvare override per `(user, ticker)`.
- Frontend: store Zustand `useAuthStore` con `accessToken` in memoria + refresh token in `httpOnly` cookie (o localStorage con consapevolezza del rischio XSS — preferito httpOnly cookie).
- Aperto un gap a contorno: `arch-auth-provider-choice` (se in futuro si rendera' necessario SSO enterprise).

## Pagine collegate

- [[webapp-architecture-vi]]
- [overview.md](../overview.md)
- [api/openapi.yaml](../api/openapi.yaml)
- [data/er-diagram.md](../data/er-diagram.md)
