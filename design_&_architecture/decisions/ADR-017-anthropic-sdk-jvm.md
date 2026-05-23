---
id: ADR-017
title: Integrazione Anthropic Claude Opus 4.7 dal backend Kotlin/Spring — adapter pattern + Resilience4j
status: accepted
created: 2026-05-23
accepted: 2026-05-23
deciders: [lead-architect, marco.ciullo]
consulted: [tpm, be-dev]
---
# ADR-017 — Anthropic SDK JVM: adapter pattern + Resilience4j circuit breaker

## Contesto

Tre nuove epiche (EP-010 Graham defensive, EP-011 Deep Analysis 10-K/10-Q, EP-012 Top Value Picks) introducono per la prima volta nel backend Kotlin/Spring Boot chiamate al provider LLM Anthropic Claude Opus 4.7 (`claude-opus-4-7`). Lo stack consolidato è **Kotlin 2.2.x + Spring Boot 3.5.x + Resilience4j 2.2.x** [^src: raw/tech_stack.md §Backend] e il pattern di integrazione provider esterni è già fissato da [ADR-004](ADR-004-fmp-integration.md) §1 (adapter behind interface) + [ADR-016](ADR-016-fmp-operations-throttling.md) (throttling/circuit-breaker chain `RateLimiter → CircuitBreaker → Retry → HTTP`).

Le US che dipendono da Claude Opus 4.7:

| US | Dove serve LLM | Volume per ticker |
|---|---|---|
| US-041 (Munger inversion) | 10 query RAG su 10-K + 10-Q + 1 sintesi | max **12 chiamate** (10 + 1 + 1 fallback) [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/US-041.md §Business Rules] |
| US-042 (news sentiment) | 1 classificazione `TEMPORARY_PANIC / STRUCTURAL_DAMAGE / NEUTRAL` su news 90gg | 1 chiamata |
| US-047 (news scout screener) | 1 chiamata aggregata batch su top-200 candidati dell'universo | 1 chiamata batch [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-128.md §Cosa fare] |

Il prototipo Python `agent.py` usa `langchain_anthropic.ChatAnthropic(model=claude-opus-4-7, max_tokens=2000)` con la nota esplicita che **`temperature`/`top_p`/`top_k` sono rimossi dall'API Opus 4.7 (settarli causa 400 error) — il modello usa Adaptive Thinking internamente** [^src: raw/agent.py:1453-1458].

TSK-104 (`Configura AnthropicClient + ResilienceConfig per LLM circuit breaker`) richiede una decisione architetturale formale su come integrare l'API Anthropic dal backend Spring Boot. Il gap `tpm-anthropic-sdk-jvm-version` (2026-05-23) segnala incertezza sulla disponibilità di `com.anthropic:anthropic-java` su Maven Central al 2026-05-23 [^src: wiki/gaps.md §tpm-anthropic-sdk-jvm-version]. Questo ADR risolve il gap **per design**, indipendentemente dalla pubblicazione effettiva del SDK.

## Decision Drivers

1. **Resilienza alla disponibilità SDK** — il knowledge cutoff dell'agent è agosto 2025 e il SDK ufficiale Anthropic Java è in beta su GitHub (`anthropics/anthropic-sdk-java`). La verifica Maven Central non è eseguibile da questo agent (no WebFetch). La decisione deve produrre un design che funziona **sia con sia senza** SDK ufficiale [^src: wiki/gaps.md §tpm-anthropic-sdk-jvm-version].
2. **Coerenza con pattern esistente** — [ADR-004](ADR-004-fmp-integration.md) §1 fissa `interface FmpAdapter` + implementazione `FmpAdapterRestClient`; la stessa forma è già stata replicata in TSK-091/092/093 (`SecEdgarAdapter`). LLM provider = stesso pattern.
3. **Resilience4j chain obbligatoria** — l'ordine `RateLimiter → CircuitBreaker → Retry → HTTP` è normativo da `raw/tech_stack.md` §Backend e ribadito da [ADR-016](ADR-016-fmp-operations-throttling.md) §4. Le chiamate Claude Opus 4.7 devono usarlo per: (a) rispettare la rate-limit Anthropic (tier-based), (b) proteggere il budget LLM stimato $110-175/mese in R2 [^src: wiki/gaps.md §tpm-llm-cost-budget-r2], (c) failure isolation rispetto al resto del backend.
4. **Vincoli API Opus 4.7** — `temperature`/`top_p`/`top_k` rimossi (vedi nota agent.py). Il bean Spring deve esporli come **non configurabili** per evitare 400 error [^src: raw/agent.py:1453-1458].
5. **Cost-awareness obbligatorio** — US-041 prescrive cap 12 chiamate/analisi; TSK-128 prescrive una sola chiamata batch invece di 200 chiamate per il news scout. Il client deve tracciare token usage per il monitoring budget.
6. **Testabilità in isolamento** — `qa-dev` deve poter mockare il client LLM in integration test senza dipendere da rete o API key, esattamente come avviene oggi per FMP via WireMock + Testcontainers [^src: raw/tech_stack.md §QA / Testing].

