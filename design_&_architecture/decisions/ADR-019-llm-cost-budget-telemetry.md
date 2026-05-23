---
id: ADR-019
title: LLM cost budget R2 + telemetry + kill-switch automatico per chiamate Claude Opus 4.7
status: proposed
created: 2026-05-23
deciders: [lead-architect, marco.ciullo]
consulted: [tpm, product-manager, be-dev, db-dev]
pending_clarification: []
---
# ADR-019 — LLM cost budget R2: telemetry, persistenza counter, kill-switch automatico

## Contesto

EP-011 (Deep Analysis 10-K/10-Q) ed EP-012 (Top Value Picks) introducono per la prima volta nel backend Kotlin chiamate al provider LLM Anthropic Claude Opus 4.7 (`claude-opus-4-7`). Tre US sono consumer dirette: US-041 (Munger inversion: 10 query RAG + 1 sintesi per ticker analizzato deep), US-042 (news sentiment classifier: 1 chiamata per ticker), US-047 (news scout screener: 1 chiamata batch aggregata) [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-041-munger-inversion-llm/US-041.md §Business Rules] [^src: wiki/concepts/value-investor-bot-architecture.md §"Strategia LLM Ibrida"].

L'integrazione tecnica del client (`AnthropicClient` interface + `AnthropicRestClient` HTTP + Resilience4j chain) è già definita da [ADR-017](ADR-017-anthropic-sdk-jvm.md). Questo ADR risolve l'aspetto complementare: **chi e come limita la spesa**.

### Pricing pubblico Anthropic Opus 4.7 (maggio 2026)

| Tipo token | Costo |
|---|---|
| Input | $15 / 1M token ($0.015 / 1k) |
| Output | $75 / 1M token ($0.075 / 1k) |

[^src: wiki/gaps.md §tpm-llm-cost-budget-r2]

### Stime di costo per chiamata

| US | Input medio | Output medio | Costo / unità |
|---|---|---|---|
| US-041 Munger inversion (10 query + 1 sintesi, per ticker analizzato deep) | ~8000 token | ~2000 token | $0.12 + $0.15 = **~$0.27/ticker** [^src: wiki/concepts/munger-inversion-rag.md §"Costo LLM"] [^src: raw/agent.py:1455-1459] |
| US-042 News sentiment classifier (per ticker) | ~7000 token | ~1500 token | **~$0.22/ticker** [^src: raw/agent.py:1569-1573] |
| US-047 News scout screener (per batch run) | ~8000 token | ~500 token | **~$0.16/run** [^src: management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/TSK-128.md §Cosa fare] |

### Stime aggregate mensili

- **Batch notturno EP-012** (30 ticker × 1 run/giorno): 30 × ($0.27 + $0.22) + $0.16 ≈ **~$14.86/run × 30gg = ~$446/mese senza cache**
- **Con cache** (filing-combo TTL 90gg + news sentiment TTL 30gg, ~10% refresh rate): **~$45/mese a regime**
- **On-demand utente** (US-045 endpoint `/api/analysis/{ticker}/deep`): ipotesi conservativa 50 chiamate/giorno × 10 utenti = 500/giorno × $0.49 → **~$735/mese con cache**

**Range realistico stato a regime (cache attiva): $50-150/mese.**

Il gap `wiki/gaps.md §tpm-llm-cost-budget-r2` (2026-05-23) richiede conferma budget utente + meccanismo automatico di contenimento spesa. Senza enforcement, un bug (es. cache invalidata, retry loop, abuso utenti) può portare la spesa a multipli del range stimato in poche ore.

## Decision Drivers

1. **Containment garantito** — il sistema NON deve mai superare un budget configurato senza intervento esplicito umano. Soft limit (alert) non basta: serve hard stop con kill-switch.
2. **Visibilità in tempo reale** — ogni chiamata deve essere tracciata con costo stimato e attribuita a US/endpoint/ticker. Senza telemetria granulare il debugging dei picchi di spesa è impossibile.
3. **Persistenza counter cross-restart** — il contatore mensile deve sopravvivere a restart applicativi, deploy, crash. Memoria in-process non sufficiente: serve tabella DB con UPSERT atomico.
4. **Graceful degradation** — al raggiungimento della soglia il sistema deve continuare a funzionare per i campi non-LLM (rule engine, FMP data, watchlist), restituendo `503 LLM_BUDGET_EXCEEDED` solo sugli endpoint LLM-dependent.
5. **Override admin di emergenza** — durante incident o eventi straordinari, l'operatore deve poter disabilitare temporaneamente il kill-switch via env var senza redeploy.
6. **Coerenza con ADR-008 / ADR-016 / ADR-017** — la telemetria LLM riusa il pattern già esistente (`*_api_event_log` + Micrometer + Actuator) e si innesta nella Resilience4j chain LLM definita da ADR-017 §5.
7. **Decisione di budget = decisione di prodotto** — il valore numerico del budget è di product-manager + utente; questo ADR fissa il *meccanismo*, non il valore (proposto come default ma overridabile).

