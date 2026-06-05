---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [correlation-id, tracing, observability, backend, frontend, fintech, platform-domain]
domain: platform
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

## Aggiornamenti (v2026-05-26)

### CorrelationIdFilter implementato (US-059, Sprint 11)

Il pattern implementativo descritto sopra è ora concretamente implementato come `CorrelationIdFilter` in `com.valueinvesting.webapp.api.filter`: [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/filter/CorrelationIdFilter.kt]

- **Tipo**: `OncePerRequestFilter` (Spring) con `@Order(HIGHEST_PRECEDENCE + 10)`, posizionato dopo `RequestIdFilter` e prima di `JwtAuthenticationFilter`.
- **Logica**: legge l'header `X-Correlation-Id` dalla request; se assente o blank, genera `UUID.randomUUID()`. Inserisce il valore in `MDC.put("correlationId")`, imposta l'header di risposta `X-Correlation-Id`, e rimuove dal MDC nel blocco `finally`.
- **Coesistenza**: il `correlationId` MDC coesiste con `requestId` (generato da `RequestIdFilter`) e con `traceId`/`spanId` (Micrometer distributed tracing). Tutti e tre sono inclusi nel JSON strutturato di produzione via `LogstashEncoder` (vedi [[structured-logging-backend]]).

### correlationId in ProblemDetail (US-059, TSK-173)

`ProblemDetailsMapper.build()` è stato esteso per includere `correlationId` come extension member RFC 9457 (pattern identico a `requestId` già presente): `MDC.get("correlationId")` → `setProperty("correlationId", value)`. Tutte le risposte ProblemDetail (400/401/403/404/409/422/500) transitano per questo mapper, quindi il campo è aggiunto uniformemente a ogni risposta di errore. [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/filter/CorrelationIdFilter.kt]

### Header propagato in risposta

L'header `X-Correlation-Id` viene impostato sulla response HTTP dal filter (`response.setHeader(HEADER, correlationId)`), rendendo il valore disponibile al client per le notifiche di errore (vedi [[frontend-error-notifications]]).

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-014 / [US-059](../../management/kanban/EP-014-logging-strutturato-observability/US-059-correlation-id-middleware/US-059.md) — Middleware Correlation ID end-to-end
- EP-015 / [US-064](../../management/kanban/EP-015-notifiche-errori-frontend/US-064-notification-service/US-064.md) — Servizio centralizzato notifiche errori (Correlation ID copiabile)
