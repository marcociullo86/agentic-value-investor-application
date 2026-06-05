---
type: runbook
sources:
  - "design_&_architecture/decisions/ADR-007-api-contract.md"
status: review
created: 2026-05-21
updated: 2026-05-27 (auth contract test + Testcontainers lifecycle)
tags: [runbook, openapi, qa, ci, contract, platform-domain]
domain: platform
---
# Runbook: Contract check OpenAPI

> Procedura per mantenere allineati contratto YAML, schema springdoc e tipi TypeScript generati.

## Prerequisiti

- JDK 17+, Docker (Testcontainers per test BE).
- Node 20+ per job frontend.
- Branch con modifiche a controller/DTO: aggiornare prima `design_&_architecture/api/openapi.yaml`.

## Step 1 — Aggiornare il contratto sorgente

1. Modificare `design_&_architecture/api/openapi.yaml` (path, schemas, responses).
2. Verificare `ProblemDetails` e header `X-Data-Snapshot-At` / `X-Data-Stale` dove richiesti.
3. Allineare `@Schema(name = "...")` sui DTO Kotlin se il nome pubblico differisce dalla classe.

## Step 2 — Backend contract test

```bash
cd src/backend
gradle contractCheck
```

- Test tag `@contract`: `OpenApiContractIT` carica YAML canonico e confronta con `GET /api/openapi.json`.
- Estendere `OpenApiContractSupport.IMPLEMENTED_OPERATIONS` quando si aggiunge un endpoint in produzione.

## Step 3 — Frontend types

```bash
cd src/frontend
npm install
npm run generate:api    # output: lib/api/generated/schema.ts (gitignored)
npm run typecheck:api
```

Il path al YAML usa virgolette per gestire `design_&_architecture/`.

## Step 4 — CI

Push su `feature/*` o PR verso `master`: workflow `contract-check` deve essere green.

## Troubleshooting

| Sintomo | Azione |
|---------|--------|
| Path runtime non in YAML | Aggiungere path a `openapi.yaml` o rimuovere controller spurio |
| Schema alias mismatch | `@Schema(name = "RuleEngineResult")` sul DTO response |
| `openapi-typescript` parse error | Validare YAML (es. spazi in `instance: { type: string }`) |
| Testcontainers fallisce | Avviare Docker; immagine tipica `pgvector/pgvector:pg17` (o `postgres:16-alpine` in test legacy) |
| `Mapped port can only be obtained after the container is started` | Non combinare `@TestInstance(PER_CLASS)` con `@SpringBootTest` + `@DynamicPropertySource` + `@Testcontainers`: Spring valuta le property prima che il container sia avviato. Usare lifecycle default (`PER_METHOD`) e `@BeforeEach` per setup che richiede MockMvc/DB. Vedi §Aggiornamenti (v2026-05-27). |
| `PatternParseException` all'avvio (Boot 3.5) | Rimuovere `springdoc-openapi-starter-webmvc-ui`; tenere solo `starter-webmvc-api` + `swagger-ui.enabled: false` |
| Contract test vede pochi path | Non usare `OpenAPIService.build()` — usare MockMvc `GET /api/openapi.json` come in `OpenApiContractIT` |
| `GET /api/openapi.json` → 404 | Verificare `springdoc.api-docs.enabled: true` e path `/api/openapi.json` in `application.yml` |
| Drift su path `/swagger-ui/*` | Attesi solo se si aggiunge lo starter UI; con API-only starter non dovrebbero comparire in produzione |

## Aggiornamenti (v2026-05-21)

**Stack verificato su `master`:** Spring Boot **3.5** + springdoc **2.8.16** + artifact `webmvc-api` only.

**Boot 3.5 + springdoc — note operative:**

1. **Dependency:** `implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.16")` — commento esplicito in `build.gradle.kts` sul clash PathPatternParser se si include lo starter UI. [^src: src/backend/build.gradle.kts §dependencies springdoc]
2. **Config:** `springdoc.swagger-ui.enabled: false`; documentazione dev su JSON raw `/api/openapi.json`. [^src: src/backend/src/main/resources/application.yml §springdoc]
3. **Test contract:** `@SpringBootTest` + `MockMvc` + Testcontainers PostgreSQL; profilo `test`; tag Gradle `@Tag("contract")` → task `contractCheck`. [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/contract/OpenApiContractIT.kt]
4. **Versione minima:** per Boot 3.5.x usare springdoc ≥ 2.8.9 (repo fissa 2.8.16). [^src: src/backend/build.gradle.kts §extra springdocVersion]

Workflow CI: `.github/workflows/contract-check.yml` — deve restare green prima del merge verso `master` (stato post TSK-037).

## Aggiornamenti (v2026-05-27)

**Testcontainers + Spring Boot — ordine extension (post EP-017 / fix CI `0050a11`):**

Su `@SpringBootTest` con container PostgreSQL in `companion object` e `@DynamicPropertySource` che legge `postgres::getJdbcUrl`:

| Pattern | Esito |
|---------|--------|
| Default `@TestInstance(PER_METHOD)` + `@BeforeEach` per caricare spec OpenAPI | OK — container avviato prima del contesto Spring per ogni test |
| `@TestInstance(PER_CLASS)` + `@BeforeAll` + `@DynamicPropertySource` | **Race** — `SpringExtension.beforeAll()` può invocare `@DynamicPropertySource` prima di `TestcontainersExtension`, errore *Mapped port can only be obtained after the container is started* |

**Fix applicato:** `AuthOpenApiSchemaContractTest` (TSK-210, ADR-024 §3) — rimosso `PER_CLASS`, `loadOpenApiSpec()` spostato da `@BeforeAll` a `@BeforeEach`. [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/api/AuthOpenApiSchemaContractTest.kt]

**Nota:** `RuleSignalEnumContractTest` usa ancora `PER_CLASS` senza `@DynamicPropertySource` né Testcontainers (legge YAML da system property) — pattern compatibile.

**Auth OpenAPI contract (tag `@contract`, stesso task Gradle `contractCheck`):**

`AuthOpenApiSchemaContractTest` verifica lo schema runtime springdoc per auth post-migrazione cookie: assenza `refreshToken` nel body di login/refresh 200, presenza `accessToken` + `expiresInSeconds`, header `Set-Cookie` documentato su login/refresh/logout, assenza schemi deprecati `TokenPairResponse` / `TokenPair`. [^src: src/backend/src/test/kotlin/com/valueinvesting/webapp/api/AuthOpenApiSchemaContractTest.kt]

## Concetti correlati

[[openapi-contract-check]]
[[analysis-api-pipeline]]

## Pagine collegate

[[webapp-value-investing-spec]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