## Considered Options

### Opzione A — SDK ufficiale `com.anthropic:anthropic-java` come dipendenza diretta

Usare direttamente le classi del SDK ufficiale (es. `AnthropicClient`, `MessageCreateParams`) all'interno di service Spring (`MungerInversionService`, `NewsSentimentClassifier`, `NewsScoutService`).

### Opzione B — HTTP client Spring diretto (`RestClient` 6.1+ o `WebClient`)

Implementare l'integrazione come chiamate HTTP verso `https://api.anthropic.com/v1/messages` usando il client Spring già a stack (`RestClient` per sync, `WebClient` per async), con header `x-api-key: ${ANTHROPIC_API_KEY}` + `anthropic-version: 2023-06-01` (spec OpenAPI pubblica Anthropic) [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-104.md §Cosa fare].

### Opzione C — Interface `AnthropicClient` + adapter behind interface (SDK ufficiale se disponibile, HTTP fallback altrimenti)

Definire un'interfaccia di dominio interna `com.valueinvesting.webapp.llm.AnthropicClient` con metodi tipizzati (`complete(prompt, system, maxTokens, model): LlmResponse`). Una sola implementazione concreta attiva alla volta (`AnthropicSdkClient` se la dipendenza SDK è risolvibile a build-time; `AnthropicRestClient` sempre disponibile come fallback). Tutti i caller (`MungerInversionService`, `NewsSentimentClassifier`, `NewsScoutService`) dipendono solo dall'interfaccia.

## Decision Outcome

**Scelta: Opzione C — Adapter behind interface `AnthropicClient` con default implementazione `AnthropicRestClient` (HTTP diretto), `AnthropicSdkClient` come implementazione opzionale attivabile via property + classpath check.**

### 1. Interfaccia di dominio `AnthropicClient`

Modulo `com.valueinvesting.webapp.llm` (nuovo, sibling di `com.valueinvesting.webapp.fmp` ex [ADR-004](ADR-004-fmp-integration.md) §1).

```
package com.valueinvesting.webapp.llm

interface AnthropicClient {
    fun complete(request: LlmRequest): LlmResponse
}

data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int,                  // 2000 default (agent.py:1458)
    val model: String = "claude-opus-4-7"
    // NOTA: temperature/top_p/top_k volutamente assenti — Opus 4.7 li rifiuta (400)
)

data class LlmResponse(
    val content: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val stopReason: String,              // "end_turn" | "max_tokens" | "stop_sequence"
    val model: String
)

sealed class LlmException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause) {
    class RateLimited(retryAfterSec: Int?, cause: Throwable?) : LlmException("Anthropic 429 rate limited", cause)
    class Overloaded(cause: Throwable?) : LlmException("Anthropic 529 overloaded", cause)
    class InvalidRequest(msg: String, cause: Throwable?) : LlmException("Anthropic 400 invalid request: $msg", cause)
    class AuthError(cause: Throwable?) : LlmException("Anthropic 401/403 auth error", cause)
    class ServerError(status: Int, cause: Throwable?) : LlmException("Anthropic 5xx server error ($status)", cause)
    class Timeout(cause: Throwable?) : LlmException("Anthropic call timeout", cause)
}
```

Tutti i caller (`MungerInversionService` US-041, `NewsSentimentClassifier` US-042, `NewsScoutService` US-047) dipendono **solo** da questa interfaccia.