## Considered Options

### Opzione A — Solo alerting (soft limit), nessun kill-switch

Configurare metriche Micrometer + alert Prometheus al superamento del 80%/90%/100% del budget; affidare all'operatore l'intervento manuale (disabilitare endpoint via feature flag).

### Opzione B — Counter in-memory + kill-switch reset al restart

`AtomicLong` per costo cumulativo mensile, kill-switch a 90%; counter perso al restart applicativo (= riparte da zero).

### Opzione C — Counter DB-persisted + kill-switch automatico + override admin (scelta)

Tabella `llm_cost_counter` con UPSERT atomico per mese, `LlmCostCounterService` che pre-stima costo + verifica budget pre-call, blocco automatico a 90%, override via property `llm.budget.kill-switch.enabled=false`, log granulare in `llm_call_log` per attribuzione.

### Opzione D — Provider esterno (Helicone, Langfuse) come gateway proxy

Tutto il traffico Anthropic passa attraverso un servizio esterno che applica budget enforcement, telemetria e cache.

## Decision Outcome

**Scelta: Opzione C — Counter DB-persisted + kill-switch automatico a 90% del budget + override admin via property + log granulare per attribuzione cost.**

### 1. Budget cap mensile (configurabile)

Default proposto: **$150/mese**. Margine conservativo sopra il range a regime stimato ($50-150/mese con cache attiva), inferiore al worst-case on-demand ($735/mese) — costringe a calibrare la cache e a tenere d'occhio i picchi.

Configurazione tramite property con override env var:

| Property | Default | Env var |
|---|---|---|
| `llm.budget.monthly-cap-usd` | `150.00` | `LLM_BUDGET_MONTHLY_CAP_USD` |
| `llm.budget.kill-switch-threshold-percent` | `90` | `LLM_BUDGET_KILL_SWITCH_THRESHOLD_PERCENT` |
| `llm.budget.kill-switch.enabled` | `true` | `LLM_BUDGET_KILL_SWITCH_ENABLED` |
| `llm.budget.reset-cron` | `0 0 0 1 * *` (1° del mese 00:00 UTC) | `LLM_BUDGET_RESET_CRON` |
| `llm.budget.timezone` | `UTC` | `LLM_BUDGET_TIMEZONE` |
| `llm.budget.cost.input-per-1k-usd` | `0.015` | `LLM_COST_INPUT_PER_1K_USD` |
| `llm.budget.cost.output-per-1k-usd` | `0.075` | `LLM_COST_OUTPUT_PER_1K_USD` |
| `llm.budget.cost.estimate-multiplier-pre-call` | `1.20` | `LLM_COST_ESTIMATE_MULTIPLIER_PRE_CALL` (margine 20% sulla stima pre-call) |

Il valore $150 è una **proposta** da confermare con utente (vedi `Pending clarifications`).

### 2. Telemetria obbligatoria

#### 2.1 Tabella `llm_cost_counter` (1 row per mese)

Aggregato mensile per kill-switch e dashboard:

| Colonna | Tipo | Note |
|---|---|---|
| `year_month` | `CHAR(7) PRIMARY KEY` | Formato `YYYY-MM` UTC (es. `2026-05`) |
| `total_cost_usd` | `NUMERIC(10,4) NOT NULL DEFAULT 0` | Cost cumulativo del mese |
| `total_calls` | `BIGINT NOT NULL DEFAULT 0` | Numero chiamate totali (success + error) |
| `total_tokens_in` | `BIGINT NOT NULL DEFAULT 0` | Input token cumulativi |
| `total_tokens_out` | `BIGINT NOT NULL DEFAULT 0` | Output token cumulativi |
| `cache_hits` | `BIGINT NOT NULL DEFAULT 0` | Numero chiamate evitate per cache hit (= savings) |
| `kill_switch_triggered_at` | `TIMESTAMPTZ NULL` | Timestamp primo trigger del kill-switch nel mese (NULL se mai triggerato) |
| `last_updated` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | Ultimo aggiornamento UPSERT |

