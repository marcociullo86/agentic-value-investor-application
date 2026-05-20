---
id: ADR-002
title: Stack backend — Kotlin 1.9 + Spring Boot 3.x
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-002 — Stack backend: Kotlin 1.9 + Spring Boot 3.x

## Contesto

La FSD prescrive esplicitamente "Kotlin con Spring Framework (Spring Boot, Spring Data)" come stack backend [^src: wiki/concepts/webapp-architecture-vi.md §Livello 2: Backend (Server)]. Questo ADR formalizza la scelta con i moduli Spring concreti necessari ai requisiti delle 17 user story.

## Decisione

Backend implementato in **Kotlin 1.9** su **Spring Boot 3.2+** (Java 21 LTS come baseline JVM).

**Moduli Spring adottati:**

| Modulo | Uso |
|---|---|
| Spring Web (MVC) | Endpoint REST sincroni; alternativa reattiva non necessaria per il MVP |
| Spring Data JPA | Persistenza relazionale su PostgreSQL ([ADR-003](ADR-003-database-postgresql.md)) |
| Spring Security | Autenticazione utenti EP-006 ([ADR-006](ADR-006-authentication.md)) |
| Spring Validation | Validazione DTO request (annotations Jakarta) |
| Spring Cache + Caffeine | Caching applicativo opzionale per metadati FMP brevi (TTL minuti) |
| Spring Actuator | Health, metrics, info per observability ([ADR-008](ADR-008-observability-logging.md)) |
| `spring-boot-starter-webflux` (solo client) | `WebClient` non-blocking per chiamate FMP parallele |

**Librerie extra:**

- **Resilience4j**: retry + circuit breaker + rate limiter per integrazione FMP ([ADR-004](ADR-004-fmp-integration.md)).
- **springdoc-openapi**: generazione automatica OpenAPI 3.1 ([ADR-007](ADR-007-api-contract.md)).
- **Flyway**: migrations PostgreSQL ([ADR-003](ADR-003-database-postgresql.md)).
- **Jackson + jackson-module-kotlin**: serializzazione JSON con supporto Kotlin null-safety.
- **Mockito-Kotlin + AssertJ + Testcontainers**: testing (PostgreSQL integration via Testcontainers).

**Struttura a package** (proposta):

```
com.valueinvesting.webapp
 ├── api          # @RestController + DTO
 ├── service      # @Service use case
 ├── ruleengine   # Rule Engine (strategy pattern + DCF) - ADR-005
 ├── fmp          # FMP Adapter, cache, resilience - ADR-004
 ├── persistence  # @Entity, @Repository
 ├── security     # JWT, filters - ADR-006
 └── config       # @Configuration cross-cutting
```

## Motivazioni

1. **Vincolo verbatim FSD**: la specifica funzionale prescrive Kotlin + Spring Boot; nessuna deroga necessaria.
2. **Null safety nativa**: il type system Kotlin previene NPE su campi finanziari opzionali tipici di FMP (es. `effectiveTaxRate`, `dividendsPaid`) [^src: wiki/concepts/webapp-architecture-vi.md §Livello 2: Backend (Server)].
3. **Data classes per mapping JSON**: minimo boilerplate per DTO FMP (decine di campi per endpoint).
4. **Maturita' ecosistema**: Spring Boot 3 e' production-ready su Java 21 con virtual threads opzionali.

## Alternative considerate

- **Java 21 puro + Spring Boot**: comporta boilerplate Lombok / record verbosi su DTO FMP; perdiamo null safety di Kotlin.
- **Ktor + Exposed**: ecosistema piu' giovane, meno librerie enterprise (Spring Security maturo per JWT).
- **Quarkus + Kotlin**: ottimo per native image ma stack meno familiare al team Spring.

## Conseguenze

- Tutte le US backend (US-004, US-005, US-006, US-007, US-008, US-009, US-010, US-011, US-012, US-013, US-016 server-side, US-017) usano questo stack.
- Build: Gradle Kotlin DSL (`build.gradle.kts`).
- CI pipeline: build + test + Docker image (vedi [ADR-009](ADR-009-deployment-target.md)).

## Pagine collegate

- [[webapp-architecture-vi]]
- [[value-investing-rule-engine-runbook]]
- [overview.md](../overview.md)
- [components/backend-components.md](../components/backend-components.md)