### 2. Implementazione primaria — `AnthropicRestClient` (HTTP diretto)

Default, sempre attivo. Implementa `AnthropicClient` chiamando `POST https://api.anthropic.com/v1/messages` via Spring `RestClient` 6.1+ (sync, già a stack per il chiamante WebClient `FmpAdapterRestClient`):

| Aspetto | Valore |
|---|---|
| Endpoint | `https://api.anthropic.com/v1/messages` (configurabile `anthropic.base-url`) |
| Auth header | `x-api-key: ${ANTHROPIC_API_KEY}` (da env var, mai hardcoded) |
| API version header | `anthropic-version: 2023-06-01` (spec pubblica Anthropic Messages API) |
| Content-Type | `application/json` |
| Request body | `{ "model": <model>, "max_tokens": <n>, "system": <sys>, "messages": [{ "role": "user", "content": <user> }] }` |
| Response parsing | Top-level: `content[0].text` → `LlmResponse.content`; `usage.input_tokens` / `usage.output_tokens`; `stop_reason` |
| Mapping errori HTTP | Vedi §4 sotto (mapping → `LlmException` sealed hierarchy) |

**API key**: stessa policy ADR-004 §6 — env var `ANTHROPIC_API_KEY`, injection via `@Value`, mascheramento Logback filter (mai loggata).

### 3. Implementazione opzionale — `AnthropicSdkClient` (SDK ufficiale)

Attivabile se **entrambe** le condizioni:

1. Property `anthropic.client.impl=sdk` (default `rest`).
2. La dipendenza `com.anthropic:anthropic-java` è risolvibile a runtime (classpath check via `ClassUtils.isPresent("com.anthropic.client.AnthropicClient", ...)`).

Spring `@Conditional`:

```
@Configuration
class AnthropicConfig {
    @Bean
    @ConditionalOnProperty(name = ["anthropic.client.impl"], havingValue = "sdk")
    @ConditionalOnClass(name = ["com.anthropic.client.AnthropicClient"])
    fun anthropicSdkClient(...): AnthropicClient = AnthropicSdkClient(...)

    @Bean
    @ConditionalOnMissingBean(AnthropicClient::class)
    fun anthropicRestClient(...): AnthropicClient = AnthropicRestClient(...)
}
```

In assenza di SDK Maven Central pubblicato, `anthropic.client.impl=sdk` non attiva nulla e il fallback `AnthropicRestClient` resta attivo. **Nessun build break.** Quando il SDK sarà pubblicato (gap `tpm-anthropic-sdk-jvm-version` chiuso), basta aggiungere la dipendenza Gradle + flip della property: zero impatto sui caller.

### 4. Mapping errori HTTP Anthropic → `LlmException`

| HTTP status | `LlmException` subtype | Comportamento Resilience4j |
|---|---|---|
| 400 invalid_request | `InvalidRequest` | Fail-fast, no retry (errore di programmazione: prompt malformato, parametro `temperature` settato per Opus 4.7) |
| 401 / 403 | `AuthError` | Fail-fast, no retry, 503 verso client |
| 429 rate_limited | `RateLimited` | Retry (vedi §5) con backoff esponenziale; rispetto header `Retry-After` se presente |
| 529 overloaded | `Overloaded` | Retry con backoff lungo (10s → 20s → 40s) |
| 5xx server error | `ServerError` | Retry + circuit breaker |
| Connection reset / timeout | `Timeout` | Retry + circuit breaker |

### 5. ResilienceConfig LLM (`llm-claude`) — Resilience4j

Configurazione bean `LlmResilienceConfig` (parallelo al `FmpResilienceConfig` ex [ADR-016](ADR-016-fmp-operations-throttling.md) §4):