Upsert atomico (PostgreSQL):

```sql
INSERT INTO llm_cost_counter (year_month, total_cost_usd, total_calls, total_tokens_in, total_tokens_out, last_updated)
VALUES (?, ?, 1, ?, ?, now())
ON CONFLICT (year_month) DO UPDATE SET
  total_cost_usd = llm_cost_counter.total_cost_usd + EXCLUDED.total_cost_usd,
  total_calls    = llm_cost_counter.total_calls + 1,
  total_tokens_in  = llm_cost_counter.total_tokens_in + EXCLUDED.total_tokens_in,
  total_tokens_out = llm_cost_counter.total_tokens_out + EXCLUDED.total_tokens_out,
  last_updated   = now();
```

#### 2.2 Tabella `llm_call_log` (1 row per chiamata)

Log granulare per attribuzione cost + audit + debugging:

| Colonna | Tipo | Note |
|---|---|---|
| `call_id` | `UUID PRIMARY KEY` | Generato lato applicativo |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | Indicizzato per query temporali |
| `endpoint` | `VARCHAR(64) NOT NULL` | Es. `/api/analysis/{ticker}/deep`, `batch.universe-screener`, `batch.munger-analysis` |
| `purpose` | `VARCHAR(32) NOT NULL` | `munger` \| `news_sentiment` \| `news_scout` (coerente con ADR-017 §6) |
| `ticker` | `VARCHAR(16) NULL` | NULL per chiamate batch aggregate (US-047) |
| `model` | `VARCHAR(32) NOT NULL` | Es. `claude-opus-4-7` |
| `input_tokens` | `INT NOT NULL` | Da response `usage.input_tokens` |
| `output_tokens` | `INT NOT NULL` | Da response `usage.output_tokens` |
| `cost_usd` | `NUMERIC(8,4) NOT NULL` | Calcolato lato applicativo (input × 0.015/1k + output × 0.075/1k) |
| `cache_hit` | `BOOLEAN NOT NULL DEFAULT FALSE` | `true` se la risposta è stata servita da cache 90gg/30gg (in tal caso `input_tokens = output_tokens = 0` e `cost_usd = 0` ma viene comunque loggata per metriche) |
| `error_code` | `VARCHAR(32) NULL` | NULL se success; valori: `RATE_LIMITED`, `OVERLOADED`, `INVALID_REQUEST`, `AUTH_ERROR`, `SERVER_ERROR`, `TIMEOUT`, `BUDGET_EXCEEDED` (coerente con `LlmException` sealed hierarchy ADR-017 §1) |
| `latency_ms` | `INT NOT NULL` | Durata end-to-end della chiamata (escluso retry interno) |
| `user_id` | `VARCHAR(64) NULL` | Attribuzione utente se on-demand; NULL per batch notturni |
| `request_id` | `VARCHAR(64) NULL` | `X-Request-Id` propagato per correlazione log (ADR-008 §1) |

Indici:
- `idx_llm_call_log_created_at` (default per dashboard / cleanup retention)
- `idx_llm_call_log_endpoint_purpose` (per breakdown per endpoint)
- `idx_llm_call_log_ticker` partial `WHERE ticker IS NOT NULL` (per audit per ticker)

Retention: **90 giorni** (consistente con `fmp_api_event_log` ex ADR-004 §5). Cleanup automatico via partition pruning o `DELETE` schedulato.

#### 2.3 Metriche Micrometer

Estensione delle metriche già previste da ADR-017 §6, con focus budget:

| Metrica | Tipo | Tag | Scopo |
|---|---|---|---|
| `llm.cost.usd.cumulative` | gauge | `year_month` | Spesa cumulativa mese corrente (letta da `llm_cost_counter`) |
| `llm.budget.utilization.percent` | gauge | — | `total_cost_usd / monthly_cap_usd * 100` |
| `llm.budget.remaining.usd` | gauge | — | `monthly_cap_usd - total_cost_usd` |
| `llm.budget.kill-switch.active` | gauge | — | `0` (inactive) \| `1` (active) |
| `llm.cost.usd.per_call` | histogram | `purpose, endpoint` | Distribuzione costo per chiamata |
| `llm.cache.savings.usd.estimated` | counter | `purpose` | Stima risparmio da cache hit (cost-equivalent delle chiamate evitate) |
| `llm.call.blocked.count` | counter | `reason=budget_exceeded` | Chiamate bloccate dal kill-switch |

