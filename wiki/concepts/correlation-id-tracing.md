---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [correlation-id, tracing, observability, backend, frontend, fintech]
---
# Correlation ID e Tracing End-to-End

> Un singolo identificatore propaga il contesto di una richiesta dal frontend al backend, rendendo possibile la correlazione di log, notifiche utente e ticket di supporto.

## Contesto

Il Correlation ID e un concetto trasversale a REQ-01 (logging) e REQ-02 (notifiche errori frontend) del documento di iterazione fintech. [^src: raw/requisiti-funzionali-fintech.md §REQ-01] Il frontend lo espone come valore copiabile nelle notifiche di errore per facilitare il supporto. [^src: raw/requisiti-funzionali-fintech.md §REQ-02]

## Dettaglio

### Meccanismo di propagazione

L'header HTTP `X-Correlation-Id` viene propagato lungo l'intera richiesta. Se il client non lo invia, il backend genera un UUID v4 all'ingresso della richiesta. [^src: raw/requisiti-funzionali-fintech.md §REQ-01]

### Backend: inclusione nei log

Ogni log entry generata durante una richiesta HTTP include il Correlation ID come campo strutturato. L'acceptance criterion richiede la presenza nel **100% dei log**. [^src: raw/requisiti-funzionali-fintech.md §REQ-01]

I campi `traceId` e `spanId` (distributed tracing) coesistono con il Correlation ID: il primo e applicativo (visibile all'utente finale), i secondi sono infrastrutturali (per tool di APM).

### Frontend: esposizione nelle notifiche

Quando una notifica di errore viene mostrata all'utente, include il Correlation ID come valore copiabile per facilitare la comunicazione con il team di supporto. [^src: raw/requisiti-funzionali-fintech.md §REQ-02] Questo collega l'esperienza utente alla traccia completa nei log backend.

### Pattern implementativo tipico

1. Middleware/Filter backend intercetta la richiesta HTTP.
2. Se header `X-Correlation-Id` presente, lo propaga; altrimenti genera UUID v4.
3. Il valore viene inserito nell'MDC (Mapped Diagnostic Context) del logger.
4. Tutti i log della richiesta ereditano il Correlation ID.
5. La response include l'header `X-Correlation-Id` per il client.
6. Il frontend lo cattura e lo rende disponibile al `NotificationService`.

## Concetti correlati

[[structured-logging-backend]]
[[frontend-error-notifications]]

## Pagine collegate

[[webapp-architecture-vi]]
[[analysis-api-pipeline]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-014 / [US-059](../../management/kanban/EP-014-logging-strutturato-observability/US-059-correlation-id-middleware/US-059.md) — Middleware Correlation ID end-to-end
- EP-015 / [US-064](../../management/kanban/EP-015-notifiche-errori-frontend/US-064-notification-service/US-064.md) — Servizio centralizzato notifiche errori (Correlation ID copiabile)
