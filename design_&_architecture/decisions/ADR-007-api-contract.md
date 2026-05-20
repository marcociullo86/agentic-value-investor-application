---
id: ADR-007
title: API contract — REST + OpenAPI 3.1
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-007 — API contract: REST + OpenAPI 3.1

## Contesto

Il backend espone un'API consumata dal frontend SPA (vedi [ADR-001](ADR-001-frontend-stack.md)). La FSD cita "endpoint REST/GraphQL" senza imporre una scelta [^src: wiki/concepts/webapp-architecture-vi.md §Livello 2: Backend (Server)]. Serve un contratto stabile per consentire al FE Next.js di generare client tipizzato e per documentare l'integrazione lato API.

## Decisione

**REST/JSON** con **OpenAPI 3.1**, contratto sorgente in [api/openapi.yaml](../api/openapi.yaml), generato dal codice via **springdoc-openapi**.

### Convenzioni

- **Base path**: `/api`
- **Versioning**: per ora nessun `/v1/` esplicito (MVP); versionamento introdotto solo se breaking change in R2.
- **Naming**: kebab-case nei path, camelCase nei body JSON.
- **HTTP semantics**:
  - `GET` per query (idempotenti, cacheable)
  - `POST` per comandi non idempotenti (add-to-watchlist, save-moat-note)
  - `DELETE` per rimozioni
- **Status codes**:
  - 200/201/204 successo
  - 400 validazione
  - 401 autenticazione mancante
  - 403 autorizzazione negata
  - 404 risorsa non trovata (es. ticker, watchlist item)
  - 422 dato di dominio invalido (es. ticker sintatticamente valido ma non esistente su FMP)
  - 429 rate limit cliente
  - 503 FMP indisponibile + nessun fallback cache
- **Error format**: RFC 9457 Problem Details (`application/problem+json`):
  ```json
  { "type": "https://api/errors/ticker-not-found",
    "title": "Ticker not found", "status": 404,
    "detail": "Ticker ZZZZ not found on FMP",
    "instance": "/api/analysis/ZZZZ" }
  ```
- **Pagination**: cursor-based per liste lunghe (screener, search) con `?cursor=&limit=` (default 50).
- **Auth header**: `Authorization: Bearer <jwt>` (vedi [ADR-006](ADR-006-authentication.md)).
- **Content type**: `application/json; charset=utf-8`.
- **Tracciabilita' freschezza**: ogni risposta di analisi include header custom `X-Data-Snapshot-At: <ISO-8601>` e campo body `dataSnapshotAt` (US-005 AC, US-006 AC).
- **Stale data marker**: header `X-Data-Stale: true` + campo `isStale: true` quando il backend serve cache scaduta (US-006).

### Endpoint principali (mappa US -> path)

| US | Method | Path |
|---|---|---|
| US-001 | GET | `/api/search?query={q}` |
| US-001 + US-007..013 | GET | `/api/analysis/{ticker}` |
| US-002 | GET | `/api/screener?marketCap=...&sector=...&excludeHardToPredict=true` |
| US-003 | (consumo via US-001/002) | — |
| US-004 + US-005 + US-006 | GET | `/api/financials/{ticker}` (interno/diagnostica, raramente esposto a UI) |
| US-014 | (consumo via `/api/analysis/{ticker}`) | — |
| US-015 | GET | `/api/historical/{ticker}` (serie ricavi/utile netto) |
| US-016 | GET/POST | `/api/moat-checklist/{ticker}` |
| US-017 | GET | `/api/watchlist` |
| US-017 | POST | `/api/watchlist/items` body `{ticker}` |
| US-017 | DELETE | `/api/watchlist/items/{ticker}` |
| EP-006 auth | POST | `/api/auth/register`, `/login`, `/refresh` |
| US-012 override | POST | `/api/dcf-overrides` body `{ticker, method}` |

Dettaglio schemas + request/response: vedi [api/openapi.yaml](../api/openapi.yaml).

## Motivazioni

1. **Toolchain matura**: springdoc-openapi genera contratto da annotation Spring; `openapi-typescript` o `orval` lato FE generano client tipizzato.
2. **Cacheabilita' GET**: utile per `/api/analysis/{ticker}` (entita' che cambia raramente).
3. **HTTP semantics chiari per il dominio**: nessun pattern fortemente nested o sottoscrizione streaming richiesto -> REST e' adeguato.
4. **Curva di apprendimento**: il team valori-investing-aware non richiede skills GraphQL avanzate (resolver, dataloader, batching) per il MVP.

## Alternative considerate

- **GraphQL** (graphql-kotlin, Spring GraphQL): utile se il frontend richiedesse query molto variabili o nested deep; il nostro dominio (15 endpoint chiari) non lo giustifica. Riaprire se R2 introduce dashboard custom query-driven.
- **gRPC**: non adatto a SPA browser.
- **HATEOAS / Hypermedia**: over-engineering per SPA con client interno.

## Conseguenze

- OpenAPI come contratto sorgente of truth dal punto di vista del FE.
- Test contract-driven: lo schema OpenAPI guida sia integration test (Spring REST Docs opzionale) sia generazione client TS.
- Nessuna evoluzione breaking nel MVP senza bump esplicito.

## Pagine collegate

- [api/openapi.yaml](../api/openapi.yaml)
- [api/endpoints-overview.md](../api/endpoints-overview.md)
- [overview.md](../overview.md)
- [[webapp-architecture-vi]]