| Pattern | Config `llm-claude` | Razionale |
|---|---|---|
| **Rate Limiter** | `limitForPeriod = 12`, `limitRefreshPeriod = 1m`, `timeoutDuration = 5s` | US-041 cap esplicito 12 chiamate per analisi (10 query + 1 sintesi + 1 fallback) [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/TSK-104.md §Cosa fare] |
| **Circuit Breaker** | `failureRateThreshold = 50%`, `slidingWindowSize = 5`, `waitDurationInOpenState = 60s`, `permittedNumberInHalfOpenState = 2` | Stesso pattern FMP scalato per volumi più bassi (5 calls vs 20) |
| **Retry** | `maxAttempts = 3`, backoff esponenziale `2s → 4s → 8s`, retry **solo** su `RateLimited`, `Overloaded`, `ServerError`, `Timeout` | Mai retry su `AuthError` / `InvalidRequest` |
| **Bulkhead** | semaphore `maxConcurrentCalls = 4` | Evita esaurimento connessioni durante il batch notturno EP-012 (30 ticker top-picks × 12 chiamate) |
| **Ordine catena** | `RateLimiter → CircuitBreaker → Retry → HTTP` | Normativo `raw/tech_stack.md` §Backend |

Property override env:

| Property | Default | Env var |
|---|---|---|
| `anthropic.api-key` | — (obbligatorio, fail-fast all'avvio) | `ANTHROPIC_API_KEY` |
| `anthropic.base-url` | `https://api.anthropic.com/v1` | `ANTHROPIC_BASE_URL` |
| `anthropic.model` | `claude-opus-4-7` | `ANTHROPIC_MODEL` |
| `anthropic.timeout-seconds` | `60` | `ANTHROPIC_TIMEOUT_SECONDS` |
| `anthropic.client.impl` | `rest` | `ANTHROPIC_CLIENT_IMPL` (`rest` \| `sdk`) |
| `anthropic.rate-limit.per-minute` | `12` | `ANTHROPIC_RATE_LIMIT_PER_MINUTE` |
| `anthropic.rate-limit.batch-per-minute` | `60` | `ANTHROPIC_RATE_LIMIT_BATCH_PER_MINUTE` (EP-012 job notturno) |

### 6. Observability — eventi LLM tracciati

Analogamente a `fmp_api_event_log` ex [ADR-004](ADR-004-fmp-integration.md) §5 e [ADR-008](ADR-008-observability-logging.md), nuova tabella `llm_api_event_log`:

| Evento | Quando | Campi |
|---|---|---|
| `LLM_REQUEST` | Ogni chiamata Anthropic | `ticker`, `purpose` (`munger` \| `news_sentiment` \| `news_scout`), `model`, `input_tokens`, `output_tokens`, `latency_ms`, `cost_estimate_usd` |
| `LLM_429_RATE_LIMITED` | HTTP 429 | `retry_after_sec`, `attempt` |
| `LLM_529_OVERLOADED` | HTTP 529 | `attempt` |
| `LLM_CIRCUIT_OPEN` | Circuit breaker apre | `failure_rate`, `window_size` |
| `LLM_AUTH_ERROR` | 401/403 | mascherato — mai api key |
| `LLM_INVALID_REQUEST` | 400 | `error_type` dal payload Anthropic |

Metriche Micrometer (per dashboard cost budget gap `tpm-llm-cost-budget-r2`):

- `llm.request.count{purpose, status}`
- `llm.tokens.input.total{purpose}` / `llm.tokens.output.total{purpose}`
- `llm.cost.usd.estimate{purpose}` (input_tokens × $0.015/1k + output_tokens × $0.075/1k stima Opus 4.7)
- `llm.latency.seconds{purpose}` (histogram)

### 7. Configurazione `claude-opus-4-7` — vincoli API

- **Parametri di sampling vietati**: `temperature`, `top_p`, `top_k` **non devono mai essere inviati** nel request body. Opus 4.7 li rifiuta con HTTP 400. Il modello applica Adaptive Thinking internamente [^src: raw/agent.py:1453-1458].
- `max_tokens`: default `2000` per Munger inversion (US-041) [^src: raw/agent.py:1458]; `1500` per news sentiment (US-042) [^src: raw/agent.py:1572]; configurabile per chiamata via `LlmRequest.maxTokens`.
- API version header: `anthropic-version: 2023-06-01` — stabile, non upgrade automatico senza ADR successivo (breaking changes possibili in versioni future).

## Consequences

### Positive

- **US-041 / US-042 / US-047 sbloccate** senza dipendere dalla pubblicazione del SDK ufficiale. I caller vedono solo `AnthropicClient`, indipendentemente dall'implementazione.
- **Coerenza con [ADR-004](ADR-004-fmp-integration.md)**: stesso adapter pattern, stesso Resilience4j chain, stessa policy API key (env var + Logback filter), stessa observability strategy (`*_api_event_log` + Micrometer).
- **Future-proof**: quando il SDK ufficiale Anthropic raggiungerà GA su Maven Central (gap `tpm-anthropic-sdk-jvm-version` chiuso), bastano due passi non distruttivi: (1) aggiungere `com.anthropic:anthropic-java:<version>` a `build.gradle.kts`, (2) flip `anthropic.client.impl=sdk`. Zero refactor service-side.
- **Testabilità**: `qa-dev` mocka `AnthropicClient` per integration test (Testcontainers WireMock già a stack). Vedi §Validation.
- **Cost-awareness built-in**: rate limiter 12/min allineato al cap US-041; tracking token + cost per request → dashboard budget gap `tpm-llm-cost-budget-r2`.

### Negative

- **Manutenzione manuale schema Messages API**: finché si usa `AnthropicRestClient`, breaking changes Anthropic (nuovi campi response, deprecation di `anthropic-version: 2023-06-01`) richiedono update manuale del client. Mitigazione: header API version stabile + monitoraggio changelog Anthropic; in caso di breaking change, aggiungere `AnthropicRestClientV2` con nuovo header version e switch via property.
- **Duplicazione minima** se in futuro si manterranno entrambe le implementazioni (`AnthropicSdkClient` + `AnthropicRestClient`). Mitigazione: una sola attiva alla volta via `@ConditionalOnProperty`; suite di contract test condivisa (stesse asserzioni su entrambe le impl).
- **Nessun supporto streaming nella v1**: l'interfaccia `AnthropicClient.complete()` è sync request/response (sufficiente per US-041/042/047, tutte batch). Se future US richiederanno streaming UI live (es. mostrare token in arrivo nel FE), servirà estensione `AnthropicClient.stream(LlmRequest): Flow<LlmChunk>` (non in scope MVP).

### Neutral

- Dipendenza `RestClient` Spring 6.1+ già a stack (`raw/tech_stack.md` §Backend: "WebClient per chiamate FMP esterne"). Nessuna nuova dipendenza Maven richiesta per l'implementazione primaria.
- Modello `claude-opus-4-7` confermato come scelta di prodotto dall'utente — non oggetto di questo ADR. Eventuali downgrade a Claude Sonnet/Haiku per ridurre budget (gap `tpm-llm-cost-budget-r2`) restano configurabili via `anthropic.model` senza modificare codice.

## Validation

### Unit test

- `AnthropicRestClientTest` con MockWebServer / WireMock:
  - Request body non contiene mai `temperature`, `top_p`, `top_k` (assert top-level keys = `{model, max_tokens, system, messages}`).
  - Header `x-api-key` + `anthropic-version: 2023-06-01` presenti.
  - Mapping 400/401/429/529/5xx → `LlmException` corretto.
  - Parsing response: `content[0].text`, `usage.input_tokens`, `usage.output_tokens`, `stop_reason`.

### Integration test

- `LlmResilienceConfigIT` (Spring Boot Test + WireMock):
  - 3 errori 429 consecutivi → 3 retry → eccezione finale `LlmException.RateLimited` propagata.
  - 5 errori 500 → circuit breaker apre → chiamata successiva fail-fast (no HTTP call).
  - Cap 12 chiamate/min: la 13° in finestra 60s → `RequestNotPermitted`.
  - Avvio senza `ANTHROPIC_API_KEY` → `ApplicationContext` fail-fast con messaggio chiaro.

### Contract test (mock LLM per US-041)

- `MungerInversionServiceIT` (mock `AnthropicClient` con risposte pre-canned):
  - 10 query + 1 sintesi → 11 chiamate → cap 12/min rispettato.
  - Risposta malformata (non parseable) → fallback nel parser, errore loggato, report con `livello_rischio = RISCHIO_BASSO` + warning.
  - 12° chiamata → blocked by rate limiter (test cap).

## Pros / Cons of the Options

### Opzione A — SDK ufficiale come dipendenza diretta

**Pro**:
- Type-safe out-of-the-box, gestione streaming nativa, mapping errori già implementato dal SDK.
- Aggiornamenti automatici allo schema Messages API via bump versione SDK.

**Con**:
- **Blocking**: il SDK Anthropic Java è in beta su GitHub (`anthropics/anthropic-sdk-java`) al knowledge cutoff agent (2025-08); la disponibilità su Maven Central a maggio 2026 **non è verificabile** da questo agent (no WebFetch, gap aperto `tpm-anthropic-sdk-jvm-version`).
- Lock-in al ciclo di release Anthropic Java SDK.
- Bug pre-1.0 potenziali → rischio in produzione.
- I service Spring vedrebbero direttamente classi `com.anthropic.*` → refactor cross-cutting se in futuro si volesse cambiare provider (es. fallback a Gemini per budget).

### Opzione B — HTTP client Spring diretto

**Pro**:
- Zero dipendenze extra (`RestClient` già a stack).
- Controllo completo su Resilience4j (chain `RateLimiter → CircuitBreaker → Retry → HTTP` allineata [ADR-016](ADR-016-fmp-operations-throttling.md)).
- Allineato a pattern `FmpAdapterRestClient` ex [ADR-004](ADR-004-fmp-integration.md).

**Con**:
- Serializzazione/deserializzazione manuale dello schema Messages API (mitigato da spec OpenAPI Anthropic pubblica + `anthropic-version: 2023-06-01` stabile).
- Update manuale per breaking changes Anthropic (mitigato: header versione stabile + ADR successivo per upgrade).
- I service Spring dipenderebbero direttamente da `RestClient` → meno isolamento future-proof rispetto a Opzione C.

### Opzione C — Adapter behind interface (scelta)

**Pro**:
- **Resiliente alla disponibilità SDK** (risolve gap `tpm-anthropic-sdk-jvm-version` per design, non per attesa).
- Caller (`MungerInversionService`, `NewsSentimentClassifier`, `NewsScoutService`) disaccoppiati dalla scelta SDK vs HTTP — testabilità massima.
- Pattern coerente con [ADR-004](ADR-004-fmp-integration.md) §1 (`interface FmpAdapter` + impl `FmpAdapterRestClient`).
- Switch trasparente quando il SDK Maven Central sarà disponibile (zero refactor service-side).
- Permette future estensioni (provider Gemini / OpenAI fallback) come implementazioni alternative della stessa interfaccia, se il budget LLM lo richiederà.

**Con**:
- Complessità implementativa leggermente maggiore (interfaccia + 1-2 implementazioni). Mitigazione: una sola impl attiva di default (`AnthropicRestClient`); l'opzionale `AnthropicSdkClient` può essere aggiunto in un secondo momento.

## Pending clarifications

Nessuna `hard`. Gap aperti correlati (`soft`, non bloccanti per la decisione architetturale, ma rilevanti per dimensionamento):

- `wiki/gaps.md §tpm-anthropic-sdk-jvm-version` — **risolto per design** da questo ADR (Opzione C); chiusura formale del gap riservata a wiki-keeper dopo verifica utente della disponibilità Maven Central del SDK ufficiale.
- `wiki/gaps.md §tpm-llm-cost-budget-r2` — budget LLM R2 stimato $110-175/mese; conferma utente richiesta prima del go-live EP-011/012 (non blocca implementazione TSK-104/105/107/108/109/111/128).

## Pagine collegate

- [ADR-004](ADR-004-fmp-integration.md) — pattern adapter di riferimento
- [ADR-005](ADR-005-rule-engine-design.md) — Strategy pattern correlato (rule engine consumer del Munger verdict)
- [ADR-008](ADR-008-observability-logging.md) — observability standard (`*_api_event_log` + Micrometer)
- [ADR-016](ADR-016-fmp-operations-throttling.md) — Resilience4j chain `RateLimiter → CircuitBreaker → Retry → HTTP`
- [[munger-inversion-rag]] — 10 query Munger + flusso RAG (consumer principale)
- [[panic-buy-vs-value-trap-detection]] — consumer news sentiment (US-042)
- [[value-investor-bot-architecture]] — consumer news scout (US-047)
- [components/backend-components.md](../components/backend-components.md)
- [overview.md](../overview.md)