### 3. Kill-switch automatico

#### 3.1 Algoritmo pre-call

`LlmCostCounterService.checkBudgetBeforeCall(estimatedCost)`:

```
1. Se llm.budget.kill-switch.enabled = false → ritorna ALLOW (override admin attivo).
2. Carica row corrente da llm_cost_counter WHERE year_month = current month.
3. Calcola: projectedCost = total_cost_usd + (estimatedCost * estimate_multiplier_pre_call).
4. Se projectedCost > (monthly_cap_usd * kill_switch_threshold_percent / 100) → ritorna BLOCK.
5. Altrimenti → ritorna ALLOW.
```

La pre-stima usa `max_tokens` configurati per la chiamata (es. 2000 per Munger US-041, 1500 per news sentiment US-042) come output upper bound + dimensione effettiva del prompt come input. Il `estimate_multiplier_pre_call = 1.20` aggiunge un 20% di margine per evitare di andare oltre la soglia per imprecisione della stima.

#### 3.2 Comportamento degraded

Quando `LlmCostCounterService` ritorna `BLOCK`:

| Caller | Comportamento |
|---|---|
| `MungerInversionService` (US-041, US-045) | Se cache hit disponibile per `(ticker, filing_combo_id)` → serve da cache. Altrimenti throw `LlmException.BudgetExceeded`. |
| `NewsSentimentClassifier` (US-042) | Se cache hit disponibile per `(ticker, news_window_30gg)` → serve da cache. Altrimenti il flusso prosegue con `sentiment=NEUTRAL` + flag `degraded_reason=LLM_BUDGET_EXCEEDED`. |
| `NewsScoutService` (US-047) | Throw `LlmException.BudgetExceeded`; batch job logga e skippa il segnale news scout per il run (gli altri 3 segnali del screener continuano). |
| Endpoint REST `/api/analysis/{ticker}/deep` | Se tutti i campi LLM-dependent hanno cache hit → ritorna payload normale con header `X-Llm-Degraded: true`. Altrimenti ritorna **HTTP 503 ProblemDetail RFC 9457**: |

```json
{
  "type": "https://valueinvesting.app/errors/llm-budget-exceeded",
  "title": "LLM monthly budget threshold reached",
  "status": 503,
  "detail": "The system has reached 90% of the configured monthly LLM budget. Deep analysis is temporarily unavailable. Retry next month or contact admin to extend the budget.",
  "instance": "/api/analysis/AAPL/deep",
  "properties": {
    "year_month": "2026-05",
    "current_utilization_percent": 91.2,
    "monthly_cap_usd": 150.00,
    "reset_at": "2026-06-01T00:00:00Z"
  }
}
```

Code: `LLM_BUDGET_EXCEEDED` (estensione del catalogo ProblemDetail consolidato da [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md)).

#### 3.3 Re-attivazione automatica

Al primo del mese 00:00 UTC, il cron `MonthlyCounterResetJob` (vedi §4) crea una nuova row vuota per il mese successivo → `total_cost_usd = 0` → kill-switch automaticamente disattivato senza intervento manuale.

### 4. Reset mensile

Cron Spring `@Scheduled(cron = "${llm.budget.reset-cron}", zone = "${llm.budget.timezone}")` default `0 0 0 1 * *` UTC:

```kotlin
@Scheduled(cron = "\${llm.budget.reset-cron}", zone = "\${llm.budget.timezone}")
fun resetMonthlyCounter() {
    val newYearMonth = YearMonth.now(UTC).toString()
    // Idempotente: se la row esiste già (es. doppio trigger), no-op.
    llmCostCounterRepository.insertIfNotExists(newYearMonth)
    log.info("LLM_BUDGET_MONTHLY_RESET year_month={}", newYearMonth)
}
```

Il job **non cancella** righe precedenti — la history mensile rimane in `llm_cost_counter` per audit (retention indefinita, volume trascurabile: 12 righe/anno).

### 5. Override admin di emergenza

Property `llm.budget.kill-switch.enabled` (default `true`). Disabilitazione via env var senza redeploy:

```bash
LLM_BUDGET_KILL_SWITCH_ENABLED=false
# Restart container (o refresh Spring Cloud Config se attivo)
```

A property `false`, `LlmCostCounterService.checkBudgetBeforeCall()` ritorna sempre `ALLOW`. Le metriche e il counter continuano a essere aggiornati: l'override **NON** disabilita la telemetria, disabilita solo il blocco.

Ogni transizione `enabled=true → enabled=false` o viceversa viene loggata a livello `WARN` con stack di chi/quando (event `LLM_BUDGET_KILL_SWITCH_OVERRIDE`).

### 6. Persistenza counter — vedi §2.1

(Già coperto: tabella `llm_cost_counter` con UPSERT atomico, idempotente sui restart, mai cancellata.)

### 7. Cost attribution — vedi §2.2

(Già coperto: tabella `llm_call_log` con `endpoint`, `purpose`, `ticker`, `user_id`, `request_id`.)

### 8. Endpoint admin `GET /admin/llm-cost`

Autenticazione richiesta (auth admin role, riferimento [ADR-006](ADR-006-authentication.md) + [ADR-010](ADR-010-auth-consolidation.md)). Risposta JSON:

```json
{
  "current_month": "2026-05",
  "monthly_cap_usd": 150.00,
  "total_cost_usd": 42.17,
  "utilization_percent": 28.1,
  "kill_switch_enabled": true,
  "kill_switch_active": false,
  "kill_switch_threshold_percent": 90,
  "total_calls": 187,
  "total_tokens_in": 1450000,
  "total_tokens_out": 280000,
  "cache_hits": 423,
  "cache_savings_estimated_usd": 95.41,
  "breakdown_by_purpose": [
    { "purpose": "munger",          "calls": 132, "cost_usd": 35.64 },
    { "purpose": "news_sentiment",  "calls": 48,  "cost_usd": 5.28  },
    { "purpose": "news_scout",      "calls": 7,   "cost_usd": 1.12  }
  ],
  "last_30_days_history": [
    { "date": "2026-05-22", "calls": 6, "cost_usd": 1.34 },
    { "date": "2026-05-21", "calls": 8, "cost_usd": 1.87 }
  ]
}
```

Query parametri opzionali: `?year_month=2026-04` per audit storico.

### 9. Integrazione con ADR-017 (Resilience4j chain)

Il `LlmCostCounterService.checkBudgetBeforeCall()` viene eseguito **prima** della Resilience4j chain LLM (RateLimiter → CircuitBreaker → Retry → HTTP) — è un check di pre-condizione che non consuma rate-limit né apre il circuit breaker se ritorna `BLOCK`.

Ordine effettivo della pipeline LLM end-to-end:

```
BudgetGuard.check()
  → (BLOCK) → throw LlmException.BudgetExceeded (no Resilience4j invocato)
  → (ALLOW) → RateLimiter → CircuitBreaker → Retry → AnthropicClient.complete()
              → (success) → LlmCallLogger.log() → UPSERT llm_cost_counter
              → (error)   → LlmCallLogger.log(error_code) → UPSERT llm_cost_counter (calls+1, cost=0 se no token consumati)
```

### 10. Implementation TSK proposti (da rivedere con TPM)

Da aggiungere a EP-011 (sotto US-041 oppure come nuova US dedicata `US-049 LLM budget enforcement`):

| TSK ID (proposto) | Owner | Cosa fare | Stima |
|---|---|---|---|
| `TSK-XXX-A` | be-dev | `LlmCostCounterService` + `LlmBudgetGuard` (`checkBudgetBeforeCall` + UPSERT atomico post-call) + `MonthlyCounterResetJob` `@Scheduled` + integrazione cache lookup in `MungerInversionService` / `NewsSentimentClassifier` per il path degraded | M (2-3gg) |
| `TSK-XXX-B` | db-dev | Migration Flyway `V0XX__llm_cost_tracking.sql` (tabelle `llm_cost_counter` + `llm_call_log` + indici) + job retention 90gg su `llm_call_log` (pgcron o Spring `@Scheduled`) | S (1gg) |
| `TSK-XXX-C` | be-dev | `LlmCallLogger` AOP `@Around` su `AnthropicClient.complete` (calcola cost da `usage.input_tokens` / `usage.output_tokens`, persiste in `llm_call_log` + UPSERT counter) + endpoint admin `GET /admin/llm-cost` (`AdminLlmCostController` + DTO + auth admin role) + `LLM_BUDGET_EXCEEDED` ProblemDetail mapping in `GlobalExceptionHandler` | M (2gg) |

