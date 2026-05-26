---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [logging, observability, correlation-id, pii-redaction, fintech, backend]
---
# Logging Backend Strutturato e Human-Readable

> Il sistema di logging deve permettere a uno sviluppatore in triage di capire cosa e successo, dove, per chi e in quale richiesta, senza aprire altri sistemi.

## Contesto

REQ-01 del documento di iterazione fintech prescrive un sistema di logging che bilancia leggibilita umana e machine-readability per ingestion in sistemi di aggregazione. [^src: raw/requisiti-funzionali-fintech.md §REQ-01]

## Dettaglio

### Formato per ambiente

Il logging deve essere **strutturato in JSON** in produzione e **pretty-printed con colori** in sviluppo locale. [^src: raw/requisiti-funzionali-fintech.md §REQ-01] La configurazione del formato e del livello avviene tramite variabile d'ambiente senza necessita di redeploy.

### Campi obbligatori per ogni log entry

| Campo | Descrizione |
|---|---|
| `timestamp` | ISO 8601 con timezone |
| `level` | TRACE / DEBUG / INFO / WARN / ERROR / FATAL |
| `service` | Nome del servizio |
| `traceId` | ID di trace distribuito |
| `spanId` | ID dello span corrente |
| `userId` | Identificativo utente (se autenticato, non PII-sensibile) |
| `message` | Linguaggio naturale inglese, verbo all'inizio |
| `context` | Oggetto con dati rilevanti al contesto |

### Correlation ID

L'header HTTP `X-Correlation-Id` viene propagato lungo l'intera richiesta; se assente, il backend genera un UUID v4 all'ingresso. [^src: raw/requisiti-funzionali-fintech.md §REQ-01] I correlation ID devono essere presenti nel 100% dei log generati durante una richiesta HTTP.

Vedi [[correlation-id-tracing]] per il pattern di propagazione end-to-end.

### Redazione automatica PII

I campi sensibili vengono redatti automaticamente secondo la policy definita in REQ-05 §5.1. [^src: raw/requisiti-funzionali-fintech.md §REQ-01] Le regole minime includono:

- **PAN:** solo BIN (prime 6) + ultime 4 cifre; mai PAN completo.
- **CVV/CVC:** mai loggato in nessuna forma o livello.
- **IBAN:** paese (2 char) + ultime 4; in DEBUG ammessa visualizzazione estesa solo in non-prod.
- **JWT, API key, refresh token, segreti, password:** mai loggati.
- **IP address:** anonimizzazione ultimo ottetto IPv4 / ultimi 80 bit IPv6.

Vedi [[pii-redaction-checklist]] per l'implementazione operativa e [[fintech-security-compliance]] per la policy completa.

### Stack trace

Gli errori applicativi includono stack trace solo da livello ERROR in su. I path filesystem del server non vengono mai esposti nelle risposte client. [^src: raw/requisiti-funzionali-fintech.md §REQ-01]

### Performance

Il logging non deve aggiungere piu di **2ms p99** alla latenza della richiesta. Si prescrive l'uso di logger async/buffered. [^src: raw/requisiti-funzionali-fintech.md §REQ-01]

### Stile dei messaggi

I messaggi devono essere in **linguaggio naturale inglese con verbo all'inizio**: `"Failed to process payment intent"` invece di `"PaymentService.process error"`. [^src: raw/requisiti-funzionali-fintech.md §REQ-01]

## Acceptance criteria

- Un log di errore consente di capire cosa e successo, dove, per chi e in quale richiesta senza aprire altri sistemi.
- Correlation ID presenti nel 100% dei log durante una richiesta HTTP.
- Nessun campo sensibile compare in chiaro nei log (verificato tramite test automatici su pattern).

## Concetti correlati

[[correlation-id-tracing]]
[[fintech-security-compliance]]

## Pagine collegate

[[frontend-error-notifications]]
[[webapp-architecture-vi]]
[[pii-redaction-checklist]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-014 / [US-058](../../management/kanban/EP-014-logging-strutturato-observability/US-058-logging-strutturato-formato/US-058.md) — Logging strutturato con formato per ambiente
- EP-014 / [US-059](../../management/kanban/EP-014-logging-strutturato-observability/US-059-correlation-id-middleware/US-059.md) — Middleware Correlation ID end-to-end
- EP-014 / [US-060](../../management/kanban/EP-014-logging-strutturato-observability/US-060-redazione-pii-log/US-060.md) — Redazione automatica PII nei log
- EP-014 / [US-061](../../management/kanban/EP-014-logging-strutturato-observability/US-061-leak-detection-ci/US-061.md) — Test leak detection PII in CI
- EP-014 / [US-062](../../management/kanban/EP-014-logging-strutturato-observability/US-062-security-events-logging/US-062.md) — Logging eventi di sicurezza
