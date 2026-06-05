---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26 (v2026-05-26 fix CI post-Sprint 12/13)
tags: [frontend, error-handling, accessibility, wcag, toast, notifications, fintech, platform-domain]
domain: platform
---
# Sistema di Notifiche Errori Frontend

> L'utente non vede mai stack trace o codici nudi; vede messaggi azionabili con correlation ID copiabile per facilitare il supporto.

## Contesto

REQ-02 del documento di iterazione fintech definisce un sistema centralizzato di notifiche per comunicare errori e stati problematici in modo chiaro, non bloccante quando possibile, e completamente accessibile WCAG 2.2 AA. [^src: raw/requisiti-funzionali-fintech.md §REQ-02]

## Dettaglio

### NotificationService

Un componente centralizzato `NotificationService` (toast/snackbar) espone un'API tipizzata con 4 livelli: `success`, `info`, `warning`, `error`. [^src: raw/requisiti-funzionali-fintech.md §REQ-02]

### Mappatura codici errore

I codici errore backend vengono mappati a **messaggi user-friendly localizzati**. L'utente non vede mai stack trace o codici HTTP nudi (es. `500`); vede messaggi azionabili come: *"Non siamo riusciti a completare il pagamento. Riprova tra qualche istante."* [^src: raw/requisiti-funzionali-fintech.md §REQ-02]

Quando disponibile, la notifica include un **correlation ID copiabile** per facilitare la comunicazione con il supporto. Vedi [[correlation-id-tracing]].

### Errori di rete

Il sistema distingue 4 categorie di errori di rete con messaggi e CTA dedicati:

| Categoria | CTA attesa |
|---|---|
| **Offline** | Messaggio stato offline |
| **Timeout** | Suggerimento retry |
| **Errore server** | Contatta supporto |
| **Errore di validazione** | Correzione campo specifico |

### Errori di form

Gli errori di validazione sono mostrati inline accanto al campo invalido + sintesi accessibile in cima al form al submit fallito. Ogni campo invalido collegato via `aria-describedby`. [^src: raw/requisiti-funzionali-fintech.md §REQ-02]

### Accessibilita WCAG 2.2 AA

| Requisito | Specifica |
|---|---|
| Role | `role="status"` per messaggi informativi, `role="alert"` per errori |
| Aria-live | `polite` o `assertive` coerente con il tipo |
| Contrasto | Testo >= 4.5:1; icone e bordi >= 3:1 |
| Auto-dismiss | Minimo 6s (>= 8s per testi lunghi); pausa al focus/hover |
| Azioni | Le notifiche con azioni non si chiudono automaticamente |
| Tastiera | Esc chiude la notifica piu recente |
| Informazione | Non veicolare informazione solo tramite colore (icona + colore + testo) |

[^src: raw/requisiti-funzionali-fintech.md §REQ-02]

## Acceptance criteria

- Audit axe-core / Lighthouse: zero violazioni critiche relative alle notifiche.
- Test con screen reader (NVDA/VoiceOver) confermano annuncio corretto.
- Nessun errore raw del backend raggiunge l'utente finale.

## Concetti correlati

[[correlation-id-tracing]]
[[material-design-3-accessibility]]

## Pagine collegate

[[structured-logging-backend]]
[[auth-guard-frontend]]
[[webapp-architecture-vi]]

## Aggiornamenti (v2026-05-26)

### EP-015 completata — Sprint 13 (11 TSK done, ADR-022 accettato)

L'epica EP-015 (Notifiche Errori Frontend) è stata implementata integralmente. ADR-022 ha formalizzato l'architettura: NotificationProvider React Context, error code mapping i18n, network error interceptor SWR-compatibile, FormErrorSummary con React Hook Form + Zod. [^src: design_&_architecture/decisions/ADR-022-frontend-error-notifications.md §Decisione]

### NotificationProvider + NotificationToast (US-064, TSK-194/195/196)