Dipendenze: TSK-XXX-B deve precedere TSK-XXX-A; TSK-XXX-A e TSK-XXX-C possono procedere in parallelo dopo B; tutti dipendono da TSK-104 (`AnthropicClient` bean da ADR-017).

## Migration & Rollout

Plan step-by-step (consistente con la sequenza TSK proposta in §10):

1. **DB migration** (TSK-XXX-B, db-dev): applicare `V0XX__llm_cost_tracking.sql`. Pre-popolare la row del mese corrente: `INSERT INTO llm_cost_counter (year_month) VALUES ('2026-05') ON CONFLICT DO NOTHING`.
2. **Budget guard service** (TSK-XXX-A, be-dev): implementare `LlmCostCounterService` con pre-check + UPSERT post-call. In questa fase il guard è **passivo**: logga ma non blocca (`llm.budget.kill-switch.enabled=false` di default per i primi 7 giorni di rollout per raccogliere baseline empirico).
3. **Call logger AOP + admin endpoint** (TSK-XXX-C, be-dev): `LlmCallLogger` AOP wrapper su `AnthropicClient.complete` per popolare `llm_call_log`. Endpoint admin `GET /admin/llm-cost` esposto.
4. **Calibrazione baseline** (7 giorni shadow mode): osservare `llm.budget.utilization.percent` reale sotto carico normale + on-demand. Validare che la stima pre-call (basata su `max_tokens` + prompt size) sia entro ±15% del costo effettivo post-call. Aggiustare `estimate_multiplier_pre_call` se necessario.
5. **Kill-switch attivo** (deploy successivo): set `llm.budget.kill-switch.enabled=true`. Validare comportamento degraded su staging con `monthly-cap-usd=5.00` artificiale (forza il trigger entro 1-2 chiamate).
6. **Cron mensile reset** (verifica al primo del mese seguente il rollout): osservare in produzione che `MonthlyCounterResetJob` esegua al cambio di mese e che la metrica `llm.cost.usd.cumulative` riparta da 0.
7. **Cleanup retention** (cron settimanale): `DELETE FROM llm_call_log WHERE created_at < now() - interval '90 days'`. Volume atteso: 90gg × ~30 chiamate/giorno ≈ 2700 righe — pulizia rapida.

Rollback plan: in qualsiasi step, set `llm.budget.kill-switch.enabled=false` via env var + restart container. La telemetria continua a funzionare; il blocco è disattivato. Per rollback completo della feature: `flyway undo` (se abilitato) o migration di drop sulle 2 tabelle.

## Consequences

### Positive

- **Spesa LLM contenuta automaticamente** sotto il budget configurato senza intervento manuale; nessun rischio di "bolletta sorpresa" da bug o abuso.
- **Visibilità completa**: ogni chiamata Claude è tracciata con costo, endpoint, ticker, utente. Audit e debugging banali via SQL ad-hoc su `llm_call_log`.
- **Graceful degradation**: il sistema continua a funzionare per i campi non-LLM (rule engine, FMP, watchlist) anche a kill-switch attivo. L'utente riceve una risposta strutturata e prevedibile (HTTP 503 ProblemDetail) invece di un errore generico.
- **Cache-friendly**: il modello `cache_hits` in `llm_cost_counter` rende esplicito quanto la cache stia risparmiando, incentivando la tuning corretta dei TTL (90gg filing / 30gg news) e fornendo segnale prodotto sul ROI della cache.
- **Coerenza architetturale**: stesso pattern di [ADR-008](ADR-008-observability-logging.md) (`*_api_event_log` + Micrometer), stesso ProblemDetail format di [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md), stesso adapter pattern di [ADR-017](ADR-017-anthropic-sdk-jvm.md). Zero novità tecnologiche introdotte.
- **Future-proof multi-provider**: se in futuro si aggiungerà un fallback (Claude Sonnet/Haiku per ridurre costi, oppure Gemini), il counter è agnostico al provider — basta aggiungere una colonna `provider` a `llm_call_log` e dimensionare i costi per modello.

### Negative

