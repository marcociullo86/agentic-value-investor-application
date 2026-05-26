---
id: ADR-021
title: Structured Logging, PII Redaction & Security Events (EP-014)
status: accepted
created: 2026-05-26
accepted: 2026-05-26
deciders: [lead-architect, simone.olivieri]
supersedes_scope: ADR-008 §Logging (extends with PII redaction, security events, GDPR retention, formal Correlation ID)
---
# ADR-021 — Structured Logging, PII Redaction & Security Events

## Contesto

EP-014 (6 storie: US-058..063) richiede un sistema di logging strutturato con redazione automatica PII, Correlation ID end-to-end, logging eventi di sicurezza e retention GDPR. [^src: management/kanban/EP-014-logging-strutturato-observability/EP-014.md §Obiettivo]

ADR-008 ha stabilito il baseline observability (SLF4J + Logback, JSON prod, metriche Micrometer), ma non copre: redazione PII ricorsiva, formato campi obbligatori completo, security events strutturati, retention differenziata GDPR. [^src: management/kanban/EP-014-logging-strutturato-observability/US-058-logging-strutturato-formato/US-058.md §Business Rules]

Lo stack è Kotlin 2.2 + Spring Boot 3.5 + Logback (default Spring Boot). L'osservabilità usa già Micrometer + Actuator. [^src: management/kanban/EP-014-logging-strutturato-observability/US-059-correlation-id-middleware/US-059.md §Business Rules]

## Decisione

### 1. Formato log per ambiente

Configurazione in `logback-spring.xml` con profilo Spring:

| Ambiente | Formato | Encoder |
|---|---|---|
| `prod` | JSON strutturato | `logstash-logback-encoder` (net.logstash.logback:logstash-logback-encoder) wrappato da `PiiRedactionEncoder` |
| `dev` (default) | Pretty-print colorato | Logback `PatternLayoutEncoder` standard con colori ANSI |

Switchabile tramite variabile d'ambiente `LOG_FORMAT` (`json` | `pretty`) senza rebuild. Livello configurabile tramite `LOG_LEVEL` (default `INFO`). [^src: management/kanban/EP-014-logging-strutturato-observability/US-058-logging-strutturato-formato/US-058.md §Business Rules]

### 2. Campi obbligatori per ogni log entry

Ogni log entry JSON in produzione contiene:

| Campo | Sorgente |
|---|---|
| `timestamp` | ISO 8601 con timezone (`logstash-logback-encoder` default) |
| `level` | SLF4J level (TRACE/DEBUG/INFO/WARN/ERROR) |
| `service` | Property `spring.application.name` via MDC o encoder config |
| `traceId` | Micrometer Tracing MDC (se abilitato) o `correlationId` come fallback |
| `spanId` | Micrometer Tracing MDC (se abilitato) |
| `correlationId` | MDC `correlationId` iniettato da `CorrelationIdFilter` |
| `userId` | MDC `userId` iniettato da `JwtAuthenticationFilter` (se autenticato) |
| `message` | Linguaggio naturale inglese, verbo all'inizio |
| `context` | Campi strutturati aggiuntivi via `StructuredArgument` (logstash-logback-encoder) |

[^src: management/kanban/EP-014-logging-strutturato-observability/US-058-logging-strutturato-formato/US-058.md §Business Rules]

### 3. CorrelationIdFilter

Servlet filter Spring registrato con `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` (prima di `JwtAuthenticationFilter`):

1. Legge header `X-Correlation-Id` dalla request.
2. Se assente, genera UUID v4.
3. Inserisce in MDC `correlationId` (ereditato da tutti i log della richiesta).
4. Aggiunge header `X-Correlation-Id` alla response.
5. Pulisce MDC nel `finally` block.

Il Correlation ID viene anche incluso come extension member nelle risposte RFC 9457 ProblemDetail (coordinamento con ADR-012: aggiungere `correlationId` nelle risposte di errore del `GlobalExceptionHandler`). [^src: management/kanban/EP-014-logging-strutturato-observability/US-059-correlation-id-middleware/US-059.md §Business Rules]

### 4. PiiRedactionEncoder

Custom Logback `CompositeJsonEncoder` wrapper che intercetta l'output JSON e applica redazione regex prima della scrittura:

| Campo sensibile | Regola di redazione |
|---|---|
| PAN (numero carta) | BIN (prime 6) + `****` + ultime 4: `123456****1234` |
| CVV/CVC | Completamente rimosso (mai presente in nessuna forma) |
| IBAN | Paese (2 char) + `****` + ultime 4: `IT****5678`. In `dev` profilo + DEBUG: ammessa estesa |
| Email | In `prod` INFO+: solo dominio `***@example.com`. In `dev` DEBUG: ammessa completa |
| JWT, API key, refresh token, password | Sostituiti con `[REDACTED]` |
| IP address | Ultimo ottetto IPv4 → `0`: `192.168.1.0`. IPv6: ultimi 80 bit mascherati |

La lista dei pattern è esternalizzata in `application.yml` sotto `app.logging.pii.patterns` e hot-reloadable via Spring Cloud Config refresh o restart profilo (senza rebuild). I pattern sono applicati ricorsivamente: oggetti nested, array, stringhe contenenti JSON serializzato. [^src: management/kanban/EP-014-logging-strutturato-observability/US-060-redazione-pii-log/US-060.md §Business Rules]

### 5. Leak detection CI

Gradle task `piiLeakDetection` eseguito post-test nella pipeline CI:

1. I test di integrazione (`@SpringBootTest`) generano log in un file temporaneo (`test-output.log`).
2. Il task scansiona il file con regex per 6+ categorie: PAN (Luhn-valid 13-19 digit), CVV pattern, IBAN completo, JWT (`eyJ...`), API key pattern, password pattern.
3. Al primo match: build fallisce con report (pattern matchato + log line).
4. Con `PiiRedactionEncoder` attivo, zero match attesi.

