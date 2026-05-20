---
id: ADR-004
title: Integrazione FMP — Adapter pattern, cache 24h, retry+fallback
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-004 — Integrazione FMP: Adapter, cache 24h, resilienza

## Contesto

L'unica fonte dati esterna del sistema e' Financial Modeling Prep (FMP) — vedi [[fmp-api]] e [[fmp-api-overview]]. La FSD impone caching 24h, throttling, retry e fallback su cache [^src: wiki/concepts/webapp-architecture-vi.md §Requisiti Non Funzionali Rilevanti]. Tre gap noti aperti in `wiki/gaps.md`: `fmp-rate-limiting`, `fmp-endpoint-base-urls`, `fmp-error-codes` — l'implementazione applica valori conservativi.

## Decisione

### 1. Adapter pattern (`FmpAdapter`)

Modulo `com.valueinvesting.webapp.fmp` con interfaccia `FmpAdapter` e implementazione `FmpAdapterRestClient`. Espone metodi tipizzati: `searchSymbol(query)`, `getProfile(ticker)`, `getIncomeStatement(ticker, limit)`, `getBalanceSheet(...)`, `getCashFlow(...)`, `getKeyMetrics(...)`, `screen(criteria)`.

Tutte le data class di risposta sono **Kotlin nullable-aware** (`val effectiveTaxRate: Double?`) per rispettare la regola "campi mancanti = assenti, mai 0" (US-004 AC).

### 2. Cache layer 24h (`FmpCacheService`)

- **Storage**: tabella `fmp_financial_snapshot` (JSONB + `fetched_at TIMESTAMPTZ`) — vedi [ADR-003](ADR-003-database-postgresql.md).
- **Strategy**: cache-aside. Ogni chiamata FMP attraversa `FmpCacheService.getOrFetch(ticker, endpoint, fetchFn)`:
  1. Cerca snapshot con `(ticker, endpoint)` piu' recente.
  2. Se `now - fetched_at < 24h` -> ritorna snapshot.
  3. Altrimenti chiama FMP, salva nuovo snapshot, ritorna fresh.
- **Output sempre marcato col timestamp dati** (US-005 AC, US-006 AC "potenzialmente non aggiornati").

### 3. Resilienza (Resilience4j)

Configurazione applicata via annotation o programmatic builder al `FmpAdapter`:

| Pattern | Config | Razionale |
|---|---|---|
| **Retry** | max 3 tentativi, backoff esponenziale 500ms -> 2s -> 4s | US-006 AC "esegue almeno un retry" |
| **Circuit Breaker** | sliding window 20, failure rate 50% -> open, half-open dopo 60s | Evita martellare FMP in down |
| **Rate Limiter** | conservativo: 30 req/min (valore conservativo finche' `fmp-rate-limiting` resta gap aperto) | Protegge la quota |
| **Bulkhead** | semaphore 10 concorrenti | Limita uso connessioni |

### 4. Fallback su cache scaduta

Se Resilience4j esaurisce retry e circuit breaker e' aperto:

1. `FmpCacheService.getStale(ticker, endpoint)` cerca snapshot anche scaduti.
2. Se trovato -> ritorna **marcato come `stale: true` + `staleReason: "fmp-unavailable"`** (US-006 AC "potenzialmente non aggiornati").
3. Se nessuno snapshot -> errore strutturato `FmpUnavailableException` mappato a HTTP 503 dal `GlobalExceptionHandler`.

### 5. Osservabilita' FMP

Eventi tracciati in tabella `fmp_api_event_log` (e log strutturati [ADR-008](ADR-008-observability-logging.md)):

| Evento | Quando |
|---|---|
| `FMP_429_RATE_LIMITED` | Header 429 ricevuto |
| `FMP_5XX` | Errori server |
| `FMP_CIRCUIT_OPEN` | Circuit breaker apre |
| `FMP_FALLBACK_STALE` | Servito snapshot scaduto |
| `FMP_TICKER_NOT_FOUND` | Risposta vuota / 404 |

US-006 AC "eventi di rate limit risultano tracciati in canale di osservabilita'": soddisfatta.

### 6. API key

- Memorizzata in variabile ambiente `FMP_API_KEY`; mai committata.
- Iniettata via `@Value` nelle config Spring.
- Mascherata nei log (filter Logback).

### 7. Endpoint base URL

Configurabile via property `fmp.base-url` (default `https://financialmodelingprep.com/api/v3`). Il gap `fmp-endpoint-base-urls` resta aperto: la configurazione e' parametrica, quindi correggibile senza rebuild.

## Conseguenze

- US-004 (recupero dati): coperta dall'`FmpAdapter` + `FinancialDataService`.
- US-005 (cache 24h): coperta dal `FmpCacheService`.
- US-006 (resilienza): coperta da Resilience4j + fallback su stale snapshot + event log.
- Il Rule Engine (EP-003) e i moduli di valutazione (EP-004) consumano sempre i dati tramite `FmpCacheService`, mai chiamando direttamente FMP -> isolamento completo del provider esterno.

## Pagine collegate

- [[fmp-api]] / [[fmp-api-overview]] / [[fmp-api-quickstart]]
- [[webapp-architecture-vi]]
- [overview.md](../overview.md)
- [components/backend-components.md](../components/backend-components.md)
- [data/er-diagram.md](../data/er-diagram.md)