- **Overhead pre-call**: ogni chiamata Claude paga 1 SELECT su `llm_cost_counter` + 1 UPSERT. Stima: ~2-5ms per chiamata, trascurabile rispetto alla latenza Anthropic (~5-20s per Opus 4.7 con `max_tokens=2000`).
- **Stima pre-call imprecisa**: il pre-check usa `max_tokens` come upper bound dell'output, ma il modello potrebbe restituire molto meno (es. 500 token su un `max_tokens=2000`). Il margine `estimate_multiplier_pre_call=1.20` mitiga, ma in casi limite il kill-switch può triggerare leggermente prima del 90% reale. Accettabile (è un soft margine voluto).
- **Race condition multi-instance**: se il backend è scalato a N istanze, due chiamate concorrenti potrebbero entrambe passare il check pre-call con totale=89% e portare il totale a 91% post-call. Mitigazione: l'UPSERT è atomico sul DB → il valore reale post-call è sempre consistente; il primo che eccede triggera il blocco per le successive. Worst-case: 1-2 chiamate extra oltre la soglia, costo aggiuntivo trascurabile.
- **Retention `llm_call_log`** aggiunge volume DB: 90gg × ~30 chiamate/giorno × ~200B/riga ≈ 540KB. Trascurabile su PostgreSQL.
- **Conferma budget richiesta**: il valore $150/mese è una proposta del lead-architect; va validato con product-manager / utente prima del go-live EP-011/012. Se il valore reale dovesse essere significativamente diverso (es. $500 o $30), gli impatti sui Business Rules di US-041/045/047 potrebbero richiedere revisione.

### Neutral

- **Endpoint admin `GET /admin/llm-cost`** richiede ruolo admin: ipotizza l'esistenza di un ruolo `ROLE_ADMIN` in Spring Security. Se non già modellato, va aggiunto come pre-requisito (riferimento ADR-006 + ADR-010).
- **Override admin via env var** richiede restart container per essere efficace. Spring Cloud Config con refresh dinamico è fuori scope (non a stack). Accettabile: l'override è per scenari di emergenza dove un restart di 30 secondi è tollerabile.
- **Modello cost** ($15 in / $75 out per 1M token) hardcoded come default ma overridable via property. Se Anthropic dovesse cambiare pricing, basta aggiornare `llm.budget.cost.input-per-1k-usd` / `output-per-1k-usd` senza redeploy. ADR-019 non viene superseded da una variazione di pricing.

## Validation

### Unit test

- `LlmCostCounterServiceTest`:
  - `checkBudgetBeforeCall(estimatedCost=1.00)` con `total_cost_usd=80.00`, `cap=150.00`, `threshold=90%` → `ALLOW` (`projectedCost = 80 + 1.20 = 81.20 < 135`).
  - `checkBudgetBeforeCall(estimatedCost=50.00)` con `total_cost_usd=120.00`, `cap=150.00`, `threshold=90%` → `BLOCK` (`projectedCost = 120 + 60 = 180 > 135`).
  - `checkBudgetBeforeCall()` con `kill-switch.enabled=false` → `ALLOW` (override attivo).
  - Calcolo cost: input 8000 token + output 2000 token → `8 × 0.015 + 2 × 0.075 = 0.27` (precisione 4 decimali).

- `LlmCallLoggerTest`:
  - Mock `AnthropicClient.complete()` success → AOP wrapper persiste 1 row in `llm_call_log` con `cost_usd` calcolato + UPSERT su `llm_cost_counter`.
  - Mock `AnthropicClient.complete()` throw `LlmException.RateLimited` → 1 row in `llm_call_log` con `error_code=RATE_LIMITED`, `cost_usd=0`, `total_calls+1` ma `total_cost_usd` invariato.

### Integration test

- `LlmBudgetEnforcementIT` (Spring Boot Test + Testcontainers PostgreSQL):
  - Configurazione `monthly-cap-usd=5.00` artificiale.
  - Esegue 25 chiamate Mock `AnthropicClient` da $0.25 ciascuna (cumulativo $6.25, threshold $4.50).
  - Asserzioni: chiamate 1-18 OK; chiamata 19 → `LlmException.BudgetExceeded`; `llm_cost_counter.total_cost_usd ≈ 4.50`; metrica `llm.budget.kill-switch.active = 1`.
  - Reset cron forzato → counter del mese seguente = 0 → chiamata successiva OK.

- `AdminLlmCostControllerIT`:
  - `GET /admin/llm-cost` senza auth → 401.
  - `GET /admin/llm-cost` con auth user-role → 403.
  - `GET /admin/llm-cost` con auth admin-role → 200 + payload conforme allo schema §8.
  - `GET /admin/llm-cost?year_month=2026-04` → ritorna dati storici se la row esiste, 404 ProblemDetail altrimenti.

