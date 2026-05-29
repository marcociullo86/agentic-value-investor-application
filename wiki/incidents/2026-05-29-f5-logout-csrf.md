---
type: incident
status: approved
created: 2026-05-29
updated: 2026-05-29
tags: [auth, csrf, session, refresh, frontend, backend, bugfix, security]
---
# Logout su refresh (F5) — cookie CSRF mai emesso + header CSRF mai inviato — 2026-05-29

> Dopo il login, premere F5 sloggava l'utente. Causa: la protezione CSRF su `POST /api/auth/refresh` era cablata a metà — il backend non emetteva mai il cookie `XSRF-TOKEN` e il frontend non inviava mai l'header `X-CSRF-Token`, quindi il refresh silenzioso al boot falliva con 403. Risolto con un `CsrfCookieFilter` lato BE + configurazione XSRF di axios lato FE. Verificato end-to-end sullo stack Podman locale.

## Contesto

Architettura sessione (ADR-024 / ADR-006): access token **solo in memoria** (Zustand, mai persistito), refresh token in cookie `httpOnly`. Su F5 la memoria si perde, quindi `AuthProvider` (mount) chiama `rehydrate()` → `POST /api/auth/refresh`; il browser allega il cookie httpOnly e la sessione si ripristina senza re-login. [^src: src/frontend/components/providers/AuthProvider.tsx] [^src: src/frontend/lib/stores/useAuthStore.ts]

In EP-018 / US-080 (TSK-223) era stata aggiunta la protezione CSRF cookie-to-header su `POST /api/auth/refresh` e `/api/auth/logout`. [^src: management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/TSK-223.md] [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §3]

## Sintomi

- Login OK → F5 → redirect a `/login` (utente sloggato).
- Non visibile subito dopo il login perché `rehydrate()` fa short-circuit finché l'access token è in memoria; solo il refresh forzato dall'F5 (memoria persa) espone il problema. Il refresh pre-expiry periodico falliva in silenzio.

## Causa root

Protezione CSRF cablata a metà — entrambi i lati mancanti:

1. **Backend — cookie `XSRF-TOKEN` mai emesso.** Con `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler` (Spring Security 6) il token è *deferred*: il cookie viene scritto solo quando si accede a `CsrfToken.getToken()`. Su un'API JWT stateless nessuno lo legge → il cookie non veniva mai impostato nel browser. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/config/CsrfTokenConfig.kt]
2. **Frontend — header `X-CSRF-Token` mai inviato.** Il client axios non leggeva il cookie `XSRF-TOKEN` né impostava l'header; al più il default axios (`X-XSRF-TOKEN`) non combaciava col nome atteso dal BE (`X-CSRF-Token`). [^src: src/frontend/lib/api/client.ts]

Catena di fallimento su F5: `rehydrate()` → `POST /api/auth/refresh` senza header → **403** → l'interceptor in `client.ts` gestiva solo 401 → la promise veniva rigettata → `clearSession()` → guard `unauthenticated` → redirect a `/login`.

L'item *"client CSRF header su refresh/logout FE"* era già tracciato come pendente di "Wave 3" e non era mai stato completato. [^src: wiki/concepts/fintech-security-compliance.md §Aggiornamenti EP-018 Sprint 15 Wave 2]

## Fix

**Backend** — emissione del cookie:

- Nuovo `CsrfCookieFilter` (`OncePerRequestFilter`) che materializza il `CsrfToken` deferred così il cookie `XSRF-TOKEN` viene effettivamente scritto. Materializzazione **duale**: per metodi safe (GET/HEAD) *prima* dell'handler (la SPA `index.html` porta il `Set-Cookie` anche se la response è committata → cookie seminato al page load); per metodi state-changing *dopo* l'handler, così `XSRF-TOKEN` è appeso *dopo* eventuali cookie dell'endpoint (es. `refresh_token`), preservando l'ordine dei `Set-Cookie` su cui si appoggiano gli IT esistenti. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/security/filter/CsrfCookieFilter.kt]
- Registrato con `.addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)`. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/security/SecurityConfig.kt]

**Frontend** — invio dell'header:

- Su `axios.create()`: `xsrfCookieName: 'XSRF-TOKEN'`, `xsrfHeaderName: 'X-CSRF-Token'` (override del default per combaciare col BE) e `withXSRFToken: true` (forza l'invio anche cross-origin in `next dev`). axios legge il cookie non-httpOnly e lo riecheggia. Poiché il BE usa il `CsrfTokenRequestAttributeHandler` "plain", l'echo del valore grezzo del cookie combacia col token atteso. [^src: src/frontend/lib/api/client.ts]

**Test** — `MfaControllerIT`: la login MFA ora emette comunque `XSRF-TOKEN`, quindi l'asserzione passa da "`Set-Cookie` è null" a "nessun `refresh_token` tra i `Set-Cookie`" (intento reale: nessuna sessione emessa prima del secondo fattore). [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/api/MfaControllerIT.kt]

## Verifica (stack Podman locale, `vi-app:8080`)

Flusso reale del browser (SPA servita same-origin da Spring):

| Step | Risultato | Atteso |
|------|-----------|--------|
| `GET /` (page load) | `Set-Cookie: XSRF-TOKEN=…` | cookie seminato ✓ |
| `POST /api/auth/refresh` **senza** `X-CSRF-Token` | **403** | CSRF ancora enforced ✓ |
| `POST /api/auth/refresh` **con** `X-CSRF-Token` (= valore cookie) | **200** + nuovo `accessToken` | sessione ripristinata ✓ |

Esito: il refresh al boot/F5 va a buon fine invece di sloggare.

## Note operative

- In dev cross-origin (`next dev` su porta diversa dal BE) il cookie viene seminato dalla prima risposta GET del BE; `withXSRFToken: true` ne forza l'invio. In produzione/Podman la SPA è same-origin su `:8080` e il page load `GET /` semina il cookie prima di qualsiasi POST.
- I cookie auth sono `Secure` anche su `http://localhost` (profilo runtime): i browser accettano cookie `Secure` su `localhost` (contesto sicuro), quindi non incide sul flusso.

## Verifica raccomandata in CI/IDE

- BE IT: `CspCsrfSecurityIT`, `AuthControllerIT`, `MfaControllerIT`, `AuthStorageSecurityIT`.
- FE: `vitest run` su `lib/api` + `lib/stores`.
- E2E reale: `auth-csp-csrf.spec.ts` (il caso "refresh senza header → 403" resta verde; consigliato aggiungere il caso happy-path "con header → 200").

## Concetti correlati

- [[auth-guard-frontend]] — ciclo di vita token, rehydration F5, refresh con mutex
- [[fintech-security-compliance]] — §5.5 threat model CSRF, storage credenziali

## Pagine collegate

- [[webapp-architecture-vi]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare -->
- EP-018 / [US-080](../../management/kanban/EP-018-hardening-sicurezza-compliance/US-080-protezione-attacchi-web/US-080.md) — Protezione contro attacchi web (CSRF)