`NotificationProvider` React Context (`src/frontend/components/notifications/notification-provider.tsx`) gestisce la coda notifiche con API tipizzata `useNotification` hook. Il componente `NotificationToast` wrappa il Toast shadcn/ui (Radix) con garanzie WCAG 2.2 AA complete: [^src: design_&_architecture/decisions/ADR-022-frontend-error-notifications.md §2. NotificationToast]

- **4 livelli**: `success`, `info`, `warning`, `error` — ciascuno con icona distinta (CheckCircle2, Info, AlertTriangle, XCircle) tutte `aria-hidden="true"`.
- **Ruoli WCAG**: success/info → `role="status"` + `aria-live="polite"`; warning/error → `role="alert"` + `aria-live="assertive"`.
- **Auto-dismiss**: 6s default, 8s per testi > 80 caratteri, pausa al focus/hover; notifiche con azioni non si chiudono automaticamente.
- **Tastiera**: `Esc` chiude la notifica più recente.
- **Correlation ID copiabile**: badge click-to-copy quando presente.
- **Multi-canale**: informazione veicolata tramite icona + colore + testo (mai solo colore).

Copertura QA: 23 test (TSK-196) — 4 livelli, correlationId, anti-raw HTTP, axe-core, WCAG roles.

### Error code mapping + i18n (US-065, TSK-197/198)

Modulo `errorCodeMap` (`src/frontend/lib/errors/error-code-map.ts`) mappa 8 URI ProblemDetail RFC 9457 a messaggi user-friendly localizzati in `locales/it.json`, con fallback generico e CTA azionabili. [^src: design_&_architecture/decisions/ADR-022-frontend-error-notifications.md §3. Error code mapping]

| Backend `type` URI | Messaggio utente (it) | CTA |
|---|---|---|
| `*/validation-failed` | "Alcuni campi non sono validi…" | Correzione campo |
| `*/invalid-credentials` | "Email o password non corretti." | Riprova |
| `*/unauthorized` | "Accesso non autorizzato…" | Vai al login |
| `*/forbidden` | "Non hai i permessi…" | — |
| `*/not-found` | "La risorsa richiesta non è stata trovata." | — |
| `*/email-already-registered` | "Questa email è già registrata." | Vai al login |
| `*/server-error` (fallback 5xx) | "Si è verificato un problema…" | Riprova + Contatta supporto |
| `*/fmp-unavailable` | "Il servizio dati è temporaneamente non disponibile." | Riprova |
| (unmapped) | "Si è verificato un errore…" | Contatta supporto |

Copertura QA: 42 test (TSK-198).

### Network error interceptor (US-066, TSK-199/200)

`networkErrorInterceptor` (`src/frontend/lib/api/network-error-interceptor.ts`) categorizza 4 classi di errori di rete prima che raggiungano i componenti: offline (`!navigator.onLine`), timeout (`AbortError`), server 5xx (con Correlation ID da `X-Correlation-Id`), validazione 4xx. Il modulo espone `createFetcher` compatibile SWR. [^src: design_&_architecture/decisions/ADR-022-frontend-error-notifications.md §4. Network error interceptor]

Copertura QA: 20 test (TSK-200) — offline, timeout, server-500 con correlationId, validazione-422, createFetcher, NetworkError class.

### FormErrorSummary + migrazione form (US-067, TSK-201/202)

Componente `FormErrorSummary` (`src/frontend/components/forms/form-error-summary.tsx`) + `FormField` integrati con React Hook Form + Zod: errori inline via `aria-describedby`, summary accessibile in cima al form (`aria-live="assertive"` con focus programmato), link error → focus campo. 3 form migrati da `useState` a React Hook Form + Zod: login, registrazione, watchlist add-ticker. [^src: design_&_architecture/decisions/ADR-022-frontend-error-notifications.md §5. FormErrorSummary]

Copertura QA: 12 test (TSK-202) — inline error, aria-describedby, summary render, aria-live assertive, link→focus, axe-core.

### Hardening a11y notifiche (US-068, TSK-203/204)

