---
type: runbook
sources:
  - "design_&_architecture/decisions/ADR-007-api-contract.md"
status: review
created: 2026-05-21
updated: 2026-05-21
tags: [runbook, openapi, qa, ci, contract]
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
| Path runtime non in YAML | Aggiungere path a openapi.yaml o rimuovere controller spurio |
| Schema alias mismatch | `@Schema(name = "RuleEngineResult")` sul DTO |
| `openapi-typescript` parse error | Validare YAML (es. spazi in `instance: { type: string }`) |
| Testcontainers fallisce | Avviare Docker; verificare immagine `postgres:16-alpine` |

## Concetti correlati

[[openapi-contract-check]]
[[analysis-api-pipeline]]

## Pagine collegate

[[webapp-value-investing-spec]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare se sei wiki-keeper -->