[^src: management/kanban/EP-014-logging-strutturato-observability/US-061-leak-detection-ci/US-061.md §Business Rules]

### 6. SecurityEventLogger

Servizio Spring (`@Component`) con metodi tipizzati per ogni categoria di evento di sicurezza. Ogni metodo logga con livello appropriato (INFO per success, WARN/ERROR per failure), sempre con `userId` e contesto:

| Evento | Livello | Contesto |
|---|---|---|
| Login success | INFO | userId, ip (redatto), device fingerprint |
| Login failure | WARN | userId (se noto), motivo (credenziali errate, account bloccato, MFA fallita) |
| Password change / reset | INFO | userId |
| MFA enable / disable / fallback | INFO | userId, metodo |
| Permission / role grant o revoke | INFO | userId, role/permission, grantedBy |
| Accesso 403 | WARN | userId, risorsa richiesta, ruolo attuale |
| Operazione finanziaria rilevante | INFO | userId, tipo operazione, ticker |

Tutti gli eventi rispettano il formato strutturato (§2) e includono il Correlation ID (§3). I dati sensibili sono redatti (§4). [^src: management/kanban/EP-014-logging-strutturato-observability/US-062-security-events-logging/US-062.md §Business Rules]

### 7. GDPR retention policy

Configurazione Logback `TimeBasedRollingPolicy`:

| Tipo log | Retention | Appender |
|---|---|---|
| Operativi (tutti i log generali) | 30 giorni | `RollingFileAppender` con `maxHistory=30` |
| Security events (filtro marker `SECURITY_EVENT`) | 365 giorni | `SiftingAppender` dedicato con `maxHistory=365` |

Per il diritto all'oblio: script/procedura documentata per pseudonimizzare i log di un utente specifico (grep + sed su `userId`, oppure query su aggregatore log centralizzato). La retention policy è documentata in questo ADR come riferimento auditabile. [^src: management/kanban/EP-014-logging-strutturato-observability/US-063-gdpr-retention-policy/US-063.md §Business Rules]

### 8. Performance

Il logging non aggiunge più di 2ms p99 alla latenza della richiesta. Misure: [^src: management/kanban/EP-014-logging-strutturato-observability/US-058-logging-strutturato-formato/US-058.md §Acceptance Criteria]

- `AsyncAppender` wrapping di tutti gli appender (buffer 256, neverBlock=true).
- `PiiRedactionEncoder` opera su stringhe pre-serializzate (no re-parsing JSON).
- Benchmark obbligatorio: 1000 richieste sotto carico simulato, p99 logging overhead < 2ms.

### Configurazione

```yaml
app:
  logging:
    format: ${LOG_FORMAT:json}
    level: ${LOG_LEVEL:INFO}
    pii:
      enabled: true
      environment-aware: true  # prod=strict, dev=relaxed per IBAN/email in DEBUG
    retention:
      operational-days: 30
      security-events-days: 365
```

## Componenti

| Componente | Layer | Package/Path |
|---|---|---|
| `CorrelationIdFilter` | BE | `security/filter` |
| `PiiRedactionEncoder` | BE | `logging` |
| `PiiRedactionConfig` | BE | `config` |
| `SecurityEventLogger` | BE | `service` |
| `logback-spring.xml` | BE | `resources` |
| CI leak detection task | QA | `build.gradle.kts` |

## Motivazioni

1. **Estensione naturale di ADR-008**: Logback + MDC sono già il baseline. L'aggiunta di PII redaction come encoder custom è il pattern meno invasivo.
2. **`logstash-logback-encoder`** è lo standard de facto per JSON strutturato con Logback, supporta MDC, StructuredArguments, e nested fields nativamente.
3. **Redazione a livello encoder** (non a livello applicativo) garantisce copertura del 100% dei log senza richiedere discipline individuale nei singoli logger.
4. **Security events come marker Logback** consente routing separato (retention differenziata) senza duplicare il logger.
5. **Leak detection in CI** è un safety net automatico che previene regressioni PII.

## Alternative considerate

| Alternativa | Esito |
|---|---|
| Redazione a livello applicativo (sanitize prima di loggare) | Non garantisce copertura al 100%; richiede disciplina in ogni singolo `log.info()`. Scartato. |
| Log4j2 al posto di Logback | Richiederebbe esclusione del default Spring Boot + riconfigurazione. Beneficio non giustificato. |
| ELK/Loki per retention differenziata | Over-engineering per MVP; la retention file-based è sufficiente. Rivalutabile in R2 con aggregatore centralizzato. |
| Redazione PII tramite log aggregator (server-side) | I log transitano in chiaro fino all'aggregatore; rischio in caso di accesso non autorizzato al filesystem. La redazione at-source è più sicura. |

## Conseguenze

- **US-058..063**: tutte implementabili con i componenti descritti.
- **ADR-008**: esteso (non superseded) — le metriche Micrometer e gli health check restano invariati.
- **ADR-012**: le risposte ProblemDetail RFC 9457 includeranno `correlationId` come extension member.
- **EP-015** (frontend): il Correlation ID nell'header response permette al `NotificationService` FE di catturarlo e esporlo nelle notifiche.
- **Dipendenza**: `logstash-logback-encoder` da aggiungere in `build.gradle.kts`.

## Pagine collegate

- [ADR-008](ADR-008-observability-logging.md) — baseline observability (questo ADR estende)
- [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md) — ProblemDetail RFC 9457 (esteso con correlationId)
- [ADR-007](ADR-007-api-contract.md) — API contract