Warning color light-mode corretto da `oklch(0.70)` a `oklch(0.62)` per raggiungere contrasto ≥ 3:1 contro surface-container. Aggiunto `data-auto-dismiss-duration` per testabilità. Contrasto verificato strumentalmente: testo 16.9:1 / 13.2:1, icone/bordi ≥ 3.1:1 entrambi i temi.

Copertura QA: 24 test (TSK-204) — axe-core audit 4 livelli, screen reader roles, contrasto token OKLCH, auto-dismiss timing, Esc dismiss, distinguibilità senza colore.

### Fix CI post-Sprint 12/13 — TypeScript strict + allineamento test E2E

Dopo il completamento di Sprint 12 e Sprint 13, 4 commit di fix CI hanno risolto errori emersi dal build Docker con TypeScript strict e dall'aggiornamento dei mock E2E:

**TypeScript strict (`error-code-map.ts` / `getErrorI18n`):**
- `resolveI18nEntry`: `split('.')` restituisce `string[]` e la chiave di accesso poteva risultare `undefined` — risolto con guard espliciti. [^src: src/frontend/lib/errors/error-code-map.ts]
- `getErrorI18n`: `errorCodeMap[type]` restituisce `string | undefined` — riscritto con nullish coalescing (`??`) per garantire tipo `string` in input a `resolveI18nEntry`. [^src: src/frontend/lib/errors/error-code-map.ts]

**Test bidirezionale i18n (`error-code-map.test.ts`):**
- TSK-199 ha aggiunto chiavi `offline` e `timeout` in `locales/it.json` come categorie di rete (non ProblemDetail `type` URI) — il test di copertura bidirezionale falliva aspettandosi un mapping corrispondente in `errorCodeMap`. Fix: filtro esplicito per escludere le chiavi di categoria network dalla verifica. [^src: src/frontend/lib/errors/__tests__/error-code-map.test.ts]

**E2E Playwright:**
- `auth-watchlist.spec.ts`: aggiunto fill del campo `confirmPassword` introdotto dalla migrazione form a React Hook Form + Zod (TSK-201). [^src: src/frontend/e2e/auth-watchlist.spec.ts]
- `accessibility-keyboard.spec.ts`: allineate mock routes agli endpoint reali (`/api/watchlist/items` POST, `/api/watchlist/items/*` DELETE) con shape `WatchlistItem` completa. [^src: src/frontend/e2e/accessibility-keyboard.spec.ts]

**Notification container (TypeScript strict):**
- `notification-container.tsx`: aggiunto non-null assertion per `mostRecent` dopo length check — errore strict solo in ambiente Docker. [^src: src/frontend/components/notifications/notification-container.tsx]

**Stato CI finale:** FE vitest pass, Playwright E2E (mocked + real BE) pass, BE gradle pass, contract-check pass.

### Run locale 2026-05-27 (post-CI #131)

Vitest locale allineato a CI (**434/434 pass**). Playwright mocked in locale **non confrontabile** con CI finché manca `npx playwright install` (30 fail infrastrutturali su `accessibility-*`, `deep-analysis`, `search-to-analysis`, `top-picks`). Report completo: [[2026-05-27-local-fe-test-run]].

[^src: wiki/incidents/2026-05-27-local-fe-test-run.md §Riepilogo]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-015 / [US-064](../../management/kanban/EP-015-notifiche-errori-frontend/US-064-notification-service/US-064.md) — Servizio centralizzato notifiche errori
- EP-015 / [US-065](../../management/kanban/EP-015-notifiche-errori-frontend/US-065-mappatura-errori-utente/US-065.md) — Mappatura codici errore a messaggi utente
- EP-015 / [US-066](../../management/kanban/EP-015-notifiche-errori-frontend/US-066-errori-rete-categorizzati/US-066.md) — Gestione categorizzata errori di rete
- EP-015 / [US-067](../../management/kanban/EP-015-notifiche-errori-frontend/US-067-validazione-form-accessibile/US-067.md) — Validazione form inline e summary accessibile
- EP-015 / [US-068](../../management/kanban/EP-015-notifiche-errori-frontend/US-068-accessibilita-notifiche-wcag/US-068.md) — Accessibilità notifiche WCAG 2.2 AA
