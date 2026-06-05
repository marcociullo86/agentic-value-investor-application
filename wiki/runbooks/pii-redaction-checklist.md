---
type: runbook
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [pii, redaction, logging, gdpr, security, compliance, fintech, runbook, platform-domain]
domain: platform
---
# PII Redaction Checklist

> Procedura operativa per implementare e verificare la redazione automatica dei dati personali nei log, conforme a REQ-01 e REQ-05 §5.1 dell'iterazione fintech.

## Contesto

La redazione PII nei log e un requisito cross-cutting tra REQ-01 (logging backend) e REQ-05 §5.1 (data privacy nei log). [^src: raw/requisiti-funzionali-fintech.md §REQ-01] Questa checklist guida l'implementazione nel contesto del backend Spring Boot 3.5.

## Step 1 — Definire la lista centralizzata dei campi sensibili

Creare una configurazione centralizzata (non hardcoded nei singoli logger) con i pattern di redazione. [^src: raw/requisiti-funzionali-fintech.md §REQ-05] La configurazione deve essere aggiornabile senza redeploy (es. `application.yml` o config esterna).

Campi minimi da includere:

| Campo | Pattern di redazione | Note |
|---|---|---|
| PAN (numero carta) | `\b\d{6}\d{3,}\d{4}\b` | Mostrare solo BIN (prime 6) + ultime 4 |
| CVV/CVC | `\b\d{3,4}\b` nel contesto di pagamento | Mai loggato |
| IBAN | `\b[A-Z]{2}\d{2}[A-Z0-9]{4,}\b` | Mostrare paese + ultime 4 |
| Email | `\b[\w.+-]+@[\w-]+\.[\w.]+\b` | Solo dominio in INFO; estesa in DEBUG non-prod |
| JWT | `\beyJ[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]*\b` | Mai loggato |
| API key | Pattern specifico per provider (FMP, Anthropic) | Mai loggato |
| Password (anche hash) | Campi nominati `password`, `secret`, `credential` | Mai loggati |
| IP address | `\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b` | Anonimizzare ultimo ottetto |

## Step 2 — Implementare il PiiRedactionEncoder

Creare un custom Logback `Encoder` o `Layout` che applichi i pattern di redazione ricorsivamente a tutti i campi del log event, inclusi oggetti nested, array, e stringhe contenenti JSON serializzato. [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

Verificare che il `PiiRedactionEncoder` sia inserito nella pipeline di logging sia per il formato JSON (produzione) sia per il pretty-print (sviluppo).

## Step 3 — Verificare la ricorsivita

I pattern devono essere applicati ricorsivamente: un campo PAN embedded in un JSON serializzato dentro un messaggio di log deve comunque essere redatto. Scrivere test unitari specifici per:

- Campo PAN top-level
- PAN nested in oggetto `context`
- PAN in stringa JSON dentro il campo `message`

## Step 4 — Ambiente-aware per DEBUG

Implementare la logica condizionale per ambiente: [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

- In produzione: redazione rigorosa su tutti i livelli.
- In non-prod con livello DEBUG: ammessa visualizzazione estesa di IBAN ed email.
- CVV e PAN: mai visibili in nessun ambiente e livello.

## Step 5 — Test di leak detection in CI

Creare una batteria di test CI che: [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

1. Genera log durante l'esecuzione dei test (inclusi scenari di errore).
2. Scansiona l'output con pattern regex per PAN, JWT, IBAN, email, API key, password.
3. Fallisce il build al primo match.

Integrare il test nella pipeline CI esistente (`.github/workflows/ci.yml`).

## Step 6 — GDPR retention policy

Documentare e implementare la policy di retention: [^src: raw/requisiti-funzionali-fintech.md §REQ-05]

- 30 giorni per log operativi contenenti dati personali.
- 1 anno per security events (vedi [[fintech-security-compliance]] §5.6).
- Cancellazione automatica (es. logrotate, lifecycle policy cloud).
- Capacita di pseudonimizzazione per diritto all'oblio.

## Step 7 — Verifica performance

Verificare che il logging con redazione PII non aggiunga piu di 2ms p99 alla latenza della richiesta. [^src: raw/requisiti-funzionali-fintech.md §REQ-01] Benchmark con e senza `PiiRedactionEncoder` attivo.

## Step 8 — Audit manuale finale

Eseguire un audit manuale dei flussi principali (login, analisi ticker, deep analysis, top picks) verificando che nessun campo sensibile compaia in chiaro nei log.

## Concetti correlati

[[structured-logging-backend]]
[[fintech-security-compliance]]
[[correlation-id-tracing]]

## Pagine collegate

[[webapp-architecture-vi]]
[[fintech-hardening-requirements-map]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-014 / [US-060](../../management/kanban/EP-014-logging-strutturato-observability/US-060-redazione-pii-log/US-060.md) — Redazione automatica PII nei log
- EP-014 / [US-061](../../management/kanban/EP-014-logging-strutturato-observability/US-061-leak-detection-ci/US-061.md) — Test leak detection PII in CI
