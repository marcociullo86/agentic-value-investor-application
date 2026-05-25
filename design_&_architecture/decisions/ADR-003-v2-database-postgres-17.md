---
id: ADR-003-v2
title: Database version 2026 — PostgreSQL 17
status: accepted
created: 2026-05-25
accepted: 2026-05-25
deciders: [lead-architect, marco.ciullo]
supersedes: [ADR-003]
supersedes_scope: "Solo versione DBMS in 'Decisione' di ADR-003 (PostgreSQL 16 → 17). ADR-003 resta accepted come contesto storico R1.0 e per lo schema canonico, ORM, Flyway, motivazioni JSONB, alternative considerate."
pending_clarification: []
---
# ADR-003-v2 — Database version 2026 (PostgreSQL 17)

## Contesto

`raw/tech_stack.md` (approvato 2026-05-20) dichiara come database canonico **PostgreSQL 17.x** + Flyway 10.x + `flyway-database-postgresql` + Testcontainers PostgreSQL su image `postgres:17`. ADR-003 originale (2026-05-20) documenta PostgreSQL 16, coerente con il momento di stesura R1.0 ma successivamente superato.

Gap `arch-adr-version-sync` (`wiki/gaps.md`) richiede allineamento formale L4 → L5 senza edit-in-place. Questo ADR-v2 è l'**appendice non-distruttiva** che formalizza la versione 2026.

[^src: raw/tech_stack.md §Database, §Follow-up (gap aperti per Arch)]
[^src: wiki/gaps.md §arch-adr-version-sync]

## Decisione

Versione DBMS canonica 2026:

| Componente | Versione canonica 2026 | Note |
|---|---|---|
| **PostgreSQL** | **17.x** | Vincolo verbatim `raw/tech_stack.md` §Database |
| **Flyway** | **10.x** + `flyway-database-postgresql` | Sola autorità schema (`spring.jpa.hibernate.ddl-auto: validate`, `spring.flyway.clean-disabled: true` in `prod`) |
| **Dev/test image** | `postgres:17` (Docker Compose + Testcontainers) | Allineato CI + ADR-015 |
| **Connection pool** | HikariCP (default Spring Boot) | Conferma ADR-003 |

[^src: raw/tech_stack.md §Database]

Tutte le altre decisioni di ADR-003 (schema canonico tabelle, ORM Spring Data JPA + Hibernate 6, JSONB per cache FMP, TTL applicativo via `fetched_at`, alternative considerate, integrità referenziale per watchlist/Moat) **restano valide e immutate**.

## Conseguenze

- I dev-agent (db-dev + be-dev) implementano e mantengono schema/migrations su PostgreSQL 17 (PATTERN §7 r.10).
- ADR-003 originale rimane `accepted` come contesto storico R1.0. La sua sezione "Decisione" è superata limitatamente alla versione PostgreSQL da questo ADR-v2.
- `docker-compose` dev/prod usa image `postgres:17` (allineato [ADR-015](ADR-015-deployment-target-r11.md)).
- Testcontainers PostgreSQL container image puntata a `postgres:17` in `src/test/resources/testcontainers.properties` o configurazione equivalente.
- Le feature PostgreSQL 17 (es. `MERGE ... RETURNING`, JSON Table, miglioramenti VACUUM) sono **disponibili ma non vincolanti**: usarle solo se semplificano logica esistente, mai per refactor speculativo.
- Gap `arch-adr-version-sync` (sezione DB) si considera risolto a L4; chiusura formale a cura di `wiki-keeper`.
- Eventuali ADR futuri che modifichino versione DB devono superseding questo ADR-v2.

## Pagine collegate

- [ADR-003](ADR-003-database-postgresql.md) (contesto storico R1.0)
- [ADR-014](ADR-014-fmp-profile-snapshot-ttl.md)
- [ADR-015](ADR-015-deployment-target-r11.md)
- `raw/tech_stack.md` §Database
- `wiki/gaps.md` §arch-adr-version-sync
