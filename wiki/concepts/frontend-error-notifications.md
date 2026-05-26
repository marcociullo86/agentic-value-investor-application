---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [frontend, error-handling, accessibility, wcag, toast, notifications, fintech]
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

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-015 / [US-064](../../management/kanban/EP-015-notifiche-errori-frontend/US-064-notification-service/US-064.md) — Servizio centralizzato notifiche errori
- EP-015 / [US-065](../../management/kanban/EP-015-notifiche-errori-frontend/US-065-mappatura-errori-utente/US-065.md) — Mappatura codici errore a messaggi utente
- EP-015 / [US-066](../../management/kanban/EP-015-notifiche-errori-frontend/US-066-errori-rete-categorizzati/US-066.md) — Gestione categorizzata errori di rete
- EP-015 / [US-067](../../management/kanban/EP-015-notifiche-errori-frontend/US-067-validazione-form-accessibile/US-067.md) — Validazione form inline e summary accessibile
- EP-015 / [US-068](../../management/kanban/EP-015-notifiche-errori-frontend/US-068-accessibilita-notifiche-wcag/US-068.md) — Accessibilità notifiche WCAG 2.2 AA
