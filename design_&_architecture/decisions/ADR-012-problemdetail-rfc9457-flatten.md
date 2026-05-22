---
id: ADR-012
title: RFC 9457 Problem Details — extension fields al top-level
status: accepted
created: 2026-05-22
deciders: [lead-architect, marco.ciullo]
---
# ADR-012 — RFC 9457 Problem Details: flatten extension members

## Contesto

[ADR-007](ADR-007-api-contract.md) e `raw/tech_stack.md` prescrivono errori HTTP in formato **RFC 9457** con extension members come campi fratelli di `type`, `title`, `status`, `detail`, `instance` (§3.2) [^src: management/kanban/EP-007-hardening-produzione/US-021-errori-api-rfc9457/US-021.md §Business Rules].

In L5, Spring Framework 6.2.x (Spring Boot 3.5.x) serializza `org.springframework.http.ProblemDetail` annidando gli extension sotto la chiave `properties`, bypassando i customizer Jackson già tentati in Sprint 3 (quattro approcci senza effetto in CI — gap `be-problemdetail-flatten`) [^src: wiki/gaps.md §be-problemdetail-flatten].

I test di contratto e integrazione assertano oggi `$.properties.ticker`; US-021 richiede `$.ticker` al top-level.

## Decisione

### 1. Serializzazione dedicata (non Jackson mixin)

Registrare un **`HttpMessageConverter` custom** per `application/problem+json` con precedenza **superiore** al converter Spring default per `ProblemDetail`:

| Elemento | Scelta |
|---|---|
| Classe | `FlatteningProblemDetailHttpMessageConverter` (modulo `config`) |
| Registrazione | `WebMvcConfigurer.extendMessageConverters` — inserire in testa alla lista, oppure `configureMessageConverters` con ordine esplicito |
| Algoritmo | Costruire `Map<String, Any?>`: campi RFC (`type`, `title`, `status`, `detail`, `instance`) + `problemDetail.properties.forEach { (k,v) -> map[k]=v }` |
| Output | JSON via `ObjectMapper.writeValueAsBytes` (mapper applicativo standard, non il path interno Spring ProblemDetail) |
| `Content-Type` | `application/problem+json` |

**Non** si ripetono mixin `@JsonAnyGetter`, `@JsonComponent StdSerializer`, né `serializerByType` su `ProblemDetail` — il gap documenta che Spring usa un percorso di serializzazione che li ignora [^src: wiki/gaps.md §be-problemdetail-flatten].

### 2. Semantica extension invariata

I nomi e i tipi degli extension esistenti (`ticker`, `timestamp`, `reason`, …) restano invariati; cambia solo la posizione nel JSON [^src: management/kanban/EP-007-hardening-produzione/US-021-errori-api-rfc9457/US-021.md §Business Rules].

Esempio target (404 ticker):

```json
{
  "type": "https://api/errors/ticker-not-found",
  "title": "Ticker not found",
  "status": 404,
  "detail": "Ticker ZZZZ not found on FMP",
  "instance": "/api/search/ZZZZ",
  "ticker": "ZZZZ",
  "timestamp": "2026-05-22T10:00:00Z"
}
```

### 3. Retrocompatibilità

**Nessun periodo dual-write** lato server: un solo shape RFC-conforme. I client interni (FE + test) aggiornano i path JSON in un unico changeset con US-021.

### 4. Contratto OpenAPI

Aggiornare gli schema `ProblemDetail` / esempi errore in [api/openapi.yaml](../api/openapi.yaml): extension come proprietà aggiuntive dello schema (non oggetto `properties` annidato). [endpoints-overview.md](../api/endpoints-overview.md) resta allineato per riferimento.

## Alternative considerate

| Alternativa | Esito |
|---|---|
| Attendere fix Spring #25801 | Tempo indefinito; viola stack RFC 9457 |
| Dual-read client (`properties.ticker` \|\| `ticker`) | Maschera il debito; US-021 esclude transizione prolungata |
| Wrapper DTO custom al posto di `ProblemDetail` | Duplica API handler; converter è minimo e localizzato |

## Conseguenze

- US-021: implementabile da `be-dev` con aggiornamento test `AnalysisControllerIT`, `SearchControllerIT`, contract-check.
- Gap `be-problemdetail-flatten`: risolvibile in chiusura wiki post-merge.
- [ADR-007](ADR-007-api-contract.md): appendice §Error format (vedi sotto) — ADR immutabile; append-only.

## Appendice — Allineamento ADR-007 §Error format (2026-05-22)

Esempio canonico aggiornato (extension al top-level):

```json
{
  "type": "https://api/errors/ticker-not-found",
  "title": "Ticker not found",
  "status": 404,
  "detail": "Ticker ZZZZ not found on FMP",
  "instance": "/api/analysis/ZZZZ",
  "ticker": "ZZZZ"
}
```

Implementazione: [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md) (`FlatteningProblemDetailHttpMessageConverter`).

## Pagine collegate

- [ADR-007](ADR-007-api-contract.md)
- [api/endpoints-overview.md](../api/endpoints-overview.md)
- [overview.md](../overview.md)