### Contract test (degraded behavior)

- `DeepAnalysisDegradedIT`:
  - Forza kill-switch attivo (mock `LlmBudgetGuard.check() → BLOCK`).
  - `GET /api/analysis/AAPL/deep` con cache hit completo → 200 + header `X-Llm-Degraded: true`.
  - `GET /api/analysis/AAPL/deep` con cache miss → 503 ProblemDetail con type `https://valueinvesting.app/errors/llm-budget-exceeded`, status 503, properties `{year_month, current_utilization_percent, monthly_cap_usd, reset_at}`.

### Load test (manuale, staging)

- Simulazione 24h di carico nominal (30 ticker batch + 50 chiamate on-demand/giorno).
- Verifica: nessun blocco prematuro, utilization < 50% del cap, latenza pre-call check < 10ms p95, nessun lock contention sul UPSERT counter.

## Pros / Cons of the Options

### Opzione A — Solo alerting

**Pro**: implementazione minima (solo Micrometer), zero overhead pre-call.

**Con**: nessuna garanzia di contenimento. Un bug notturno (es. retry loop su prompt malformato) può sprecare $1000 prima che l'alert raggiunga l'operatore. Inaccettabile per il driver §1.

### Opzione B — Counter in-memory + kill-switch

**Pro**: zero dipendenze DB, latenza pre-call trascurabile.

**Con**: counter perso al restart → un deploy notturno può azzerare il contatore. Multi-instance impossibile (ogni JVM ha il suo `AtomicLong`). Inaccettabile per il driver §3.

### Opzione C — DB-persisted (scelta)

**Pro**: garantisce containment cross-restart, multi-instance safe (UPSERT atomico PostgreSQL), audit completo via `llm_call_log`, coerenza con pattern `fmp_api_event_log` ex ADR-004 §5.

**Con**: ~5ms overhead per chiamata + 540KB DB volume. Trascurabili.

### Opzione D — Provider esterno (Helicone / Langfuse)

**Pro**: feature complete out-of-the-box (budget, telemetria, cache, A/B testing su modelli).

**Con**: dipendenza esterna addizionale (vendor lock-in), costo subscription, latenza extra (proxy hop), dati LLM (prompt + response) passano via terze parti — implicazioni privacy/compliance non valutate. Sovradimensionato per MVP. Riconsiderabile in R3+ se la complessità crescesse.

## Pending clarifications

Nessuna `hard`. Gap aperti correlati (`soft`, non bloccanti per la decisione architetturale):

- `wiki/gaps.md §tpm-llm-cost-budget-r2` — **risolto per design** da questo ADR (meccanismo). La conferma del **valore numerico** del budget mensile ($150 proposto) resta riservata a product-manager / utente prima del go-live EP-011/012. La chiusura formale del gap è subordinata a (a) accettazione di questo ADR da parte dell'utente, (b) conferma esplicita del valore di `llm.budget.monthly-cap-usd`.

## Pagine collegate

- [ADR-004](ADR-004-fmp-integration.md) — pattern `fmp_api_event_log` di riferimento per `llm_call_log`
- [ADR-006](ADR-006-authentication.md) — auth admin per endpoint `GET /admin/llm-cost`
- [ADR-008](ADR-008-observability-logging.md) — observability standard (Micrometer + ProblemDetail + structured logging)
- [ADR-010](ADR-010-auth-consolidation.md) — ruoli admin
- [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md) — ProblemDetail RFC 9457 per response 503 `LLM_BUDGET_EXCEEDED`
- [ADR-016](ADR-016-fmp-operations-throttling.md) — pattern Resilience4j chain di riferimento
- [ADR-017](ADR-017-anthropic-sdk-jvm.md) — Anthropic Claude Opus 4.7 integration (`AnthropicClient` interface + Resilience4j LLM chain — questo ADR si innesta sopra)
- [[munger-inversion-rag]] — consumer primario LLM budget (US-041 Munger inversion)
- [[panic-buy-vs-value-trap-detection]] — consumer LLM budget (US-042 news sentiment)
- [[value-investor-bot-architecture]] — consumer LLM budget (US-047 news scout)
- [components/backend-components.md](../components/backend-components.md)
- [overview.md](../overview.md)
