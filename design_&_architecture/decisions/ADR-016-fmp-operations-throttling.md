---
id: ADR-016
title: Operazioni FMP e throttling backend R1.1
status: accepted
created: 2026-05-22
deciders: [lead-architect, marco.ciullo]
---
# ADR-016 — Operazioni FMP e throttling backend

## Contesto

Tre gap wiki restano aperti per documentazione provider: `fmp-rate-limiting`, `fmp-endpoint-base-urls`, `fmp-error-codes` [^src: management/kanban/EP-009-throttling-fmp-runbook/US-029-documentazione-operativa-fmp/US-029.md §Business Rules] [^src: wiki/gaps.md §fmp-rate-limiting].

[ADR-004](ADR-004-fmp-integration.md) applica già Resilience4j con **rate limiter conservativo 30 req/min** e `fmp.base-url` parametrico. US-030 richiede allineamento numerico ai limiti **documentati** (non inventati) e tracciamento 429 [^src: management/kanban/EP-009-throttling-fmp-runbook/US-030-throttling-backend-fmp/US-030.md §Business Rules].

US-029 è principalmente **wiki/runbook** (wiki-keeper post-ingest raw); questo ADR fissa il **contratto L4** e la policy finché i gap restano aperti.

## Decisione

### 1. Documentazione operativa (US-029) — ripartizione responsabilità

| Gap | Azione L4 (questa run) | Azione L2 wiki (US-029) |
|---|---|---|
| `fmp-rate-limiting` | Policy numerica conservativa sotto (fino a chiusura gap) | wiki-keeper: pagina/runbook con valori **solo se** citabili da raw |
| `fmp-endpoint-base-urls` | Conferma default `https://financialmodelingprep.com/api/v3` + tabella path canonici MVP | Ingest raw ufficiale FMP → aggiornare `wiki/runbooks/fmp-api-quickstart.md` |
| `fmp-error-codes` | Tabella mapping HTTP → comportamento adapter (sotto) | Stesso runbook; niente codici inventati |

**Regola:** nessun numero di quota/minuto oggi promosso a "ufficiale" senza `[^src: raw/...]`. I gap restano **aperti** fino a ingest wiki-keeper.

### 2. URL base endpoint MVP (configurazione)

Property `fmp.base-url` default (invariato):

| Endpoint logico | Path relativo (v3) | US |
|---|---|---|
| Profile | `/profile/{ticker}` | US-001, US-013 |
| Search | `/search?query={q}` | US-001 |
| Income statement | `/income-statement/{ticker}?limit=10` | US-004+ |
| Balance sheet | `/balance-sheet-statement/{ticker}?limit=10` | US-004+ |
| Cash flow | `/cash-flow-statement/{ticker}?limit=10` | US-004+ |
| Key metrics | `/key-metrics/{ticker}?limit=10` | US-004+ |
| Stock screener | `/stock-screener?...` | US-002 |

Full URL = `{fmp.base-url}` + path (es. `https://financialmodelingprep.com/api/v3/profile/AAPL`).

### 3. Mapping errori HTTP provider → adapter

| HTTP FMP | Evento log (`fmp_api_event_log`) | Comportamento adapter |
|---|---|---|
| 401 / 403 | `FMP_AUTH_ERROR` | Fail fast, no retry; 503 verso client con ProblemDetail |
| 404 / body vuoto | `FMP_TICKER_NOT_FOUND` | 404 strutturato verso client |
| 429 | `FMP_429_RATE_LIMITED` | Retry [ADR-004](ADR-004-fmp-integration.md) + rispetto rate limiter uscente |
| 5xx | `FMP_5XX` | Retry + circuit breaker |
| Timeout / conn reset | `FMP_TIMEOUT` | Retry; poi fallback stale cache |

Formato body errore FMP: **non documentato nei raw** — adapter logga status + primi N byte body, mai apikey.

### 4. Throttling backend (US-030) — policy R1.1

Fino a chiusura `fmp-rate-limiting` in wiki:

| Parametro | Valore R1.1 | Env override |
|---|---|---|
| Rate limiter (Resilience4j) | **30 richieste / 60s** (globale verso FMP) | `FMP_RATE_LIMIT_PER_MINUTE` |
| Retry | max 3, backoff 500ms → 2s → 4s | invariato ADR-004 |
| Circuit breaker | invariato ADR-004 | — |
| Ordine catena | `RateLimiter → CircuitBreaker → Retry → HTTP` | `raw/tech_stack.md` §Backend |

**Calibrazione post-US-029:** quando il runbook wiki riporta limiti ufficiali con citazione raw, `be-dev` aggiorna solo env/config; eventuale revisione numerica via **nuovo ADR** che supersede appendice ADR-016 (mai edit in-place di ADR accepted oltre appendice).

### 5. Risposta 429 verso client UI

Il throttling **in uscita verso FMP** non espone 429 al browser per default. Degradazione utente: cache stale + header `X-Data-Stale: true` ([ADR-004](ADR-004-fmp-integration.md)). Endpoint diagnostici possono esporre metrica `fmp.request.count{status=429}`.

## Conseguenze

- US-029: task wiki-keeper + PM; L4 non inventa quote.
- US-030: task `be-dev` — verificare config Resilience4j + test WireMock 429 + env documentati in runbook deploy.
- [ADR-004](ADR-004-fmp-integration.md): appendice §8 (sotto).
- US-006 regressione: fallback stale invariato.

## Pagine collegate

- [ADR-004](ADR-004-fmp-integration.md)
- [ADR-008](ADR-008-observability-logging.md)
- [[fmp-api-quickstart]]
- [operations/deploy-runbook-r11.md](../operations/deploy-runbook-r11.md)
