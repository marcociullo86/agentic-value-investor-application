---
id: ADR-022
title: Frontend Error Notification System (EP-015)
status: proposed
created: 2026-05-26
deciders: [lead-architect]
---
# ADR-022 — Frontend Error Notification System

## Contesto

EP-015 (5 storie: US-064..068) richiede un sistema centralizzato di notifiche errori frontend che comunichi stati problematici in modo chiaro, accessibile WCAG 2.2 AA e azionabile, senza mai esporre stack trace o codici HTTP raw all'utente. [^src: management/kanban/EP-015-notifiche-errori-frontend/EP-015.md §Obiettivo]

L'architettura corrente non ha un `NotificationService` centralizzato. Gli errori API sono gestiti ad-hoc nei singoli componenti. Il backend espone errori in formato RFC 9457 ProblemDetail (ADR-007/ADR-012) con campo `type` come discriminante. Il Correlation ID sarà disponibile nella response header `X-Correlation-Id` (ADR-021 §3). [^src: management/kanban/EP-015-notifiche-errori-frontend/US-064-notification-service/US-064.md §Business Rules]

Lo stack frontend è React 19 + Next.js 16 + shadcn/ui (Radix + Tailwind) + SWR + Zustand + React Hook Form + Zod.

## Decisione

### 1. NotificationProvider + useNotification hook

Un React Context `NotificationProvider` gestisce la coda delle notifiche. L'hook `useNotification` espone un'API tipizzata:

```typescript
type NotificationLevel = 'success' | 'info' | 'warning' | 'error';

interface NotificationOptions {
  title: string;
  message: string;
  level: NotificationLevel;
  correlationId?: string;
  actions?: Array<{ label: string; onClick: () => void }>;
  autoDismiss?: boolean; // default: true per success/info, false per error con actions
  duration?: number;     // default: 6000ms (8000ms per messaggi lunghi)
}

const { notify } = useNotification();
notify.error({ title, message, correlationId, actions });
```

`NotificationProvider` viene montato nel root layout di Next.js. [^src: management/kanban/EP-015-notifiche-errori-frontend/US-064-notification-service/US-064.md §Business Rules]

### 2. NotificationToast (shadcn/ui wrapper)

Il componente visuale wrappa il `Toast` di shadcn/ui con le seguenti garanzie WCAG 2.2 AA:

| Requisito | Implementazione |
|---|---|
| Role semantico | `role="status"` + `aria-live="polite"` per success/info; `role="alert"` + `aria-live="assertive"` per warning/error |
| Contrasto | Testo >= 4.5:1; icone e bordi >= 3:1 (garantito dai token semantici EP-016) |
| Auto-dismiss | Minimo 6s (default), >= 8s per testi > 80 chars; pausa al focus/hover |
| Azioni | Notifiche con azioni non si chiudono automaticamente |
| Tastiera | `Esc` chiude la notifica più recente |
| Multi-canale | Informazione non veicolata solo tramite colore: icona + colore + testo per ogni livello |

[^src: management/kanban/EP-015-notifiche-errori-frontend/US-068-accessibilita-notifiche-wcag/US-068.md §Business Rules]

### 3. Error code mapping (i18n layer)

Un modulo `errorCodeMap` mappa i `type` URI delle risposte ProblemDetail RFC 9457 a messaggi user-friendly localizzati:

| Backend `type` URI | Messaggio utente (it) | CTA |
|---|---|---|
| `*/validation-failed` | "Alcuni campi non sono validi. Controlla i dati inseriti." | Correzione campo |
| `*/invalid-credentials` | "Email o password non corretti." | Riprova |
| `*/unauthorized` | "Accesso non autorizzato. Effettua il login." | Vai al login |
| `*/forbidden` | "Non hai i permessi per questa operazione." | — |
| `*/not-found` | "La risorsa richiesta non è stata trovata." | — |
| `*/email-already-registered` | "Questa email è già registrata." | Vai al login |
| `*/server-error` (fallback 5xx) | "Si è verificato un problema. Riprova tra qualche istante." | Riprova + Contatta supporto |
| `*/fmp-unavailable` | "Il servizio dati è temporaneamente non disponibile." | Riprova |
| (unmapped) | "Si è verificato un errore. Riprova o contatta il supporto." | Contatta supporto |

Tutte le stringhe sono esternalizzate in un file i18n (inizialmente `it.json`); il Correlation ID copiabile viene appendato nelle notifiche di errore quando disponibile. [^src: management/kanban/EP-015-notifiche-errori-frontend/US-065-mappatura-errori-utente/US-065.md §Business Rules]

### 4. Network error interceptor

Un interceptor SWR/fetch categorizza gli errori di rete prima che raggiungano i componenti:

| Categoria | Condizione | Livello | Messaggio | CTA |
|---|---|---|---|---|
| Offline | `!navigator.onLine` | error | "Sei offline. Verifica la connessione." | Verifica connessione |
| Timeout | `AbortError` / timeout config | warning | "La richiesta ha impiegato troppo tempo." | Riprova |
| Server error (5xx) | `response.status >= 500` | error | Messaggio da errorCodeMap + Correlation ID copiabile | Contatta supporto |
| Validation (4xx) | `response.status >= 400 && < 500` | info/warning | Messaggio specifico da errorCodeMap | Correzione campo |

L'interceptor è un wrapper attorno a `fetch` usato come fetcher di SWR e per le chiamate dirette. Cattura l'header `X-Correlation-Id` dalla response e lo propaga al `NotificationService`. [^src: management/kanban/EP-015-notifiche-errori-frontend/US-066-errori-rete-categorizzati/US-066.md §Business Rules]

### 5. FormErrorSummary

Componente per la validazione form inline + summary accessibile, integrato con React Hook Form + Zod:

- Errori inline: messaggio sotto il campo invalido, collegato via `aria-describedby`.
- Summary in cima al form: appare al submit fallito, annunciato da screen reader (`aria-live="assertive"`, focus programmato).
- I form esistenti (login, registrazione, watchlist) vengono aggiornati per usare il pattern.
- Messaggi localizzati via i18n. [^src: management/kanban/EP-015-notifiche-errori-frontend/US-067-validazione-form-accessibile/US-067.md §Business Rules]

## Componenti

| Componente | Layer | Path suggerito |
|---|---|---|
| `NotificationProvider` | FE | `components/notifications/notification-provider.tsx` |
| `useNotification` | FE | `hooks/use-notification.ts` |
| `NotificationToast` | FE | `components/notifications/notification-toast.tsx` |
| `errorCodeMap` | FE | `lib/errors/error-code-map.ts` |
| `networkErrorInterceptor` | FE | `lib/api/network-error-interceptor.ts` |
| `FormErrorSummary` | FE | `components/forms/form-error-summary.tsx` |
| File i18n | FE | `locales/it.json` (sezione `errors`) |

Nessun componente backend o DB per questa epica: è interamente frontend.

## Motivazioni

1. **shadcn/ui Toast** come base evita l'introduzione di una nuova libreria di notifiche; il wrapper aggiunge la semantica WCAG mancante.
2. **Error code mapping centralizzato** garantisce che nessun codice tecnico raw raggiunga l'utente, indipendentemente dal componente che effettua la chiamata API.
3. **i18n fin dall'inizio** prepara il terreno per la localizzazione multilingua futura (attualmente solo `it`).
4. **Correlation ID copiabile** riduce i tempi di triage: l'utente incolla l'ID nel ticket, il supporto filtra i log backend (ADR-021 §3).
5. **FormErrorSummary** riusa React Hook Form + Zod già nello stack, senza introdurre librerie aggiuntive.

## Alternative considerate

| Alternativa | Esito |
|---|---|
| react-hot-toast / react-toastify | Aggiungono dipendenza esterna; shadcn/ui Toast è già nel progetto e customizzabile. Scartato. |
| Notifiche modali per errori critici | US-064 richiede esplicitamente notifiche non bloccanti (toast/snackbar). Scartato. |
| Error boundary React come unico gestore | Copre solo errori di rendering, non errori API/rete. Complementare, non sostitutivo. |
| Error mapping server-side (backend restituisce messaggi user-friendly direttamente) | Accoppia backend e UX; viola separazione delle responsabilità. I messaggi utente sono responsabilità del frontend. |

## Conseguenze

- **US-064..068**: tutte implementabili con i componenti descritti.
- **Dipendenza EP-014**: il Correlation ID nelle response HTTP (ADR-021 §3) abilita la feature "Correlation ID copiabile" nelle notifiche.
- **Dipendenza EP-016**: i token semantici di colore (ADR-023) garantiscono il contrasto WCAG 2.2 AA nelle notifiche.
- **Nessuna nuova dipendenza npm**: tutto costruito su shadcn/ui Toast + React Hook Form + Zod + SWR già presenti.
- **File i18n**: introduce il pattern di localizzazione (`locales/it.json`) che potrà essere esteso a EN/altre lingue.

## Pagine collegate

- [ADR-021](ADR-021-structured-logging-pii-redaction.md) — Correlation ID (dipendenza)
- [ADR-023](ADR-023-design-token-system-shadcn.md) — Design token per contrasto WCAG (dipendenza)
- [ADR-007](ADR-007-api-contract.md) — API contract, error format RFC 9457
- [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md) — ProblemDetail extension members al top-level
