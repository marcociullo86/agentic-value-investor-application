# syntax=docker/dockerfile:1.7
# Multi-stage Dockerfile — FE static export + BE bootJar + Temurin JRE runtime.
# [^src: design_&_architecture/decisions/ADR-009-deployment-target.md §2 Build artifact]
# [^src: management/kanban/.../TSK-032.md §Scope tecnico]
# [^src: raw/tech_stack.md §Backend (Kotlin 2.2 + Spring Boot 3.5)]
#
# Build context expected: repository root (`.`)
#   docker build -f src/docker/Dockerfile -t vi-webapp .
#
# Stage layout (named, multi-arch friendly):
#   1. fe-build  — Next.js 16 static export -> /app/fe/out
#   2. be-build  — Gradle bootJar           -> /app/be/build/libs/*.jar
#   3. runtime   — Temurin 21 JRE alpine    -> app.jar + /app/public
#
# Note ADR vs runtime versions:
#   - tech_stack.md fixa JVM 17+ come baseline lingua. ADR-009 fixa runtime su
#     `eclipse-temurin:21-jre-alpine` per allineamento al canale LTS più recente
#     supportato dal Boot 3.5 (TSK-032 brief). Lo source-compatibility resta 17.
#   - Next.js >=13 ha deprecato `npm run export`: con `output: 'export'` in
#     next.config.js, `next build` genera direttamente `out/`.

############################
# Stage 1 — Frontend build
############################
FROM node:20-alpine AS fe-build
WORKDIR /app/fe

# Dependency layer (cached when lockfile unchanged)
COPY src/frontend/package.json src/frontend/package-lock.json* ./
# package-lock.json non è committato in TSK-030; usiamo `npm install` come fallback.
# `npm ci` viene preferito se il lockfile esiste (build deterministica).
# swr@2.4.1+ supports React 19 peers (TSK-053 / US-022).
RUN if [ -f package-lock.json ]; then npm ci --no-audit --no-fund; \
    else npm install --no-audit --no-fund; fi

# Sources
COPY src/frontend/ ./

# Client TS generato dall'OpenAPI canonico: lib/api/generated/schema.ts è gitignored
# (assente dal checkout pulito CI), quindi va rigenerato dentro l'immagine. Lo script
# `generate:api` legge ../../design_&_architecture/api/openapi.yaml rispetto a /app/fe
# → lo copiamo in /design_&_architecture/api/openapi.yaml. Senza questo step
# `next build` fallisce il type-check ("Cannot find module '@/lib/api/generated/schema'").
COPY design_&_architecture/api/openapi.yaml /design_&_architecture/api/openapi.yaml
RUN npm run generate:api

# `output: 'export'` (see next.config.js) -> emette /app/fe/out
RUN npm run build

############################
# Stage 2 — Backend build
############################
FROM gradle:8-jdk21-alpine AS be-build
WORKDIR /app/be

# Source copy (build.gradle.kts + settings + sources)
COPY src/backend/ ./

# bootJar — `--no-daemon` evita processi residui in container layer.
# Cache Gradle wrapper / dependencies via BuildKit (`--mount=type=cache`)
# se BuildKit attivo lato runner; fallback a build classico altrimenti.
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle bootJar --no-daemon

############################
# Stage 3 — Runtime
############################
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user (security hardening — see TSK-032 brief)
RUN addgroup -S app && adduser -S -G app -u 10001 appuser

WORKDIR /app

# Backend JAR
COPY --from=be-build /app/be/build/libs/*.jar /app/app.jar

# Frontend static export served by Spring at /app/public
# (configured in application.yml profile `prod` via spring.web.resources.static-locations)
COPY --from=fe-build /app/fe/out/ /app/public/

# Filesystem ownership: drop privileges
RUN chown -R appuser:app /app
USER appuser

EXPOSE 8080

# Healthcheck mirrors docker-compose: Actuator /health on 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
  CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
