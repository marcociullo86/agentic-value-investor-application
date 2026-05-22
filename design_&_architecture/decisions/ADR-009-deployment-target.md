---
id: ADR-009
title: Deployment — Docker monorepo, profili Spring
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-009 — Deployment: Docker monorepo + profili Spring

## Contesto

Il `factory.config.yaml` dichiara `vcs.mode: monorepo` e `code_path: ./src/` interno al repo. La FSD non specifica un target deploy (cloud provider, on-prem, k8s, VM). Questo ADR fissa il baseline di build/run riproducibile, lasciando aperto il target hosting effettivo (segnalato come gap: `arch-deployment-target`).

## Decisione

### 1. Layout monorepo (`./src/`)

```
src/
 ├── backend/          # progetto Kotlin + Spring Boot (Gradle)
 │    ├── build.gradle.kts
 │    └── src/main/kotlin/com/valueinvesting/webapp/...
 ├── frontend/         # progetto Next.js (npm/pnpm)
 │    ├── package.json
 │    └── app/...      # App Router
 └── docker/
      ├── Dockerfile         # multi-stage: build FE + BE -> immagine finale
      ├── docker-compose.yml # dev locale: app + postgres + adminer
      └── .env.example
```

### 2. Build artifact

**Approccio multi-stage Dockerfile:**

```dockerfile
# Stage 1 - build frontend
FROM node:20-alpine AS fe-build
WORKDIR /fe
COPY src/frontend/ .
RUN npm ci && npm run build && npm run export

# Stage 2 - build backend
FROM gradle:8-jdk21 AS be-build
WORKDIR /be
COPY src/backend/ .
RUN gradle bootJar --no-daemon

# Stage 3 - runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=be-build /be/build/libs/*.jar app.jar
COPY --from=fe-build /fe/out /app/public      # static export Next.js
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Lo Spring Boot serve gli asset statici dalla directory `/app/public` come risorse. Single container, deploy semplificato.

### 3. Profili Spring (`spring.profiles.active`)

| Profile | Scopo |
|---|---|
| `dev` | Logback human-readable, CORS aperto a `localhost:3000`, hot reload tramite gradle continuous, Flyway baseline su DB vuoto |
| `test` | Testcontainers PostgreSQL, FMP adapter mockato (fixture in `src/test/resources/fmp-fixtures/`) |
| `prod` | Logback JSON, CORS allowed origins via env, Flyway repair OFF, JWT secret obbligatorio, Actuator endpoint non-health protetti |

### 4. Variabili d'ambiente principali

| Variabile | Scopo | Default |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | profilo attivo | `dev` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | connessione PostgreSQL | — |
| `FMP_API_KEY` | API key FMP | — (required) |
| `FMP_BASE_URL` | base url FMP override (vedi [ADR-004](ADR-004-fmp-integration.md)) | `https://financialmodelingprep.com/api/v3` |
| `JWT_SIGNING_SECRET` | secret JWT (≥ 256 bit) | — (required in prod) |
| `CORS_ALLOWED_ORIGINS` | CSV origini autorizzate | `http://localhost:3000` in dev |

### 5. Dev locale

`docker-compose up`: avvia container `app` + `postgres:16` + `adminer` (UI). Hot reload BE via gradle, FE via `next dev` (porta 3000, proxy a `/api/*` -> 8080).

### 6. VCS handoff

Mode `monorepo` (configurato in `factory.config.yaml`): commit unico cross-layer FE+BE+DB. Branch strategy `shared` (single branch `master`/`main`), `commit_coupling: float` (commit BE/FE possono divergere temporalmente).

Operazioni VCS distruttive (force-push, rebase su branch condivisi) richiedono gate umano via skill `vcs-handoff` (PATTERN.md §7 r.14).

## Motivazioni

1. **Container singolo** = deploy minimo per MVP (un VM o un servizio container in cloud generico).
2. **Static export FE** = no Node runtime in prod, riduce attack surface.
3. **Profili Spring** = configurazione esplicita per ambiente, no rebuild per cambio config.
4. **Testcontainers** = parita' dev/prod del DB (JSONB).

## Alternative considerate

- **Container separati FE + BE + reverse proxy nginx**: piu' flessibile ma complica deploy R1.0.
- **Kubernetes / Helm chart**: out of scope MVP; valutabile R2.
- **Vercel (FE) + Render/Fly.io (BE) split**: split cross-region; aumenta latenza intra-stack senza beneficio MVP.

## Conseguenze

- Build pipeline CI: Gradle task `gradle bootJar` + `npm run build && npm run export` + `docker build`.
- Gap aperto a contorno: `arch-deployment-target` (cloud provider, sizing, backup policy) -> da risolvere prima di R1.0 cutover.

## Appendice R1.1 — Target produzione (ADR-015)

Estensione accettata per cutover R1.1 (non supersede questo ADR):

- Topologia: VM + Docker Compose prod + nginx TLS — [ADR-015](ADR-015-deployment-target-r11.md).
- FE routing: static export mantenuto; analisi su `/analysis?ticker=` — [ADR-013](ADR-013-fe-analysis-routing-static-export.md).
- Runbook: [operations/deploy-runbook-r11.md](../operations/deploy-runbook-r11.md).

Gap `arch-deployment-target`: modello runtime chiuso; scelta vendor cloud resta operativa.

## Pagine collegate

- [overview.md](../overview.md)
- [ADR-015](ADR-015-deployment-target-r11.md)
- [operations/deploy-runbook-r11.md](../operations/deploy-runbook-r11.md)
- `factory.config.yaml` (root del repo)
