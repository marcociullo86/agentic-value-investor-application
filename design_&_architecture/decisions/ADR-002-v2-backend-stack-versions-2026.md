---
id: ADR-002-v2
title: Backend stack versions 2026 — Kotlin 2.2 + Spring Boot 3.5 + JVM 21
status: accepted
created: 2026-05-25
accepted: 2026-05-25
deciders: [lead-architect, marco.ciullo]
supersedes: [ADR-002]
supersedes_scope: "Solo sezione 'Decisione' di ADR-002 limitatamente alle versioni di Kotlin, Spring Boot, JVM baseline. ADR-002 resta accepted come contesto storico R1.0 e per le motivazioni di vincolo FSD, null safety, data classes."
pending_clarification: []
---
# ADR-002-v2 — Backend stack versions 2026 (Kotlin 2.2 + Spring Boot 3.5 + JVM 21)

## Contesto

`raw/tech_stack.md` (approvato 2026-05-20) dichiara come stack backend canonico **Kotlin 2.2.x + Spring Boot 3.5.x (JVM 17+)** con Resilience4j 2.2.x, springdoc-openapi 2.x, Flyway 10.x, JJWT 0.12+, RFC 9457 Problem Details. ADR-002 originale (2026-05-20) documenta Kotlin 1.9 + Spring Boot 3.2+ (Java 21 LTS) coerenti con il momento di stesura R1.0 ma successivamente superate.

Gap `arch-adr-version-sync` (`wiki/gaps.md`) richiede allineamento formale L4 → L5 senza edit-in-place. Questo ADR-v2 è l'**appendice non-distruttiva** che formalizza le versioni 2026.

[^src: raw/tech_stack.md §Backend, §Follow-up (gap aperti per Arch)]
[^src: wiki/gaps.md §arch-adr-version-sync]

## Decisione

Lo stack BE è allineato alle versioni di `raw/tech_stack.md`:

| Componente | Versione canonica 2026 | Note |
|---|---|---|
| **Kotlin** | **2.2.x** | Vincolo verbatim `raw/tech_stack.md` §Backend |
| **Spring Boot** | **3.5.x** | LTS allineata aprile 2026 |
| **JVM baseline** | **JVM 21** (Temurin LTS) | `raw/tech_stack.md` consente JVM 17+; scelta operativa JVM 21 per coerenza con virtual threads e allineamento Spring Boot 3.5 |
| **Build tool** | Gradle Kotlin DSL (`build.gradle.kts`) | Conferma ADR-002 |
| **Web** | Spring MVC (sync) + WebClient per FMP | Conferma ADR-002 |
| **Security** | Spring Security + **JJWT 0.12+** (RFC 7519/7515) | Esplicitato da `raw/tech_stack.md` |
| **Persistence** | Spring Data JPA + Hibernate 6.x | Conferma ADR-002 |
| **Migration** | **Flyway 10.x** + `flyway-database-postgresql` | Esplicitato versione 10.x |
| **Resilience** | **Resilience4j 2.2.x** + `resilience4j-spring-boot3` | Ordine: `Request → CircuitBreaker → Retry → HTTP call` |
| **OpenAPI** | **springdoc-openapi 2.x** (OpenAPI 3.1) | Esplicitato versione 2.x |
| **Errori HTTP** | **RFC 9457 Problem Details** (ProblemDetail Spring 6) | Vincolo standard verbatim PATTERN §11 |
| **Observability** | Micrometer + Spring Boot Actuator (logback JSON in `prod`) | Conferma ADR-002 |

[^src: raw/tech_stack.md §Backend, §Standards verbatim]

Tutte le altre decisioni di ADR-002 (struttura a package, moduli Spring, motivazioni null safety / data classes / vincolo FSD) **restano valide e immutate**.

## Conseguenze

- I dev-agent (be-dev) implementano e mantengono il codice secondo queste versioni (PATTERN §7 r.10).
- ADR-002 originale rimane `accepted` come contesto storico R1.0. La sua sezione "Decisione" è superata limitatamente a versioni Kotlin/Spring Boot/JVM da questo ADR-v2.
- Coerenza CI/Dockerfile: usare image base Temurin 21 (JVM 21).
- Gap `arch-adr-version-sync` (sezione BE) si considera risolto a L4; chiusura formale a cura di `wiki-keeper`.
- Standards verbatim (JWT RFC 7519/7515, OpenAPI 3.1, RFC 9457, BCrypt, HTTP/2+TLS 1.3, JSON RFC 8259) sono **mai sostituibili** (PATTERN §11).
- Eventuali ADR futuri che modifichino versioni BE devono superseding questo ADR-v2.

## Pagine collegate

- [ADR-002](ADR-002-backend-stack.md) (contesto storico R1.0)
- [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md)
- [ADR-017](ADR-017-anthropic-sdk-jvm.md)
- `raw/tech_stack.md` §Backend, §Standards verbatim
- `wiki/gaps.md` §arch-adr-version-sync
