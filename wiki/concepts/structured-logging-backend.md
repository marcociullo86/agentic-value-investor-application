---
type: concept
sources: ["raw/requisiti-funzionali-fintech.md"]
status: draft
created: 2026-05-26
updated: 2026-05-26
tags: [logging, observability, correlation-id, pii-redaction, fintech, backend, gdpr, security-events, platform-domain]
domain: platform
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

## Aggiornamenti (v2026-05-26)

### EP-014 completata — Sprint 11 (tutti i 14 TSK done)

L'intera epica EP-014 (Logging Strutturato e Observability) è stata implementata e pushata su master. ADR-021 accettato come riferimento architetturale. Di seguito il dettaglio implementativo per US.

### logback-spring.xml unificato (US-058)

La configurazione di logging è unificata in un singolo `logback-spring.xml` con profili condizionali Janino (`<if condition>`): [^src: src/backend/src/main/resources/logback-spring.xml]

- **Profilo `prod`**: `LogstashEncoder` JSON strutturato (7+ campi obbligatori: timestamp, level, service, traceId, spanId, correlationId, userId, message) wrappato da `PiiRedactionEncoder(relaxedMode=false)`. Se env `LOG_FORMAT=pretty`, switch a `PatternLayoutEncoder` con colori ANSI.
- **Profilo `dev | default`**: `PatternLayoutEncoder` pretty-print con `[requestId]` e `[correlationId]` colorati. Se env `LOG_FORMAT=json`, switch a `LogstashEncoder` con `PiiRedactionEncoder(relaxedMode=true)`.
- **Profilo `test`**: WARN root, pretty-print console only.
- **AsyncAppender** su tutti i percorsi (`queueSize=256`, `neverBlock=true`) per rispettare il vincolo p99 < 2ms.
- Env var `LOG_LEVEL` e `LOG_FORMAT` configurabili senza redeploy (Janino conditional processing).
- Dipendenza `runtimeOnly("org.codehaus.janino:janino:3.1.12")` aggiunta a `build.gradle.kts`.

### PiiRedactionEncoder (US-060)

`PiiRedactionEncoder` è un `EncoderBase<ILoggingEvent>` che wrappa `LogstashEncoder` e applica redazione PII regex sul JSON serializzato prima della scrittura: [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/logging/PiiRedactionEncoder.kt]

| Categoria | Pattern | Redazione |
|---|---|---|
| PAN | `\b(\d{6})\d{3,9}(\d{4})\b` | BIN + `****` + last4 |
| JWT / API key / password / secret | `eyJ...` (triple-segment) + campo JSON `password`/`api_key`/`secret`/`refresh_token`/`authorization` | `[REDACTED]` |
| IPv4 | `\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.)\d{1,3}\b` | Ultimo ottetto → `0` |
| IBAN | `\b([A-Z]{2})\d{2}[A-Z0-9]{4,30}([A-Z0-9]{4})\b` | Paese + `****` + last4 (solo prod strict) |
| Email | `\b[\w.+-]+@([\w-]+\.[\w.-]+)\b` | `***@dominio` (solo prod strict) |

- **Environment-aware**: `relaxedMode=true` (dev/test) salta IBAN ed email a livello DEBUG/TRACE.
- **Ricorsivo**: rileva campi JSON-in-string (escaped JSON) e applica redazione anche all'interno.
- Configurazione esternalizzata in `application.yml` sotto `app.logging.pii` via `PiiRedactionConfig` `@ConfigurationProperties`.
- Gradle task `piiLeakDetection` (US-061, TSK-177) esegue scan post-test su `build/test-results` e `build/reports` per 6 categorie PII; fail su qualsiasi match.

### SecurityEventLogger (US-062)

`SecurityEventLogger` è un `@Component` con 10 metodi tipizzati per 7 categorie di eventi di sicurezza (ADR-021 §6): [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/service/SecurityEventLogger.kt]

| Metodo | Evento | Livello |
|---|---|---|
| `loginSuccess` | LOGIN_SUCCESS | INFO |
| `loginFailure` | LOGIN_FAILURE | WARN |
| `passwordChanged` | PASSWORD_CHANGED | INFO |
| `passwordResetRequested` | PASSWORD_RESET_REQUESTED | INFO |
| `mfaEnabled` | MFA_ENABLED | INFO |
| `mfaDisabled` | MFA_DISABLED | INFO |
| `mfaFallback` | MFA_FALLBACK | INFO |
| `permissionGranted` | PERMISSION_GRANTED | INFO |
| `permissionRevoked` | PERMISSION_REVOKED | INFO |
| `accessDenied` | ACCESS_DENIED | WARN |

Tutti gli eventi portano il marker `SECURITY_EVENT` (creato via `MarkerFactory.getMarker`) per il routing alla retention differenziata. Campi di contesto strutturati via `StructuredArguments.kv()` (top-level JSON keys in produzione). CorrelationId e userId da MDC (automatici). PII raw nei parametri — redazione delegata a `PiiRedactionEncoder`.

### GDPR retention differenziata (US-063)

logback-spring.xml include due `RollingFileAppender` attivi solo nel profilo `prod`: [^src: src/backend/src/main/resources/logback-spring.xml]

- **FILE_OPS** (`app.log`): `TimeBasedRollingPolicy`, `maxHistory` da `app.logging.retention.operational-days` (default **30 giorni**). Tutti i log operativi.
- **FILE_SECURITY** (`security.log`): `EvaluatorFilter` con `OnMarkerEvaluator` su `SECURITY_EVENT` (ACCEPT/DENY). `maxHistory` da `app.logging.retention.security-events-days` (default **365 giorni**). Solo eventi di sicurezza.
- Entrambi wrappati in `AsyncAppender` e con `PiiRedactionEncoder(relaxedMode=false)`.
- Override env var: `LOG_RETENTION_OPS_DAYS`, `LOG_RETENTION_SECURITY_DAYS`.

Script `pseudonymize-user-logs.sh` (TSK-182) per il diritto all'oblio GDPR: pseudonimizzazione deterministica `USER_DELETED_<sha256-12char>`, portabile GNU/BSD (macOS shasum fallback), gestisce 3 pattern userId (JSON quoted, numeric, pretty-print). [^src: src/backend/scripts/pseudonymize-user-logs.sh]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
- EP-014 / [US-058](../../management/kanban/EP-014-logging-strutturato-observability/US-058-logging-strutturato-formato/US-058.md) — Logging strutturato con formato per ambiente
- EP-014 / [US-059](../../management/kanban/EP-014-logging-strutturato-observability/US-059-correlation-id-middleware/US-059.md) — Middleware Correlation ID end-to-end
- EP-014 / [US-060](../../management/kanban/EP-014-logging-strutturato-observability/US-060-redazione-pii-log/US-060.md) — Redazione automatica PII nei log
- EP-014 / [US-061](../../management/kanban/EP-014-logging-strutturato-observability/US-061-leak-detection-ci/US-061.md) — Test leak detection PII in CI
- EP-014 / [US-062](../../management/kanban/EP-014-logging-strutturato-observability/US-062-security-events-logging/US-062.md) — Logging eventi di sicurezza
