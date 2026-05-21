---
type: concept
sources:
  - "design_&_architecture/api/openapi.yaml"
  - "design_&_architecture/decisions/ADR-007-api-contract.md"
status: review
created: 2026-05-21
updated: 2026-05-21 (post-contract-check)
tags: [qa, openapi, contract, springdoc, ci]
---
# Contract check OpenAPI (springdoc vs openapi.yaml)

> Verifica automatica che l'API esposta a runtime non diverga dal contratto sorgente `design_&_architecture/api/openapi.yaml` (ADR-007).

## Contesto

Il backend genera lo schema con **springdoc-openapi** su `GET /api/openapi.json`. Il file YAML in `design_&_architecture/api/` è la **source of truth** per il frontend (`npm run generate:api`) e per la review architetturale. [^src: design_&_architecture/decisions/ADR-007-api-contract.md]

TSK-037 introduce il gate `contract-check` in CI prima del merge verso `master`.

## Regole del gate

1. **Endpoint implementati** — ogni operazione in produzione (allowlist Sprint 2) deve esistere sia nel YAML sia nello schema runtime:
   - `GET /api/financials/{ticker}`
   - `GET /api/analysis/{ticker}`
   - `POST /api/dcf-overrides`
   - `DELETE /api/dcf-overrides/{ticker}`
2. **No drift in uscita** — nessun path `/api/*` nello schema runtime può mancare nel YAML (nuovo controller senza aggiornamento contratto → build fallisce). [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/contract/OpenApiContractValidator.kt §findUndeclaredRuntimePaths]
3. **Schema names** — alias ammessi: es. `RuleEngineResultResponse` → `RuleEngineResult` via `@Schema(name=...)`.
4. **Frontend** — `src/frontend`: `openapi-typescript` sul YAML + `tsc --noEmit` sui tipi generati.

## Esecuzione locale

```bash
cd src/backend && gradle contractCheck
cd src/frontend && npm run generate:api && npm run typecheck:api
# oppure
./scripts/contract-check.sh
```

Workflow GitHub: `.github/workflows/contract-check.yml` (job `be-contract` + `fe-contract`).

## Concetti correlati

[[analysis-api-pipeline]]
[[webapp-architecture-vi]]

## Pagine collegate

[[runbook-openapi-contract-check]]

## Aggiornamenti (v2026-05-21)

**springdoc su `master` (post TSK-037, CI `contract-check` green):**

| Elemento | Valore in repo |
|----------|----------------|
| Versione | **2.8.16** (`springdocVersion` in `src/backend/build.gradle.kts`; Boot 3.5.x richiede ≥ 2.8.9) [^src: src/backend/build.gradle.kts §extra springdocVersion] |
| Artifact | `springdoc-openapi-starter-webmvc-api` — **senza** `starter-webmvc-ui` (evita `PatternParseException` su `PathPatternParser` con Boot 3.5) [^src: src/backend/build.gradle.kts §dependencies springdoc] |
| Endpoint schema | `GET /api/openapi.json` (`springdoc.api-docs.path` in `application.yml`) [^src: src/backend/src/main/resources/application.yml §springdoc.api-docs] |
| Swagger UI | `springdoc.swagger-ui.enabled: false` — in dev usare viewer esterno (Swagger Editor, Stoplight, ecc.) contro `/api/openapi.json` [^src: src/backend/src/main/resources/application.yml §springdoc.swagger-ui] |

**Contract test — fonte runtime corretta:**

`OpenApiContractIT` carica lo schema con **MockMvc** `GET /api/openapi.json`, identico al documento servito in produzione. [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/contract/OpenApiContractIT.kt §loadRuntimeDocument]

**Anti-pattern:** non usare `org.springdoc.core.service.OpenAPIService.build()` nel test: restituisce solo il bean OpenAPI statico, **senza** i path dei controller Spring MVC. [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/contract/OpenApiContractIT.kt §class KDoc]

Allowlist operazioni Sprint 2 in `OpenApiContractSupport.IMPLEMENTED_OPERATIONS`; path infra ignorati (`/actuator/*`, residui swagger) via `RUNTIME_PATH_IGNORE`. [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/contract/OpenApiContractSupport.kt]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
