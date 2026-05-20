---
id: ADR-008
title: Observability — Structured logging + Micrometer + Actuator
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-008 — Observability: Structured logging + Micrometer + Spring Actuator

## Contesto

La FSD richiede tracciabilita' degli eventi FMP (errori 429, rate limit) per analisi successiva [^src: management/kanban/EP-002-integrazione-fmp-data-provider/US-006-resilienza-fmp/US-006.md §Business Rules]. US-006 AC: "Eventi di rate limit del provider risultano tracciati in un canale di osservabilita' (log o equivalente)." Nessun standard normativo di observability (es. OpenTelemetry-mandatory) e' citato nei raw, ma e' best practice fissare un baseline.

## Decisione

### 1. Logging

- **Framework**: SLF4J + Logback (default Spring Boot).
- **Formato dev**: human-readable colorato.
- **Formato prod**: JSON strutturato via `logstash-logback-encoder` (eventi con `@timestamp`, `level`, `logger`, `thread`, `traceId`, `spanId`, message, payload campo `mdc.*`).
- **Correlation ID**: ogni request HTTP riceve un `X-Request-Id` (generato lato server se non presente nel header), iniettato in MDC tramite filter `RequestIdFilter`. Propagato come header in chiamate FMP.
- **Mascheramento**: filter Logback rimuove `apikey=` da URL/body. Email utenti loggate solo come hash o `user.id`.

### 2. Metriche (Micrometer)

Esposte via Spring Boot Actuator endpoint `/actuator/prometheus` (scraping Prometheus-compatibile):

| Metrica | Tipo | Scopo |
|---|---|---|
| `fmp.request.count{endpoint, status}` | counter | Volume chiamate FMP per endpoint e esito |
| `fmp.request.duration{endpoint}` | timer | Latenza FMP per endpoint |
| `fmp.cache.hit.count{endpoint}` / `fmp.cache.miss.count{endpoint}` | counter | Hit ratio cache 24h |
| `fmp.fallback.stale.count` | counter | Quante volte abbiamo servito snapshot scaduto |
| `ruleengine.evaluate.duration{ticker_class}` | timer | Performance Rule Engine |
| `dcf.method.used{method}` | counter | Distribuzione Greenwald vs FCF fallback |
| `http.server.requests` | timer (Spring) | Latenza endpoint REST |
| `jvm.*`, `process.*`, `hikaricp.*` | default Actuator | Salute baseline |

### 3. Tracing (out of MVP, optional)

OpenTelemetry tracing **opt-in** in R1.1: abilitabile via dipendenza `opentelemetry-spring-boot-starter` + variabile `OTEL_EXPORTER_OTLP_ENDPOINT`. Per il MVP basta `X-Request-Id` propagato.

### 4. Eventi di dominio in tabella (FMP)

US-006 AC e' soddisfatta da:

- **Log line strutturata** (`event=FMP_429_RATE_LIMITED ticker=AAPL endpoint=income-statement`).
- **+ Riga in tabella** `fmp_api_event_log` (ridondanza intenzionale: i log possono ruotare, la tabella conserva history breve per debugging) — vedi [ADR-004](ADR-004-fmp-integration.md) §5.

### 5. Health checks

- `/actuator/health` espone:
  - liveness probe (sempre 200 se app vive)
  - readiness probe (200 solo se DB raggiungibile)
  - custom `FmpHealthIndicator` (ping `/api/v3/profile/AAPL` cached, soft fail: degraded se FMP down ma cache disponibile)

### 6. Sensitive data

- Endpoint Actuator non-public (`/actuator/**` non in `permitAll`) tranne `/actuator/health`. Richiede auth o segregati a network privata (vedi [ADR-009](ADR-009-deployment-target.md)).

## Motivazioni

1. **Minimo viable observability**: log strutturati + metriche Prometheus coprono il 90% del debugging.
2. **Riuso ecosistema Spring**: zero costo aggiuntivo (Micrometer + Actuator inclusi).
3. **Compatibilita' futura con OTEL**: nessun lock-in.

## Conseguenze

- US-006 AC tracciabilita' rate limit: soddisfatta a doppia via (log + tabella).
- Dashboard ops futura (Grafana) consuma `/actuator/prometheus` senza modifiche al codice.
- Aperto un gap operativo a contorno (vedi [ADR-009](ADR-009-deployment-target.md)): definizione esatta del target di runtime/ops e' fuori scope MVP.

## Pagine collegate

- [overview.md](../overview.md)
- [components/backend-components.md](../components/backend-components.md)
- [[webapp-architecture-vi]]
