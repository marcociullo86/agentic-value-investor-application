---
type: tech_stack
generated: 2026-05-20
approved_by: marco.ciullo
approved_on: 2026-05-20
status: approved
source_proposal: raw/tech_stack.md.proposal
---
# Tech Stack — App Template Demo

> Stack tecnologico approvato (PATTERN §7 r.10 — priorità assoluta per i dev-agent).
> Origine: `tech-scout` skill 2026-05-20, promosso da `.proposal` con gate umano.
> Standards normativi (§11) adottati verbatim.

## Backend

**Stack scelto**: **Kotlin 2.2.x + Spring Boot 3.5.x (JVM 17+)**

- Build tool: **Gradle Kotlin DSL** (`build.gradle.kts`)
- Web: Spring MVC (sync) + WebClient per chiamate FMP esterne
- Security: **Spring Security + JJWT 0.12+** (JWT stateless, RFC 7519/7515)
- Persistence: **Spring Data JPA + Hibernate 6.x**
- Migration: **Flyway 10.x** + modulo `flyway-database-postgresql`
- Resilience: **Resilience4j 2.2.x** con `resilience4j-spring-boot3` (ordine: `Request → CircuitBreaker → Retry → HTTP call`)
- OpenAPI: **springdoc-openapi 2.x** (OpenAPI 3.1)
- Errori HTTP: **RFC 9457 Problem Details**
- Observability: **Micrometer + Spring Boot Actuator** (logback JSON in `prod`)

[^web: https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026] (accessed 2026-05-20)
[^web: https://docs.spring.io/spring-boot/reference/features/kotlin.html] (accessed 2026-05-20)
[^web: https://resilience4j.readme.io/docs/getting-started-3] (accessed 2026-05-20)

## Frontend

**Stack scelto**: **React 19 + Next.js 16.x (App Router + RSC stabile + Turbopack default)**

- Build: Next.js 16 (Turbopack)
- State management: **Zustand**
- Styling: **Tailwind CSS + Radix UI primitives**
- Charting: **Recharts**
- Forms: **React Hook Form + Zod**
- HTTP client: **fetch (Server) + SWR (Client)**

⚠️ Sicurezza: mantenere Next.js sempre patchato (CVE-2025-55184 DoS, CVE-2025-66478 RCE su RSC, Dec 2025).

[^web: https://nextjs.org/blog] (accessed 2026-05-20)
[^web: https://nextjs.org/docs/app/guides/upgrading/version-16] (accessed 2026-05-20)

## Database

**Stack scelto**: **PostgreSQL 17.x**

- Migration tool: **Flyway 10.x** (sola autorità schema)
- Config: `spring.jpa.hibernate.ddl-auto: validate`, `spring.flyway.clean-disabled: true` in `prod`
- Locale dev/test: **Docker Compose** (`postgres:17`)
- Test DB: **Testcontainers PostgreSQL**

[^web: https://thelinuxcode.com/spring-boot-postgresql-in-a-maven-project-2026-a-practical-production-ready-integration-guide/] (accessed 2026-05-20)
[^web: https://mvnrepository.com/artifact/org.flywaydb/flyway-database-postgresql] (accessed 2026-05-20)

## QA / Testing

**Stack scelto**:

- **Unit + Integration (BE)**: JUnit 5 + Spring Boot Test + REST Assured (contract)
- **Container-based integration**: **Testcontainers** (PostgreSQL + FMP mock WireMock)
- **E2E**: **Playwright** (multi-browser: Chromium, Firefox, WebKit)
- **Frontend unit**: **Vitest + React Testing Library**

[^web: https://java.testcontainers.org/test_framework_integration/junit_5/] (accessed 2026-05-20)
[^web: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html] (accessed 2026-05-20)
[^web: https://github.com/orange-buffalo/testcontainers-playwright] (accessed 2026-05-20)

## Infra / Deployment

**Stack scelto**:

- **Dev locale**: Docker + Docker Compose (`app`, `postgres:17`, `adminer`)
- **CI**: build con Gradle, test con Testcontainers, image Docker layered
- **Logging**: Logback (human-readable in `dev`, JSON in `prod`)
- **Metrics**: Micrometer + endpoint Actuator protetti in `prod`
- **Target deploy prod**: ⚠️ ancora aperto — gap `arch-deployment-target` in `wiki/gaps.md` (non blocca R1.0 dev locale).

[^web: https://oneuptime.com/blog/post/2026-02-01-spring-resilience4j-circuit-breaker/view] (accessed 2026-05-20)

## Standards verbatim (PATTERN §11)

Standards normativi/protocolli citati, **mai sostituiti**:

| Standard | Uso | Implementazione |
|---|---|---|
| **JWT** (RFC 7519/7515) | Auth stateless | JJWT 0.12+ |
| **OpenAPI 3.1** | API contract | springdoc-openapi 2.x |
| **RFC 9457** | Problem Details for HTTP APIs | ProblemDetail Spring 6 |
| **BCrypt** | Password hashing | `BCryptPasswordEncoder` Spring Security |
| **HTTP/2 + TLS 1.3** | Trasporto produzione | Reverse proxy / ingress |
| **JSON** (RFC 8259) | Payload API | Jackson 2.x |

## Follow-up (gap aperti per Arch)

I 3 ADR seguenti contengono versioni inferiori a quelle qui adottate. Vanno aggiornati formalmente (gap `arch-adr-version-sync` in `wiki/gaps.md`):

- **ADR-001** Frontend → React 18 (documenta) ↔ React 19 (stack)
- **ADR-002** Backend → Kotlin 1.9 (documenta) ↔ Kotlin 2.2 (stack); Spring Boot 3.x → 3.5.x specifico
- **ADR-003** Database → PostgreSQL 16 (documenta) ↔ PostgreSQL 17 (stack)

Le versioni in `raw/tech_stack.md` **prevalgono per i dev-agent** (PATTERN §7 r.10).
