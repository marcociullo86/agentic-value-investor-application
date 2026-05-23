---
id: ADR-019
title: LLM cost telemetry + budget alert (no kill-switch automatico) — LLM on-demand manuale dalla scheda dettaglio
status: accepted
created: 2026-05-23
updated: 2026-05-23
accepted: 2026-05-23
deciders: [lead-architect, marco.ciullo]
consulted: [tpm, product-manager, be-dev, db-dev]
pending_clarification: []
supersedes: ADR-019-v1 (kill-switch automatico, rifiutato dall'utente)
---
# ADR-019 v2 — LLM cost telemetry + budget alert (no kill-switch automatico)

## Aggiornamento 2026-05-23 — Switch policy: LLM on-demand-only

Decisione di prodotto utente (2026-05-23): **lo step LLM per la lettura dei filing SEC e l'arricchimento del singolo titolo NON deve mai essere eseguito in modo automatico**. Si attiva **solo** quando l'utente preme esplicitamente un pulsante "Avvia analisi LLM" nella scheda dettaglio del titolo (sia che ci arrivi via search diretto sia via lista screener notturno).

Conseguenze:

- Il kill-switch automatico al 90% del budget (proposta v1) è **rifiutato**: la spesa è già containerizzata dalla UX (utente clicca consapevolmente). Un kill-switch automatico è più pericoloso che utile (blocca utenti legittimi a fine mese).
- Il batch notturno EP-012 NON usa LLM Claude Opus (US-041, US-042 non vengono mai eseguite automaticamente).
- Il batch notturno EP-012 **continua** a usare il `_segnale_3_news_buffett_filter` (Gemini 2.5 Flash, ~$0.10/mese) solo per identificare candidati allo screener. Questo non legge filings SEC né arricchisce il singolo titolo — è solo un meccanismo di scoperta.

## Contesto

EP-011 (Deep Analysis 10-K/10-Q) ed EP-012 (Top Value Picks) introducono per la prima volta nel backend Kotlin chiamate al provider LLM Anthropic Claude Opus 4.7 (`claude-opus-4-7`). Le US consumer e i loro **trigger**:

| US | Cosa fa | Modello | Trigger | Volume per evento |
|---|---|---|---|---|
| US-041 (Munger inversion 10-K/10-Q) | 10 query RAG su filing + 1 sintesi | Claude Opus 4.7 | **Manuale on-demand** dalla scheda dettaglio (pulsante "Avvia analisi LLM") | 12 chiamate Opus per ticker |
| US-042 (News sentiment classifier) | Classifica TEMPORARY_PANIC / STRUCTURAL_DAMAGE / NEUTRAL su news 90gg + press releases | Claude Opus 4.7 | **Manuale on-demand** (stesso pulsante) | 1 chiamata Opus per ticker |
| US-047 / TSK-128 (News scout screener) | Identifica candidati Buffett-style dalle news + press releases ultime 7gg | **Gemini 2.5 Flash** | **Automatico** batch notturno 02:00 UTC | 1 chiamata batch aggregata (no per ticker) |

L'integrazione tecnica del client (`AnthropicClient` interface + `AnthropicRestClient` HTTP + Resilience4j chain) è definita da [ADR-017](ADR-017-anthropic-sdk-jvm.md). Questo ADR risolve l'aspetto complementare: **come monitorare/limitare la spesa**.

### Pricing pubblico (maggio 2026)

| Provider | Input | Output |
|---|---|---|
| Anthropic Claude Opus 4.7 | $15/1M | $75/1M |
| Google Gemini 2.5 Flash | $0.30/1M | $2.50/1M |

[^src: wiki/gaps.md §tpm-llm-cost-budget-r2]

### Stime di costo (policy on-demand)

| Scenario | Volume mensile | Costo stimato |
|---|---|---|
| Segnale 3 batch (Gemini Flash, 1 chiamata/giorno) | 30 run/mese | **~$0.10/mese** |
| Scheda dettaglio LLM on-demand, 10 utenti × 5 click/giorno × 30gg | 1500 chiamate Opus | **~$735/mese senza cache** |
| Stesso scenario con cache filing-combo 90gg (~10% refresh rate) | ~150 chiamate effettive | **~$75/mese** |
| Stesso con cache user-level (deduplica click multipli stesso ticker × mese) | ~50 chiamate effettive | **~$25/mese** |

**Range realistico a regime con tutte le cache attive: $30-100/mese.** Budget $150/mese = ~50% margine di sicurezza.

## Decision Drivers

1. **Trigger esplicito utente già containerizza la spesa** — l'utente vede il pulsante "Avvia analisi LLM" e la stima costo prima di cliccare. Non può "sforare per errore". Kill-switch automatico → falso problema.
2. **Visibilità in tempo reale obbligatoria** — ogni chiamata deve essere tracciata con costo + ticker + user + endpoint. Senza telemetria granulare il debugging di picchi è impossibile.
3. **Persistenza counter cross-restart** — il contatore mensile deve sopravvivere a restart/deploy. Memoria in-process non sufficiente.
4. **Cache user-level (nuovo, v2)** — un utente che clicca 3 volte "Avvia analisi" sullo stesso ticker nello stesso mese deve produrre solo 1 chiamata Opus (cache hit per ticker × user × year_month). Implementazione naturale dato che già esiste cache filing-combo 90gg.
5. **Admin freeze manuale d'emergenza** — durante un incident (es. attacco DoS, prompt injection, bug retry loop), l'admin deve poter bloccare immediatamente tutte le chiamate LLM senza redeploy.
6. **Alert ma non blocco** — soglia 80% del budget invia notifica/email all'admin (Slack webhook configurabile), 100% emette WARN log + freezing **manuale** disponibile.
7. **Costo trasparente in UI** — il pulsante "Avvia analisi LLM" deve mostrare la stima costo della chiamata e l'utilizzo budget mensile corrente (es. "$0.49 stimato — budget mensile usato 23%").

## Considered Options

### Opzione A — Solo alerting (soft limit), no kill-switch, freeze admin manuale (scelta v2)

Telemetria + soglie warning (80%/100%) → alert. Nessun blocco automatico. Endpoint admin `POST /admin/llm-cost/freeze` per stop manuale d'emergenza (no restart richiesto, toggle live).

### Opzione B — Kill-switch automatico a 90% (proposta v1, rifiutata)

Blocco automatico delle nuove chiamate al raggiungimento del 90% del budget. Override admin via env var + restart. **Rifiutata**: l'utente trigger esplicito già fa da freno; aggiungere blocco automatico crea UX scadente a fine mese e non risolve un problema reale.

### Opzione C — Counter in-memory + reset al restart

Counter perso a ogni restart applicativo. **Rifiutata**: perdita di osservabilità inaccettabile.

### Opzione D — Provider esterno (Helicone, Langfuse) come gateway proxy

Tutto il traffico Anthropic passa attraverso un servizio esterno con telemetria/budget integrati. **Rifiutata**: dipendenza esterna + costo ricorrente + lock-in vendor.

## Decision Outcome

**Scelta: Opzione A — Telemetria persistente + alert (80%/100%) + endpoint admin freeze manuale. Nessun kill-switch automatico.**

### 1. Budget cap mensile (advisory, no enforcement automatico)

| Property | Default | Env var |
|---|---|---|
| `llm.budget.monthly-cap-usd` | `150.00` | `LLM_BUDGET_MONTHLY_CAP_USD` |
| `llm.budget.alert-threshold-percent` | `80` | `LLM_BUDGET_ALERT_THRESHOLD_PERCENT` |
| `llm.budget.frozen` | `false` | `LLM_BUDGET_FROZEN` (true → blocca tutte le chiamate LLM, admin-controlled) |
| `llm.budget.reset-cron` | `0 0 0 1 * *` (1° del mese 00:00 UTC) | `LLM_BUDGET_RESET_CRON` |
| `llm.budget.timezone` | `UTC` | `LLM_BUDGET_TIMEZONE` |
| `llm.budget.cost.input-per-1k-usd.opus` | `0.015` | `LLM_COST_INPUT_PER_1K_USD_OPUS` |
| `llm.budget.cost.output-per-1k-usd.opus` | `0.075` | `LLM_COST_OUTPUT_PER_1K_USD_OPUS` |
| `llm.budget.cost.input-per-1k-usd.gemini-flash` | `0.0003` | `LLM_COST_INPUT_PER_1K_USD_GEMINI_FLASH` |
| `llm.budget.cost.output-per-1k-usd.gemini-flash` | `0.0025` | `LLM_COST_OUTPUT_PER_1K_USD_GEMINI_FLASH` |
| `llm.budget.alert.slack-webhook-url` | (vuoto) | `LLM_BUDGET_ALERT_SLACK_WEBHOOK_URL` |

### 2. Telemetria persistente (invariata da v1)

#### 2.1 Tabella `llm_cost_counter` (1 row per mese)

| Colonna | Tipo | Note |
|---|---|---|
| `year_month` | `CHAR(7) PRIMARY KEY` | `YYYY-MM` UTC |
| `total_cost_usd` | `NUMERIC(10,4) NOT NULL DEFAULT 0` | Cost cumulativo del mese |
| `total_calls` | `BIGINT NOT NULL DEFAULT 0` | Number di chiamate totali |
| `total_tokens_in` | `BIGINT NOT NULL DEFAULT 0` | |
| `total_tokens_out` | `BIGINT NOT NULL DEFAULT 0` | |
| `cache_hits` | `BIGINT NOT NULL DEFAULT 0` | Chiamate evitate (= savings) |
| `alert_80_sent_at` | `TIMESTAMPTZ NULL` | Quando l'alert 80% è stato inviato (NULL = mai) |
| `alert_100_sent_at` | `TIMESTAMPTZ NULL` | Idem 100% |
| `last_updated` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |

UPSERT atomico PostgreSQL (invariato da v1).

#### 2.2 Tabella `llm_call_log` (1 row per chiamata)

| Colonna | Tipo | Note |
|---|---|---|
| `id` | `BIGSERIAL PK` | |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |
| `endpoint` | `VARCHAR(64)` | `munger-inversion`, `news-sentiment`, `news-scout`, etc. |
| `purpose` | `VARCHAR(32)` | `munger`, `news_sentiment`, `news_scout` |
| `ticker` | `VARCHAR(16)` | NULL per scout aggregato |
| `user_id` | `BIGINT NULL` | NULL se chiamata system (es. scout batch) |
| `request_id` | `UUID` | Per tracciare retry e dedup |
| `model` | `VARCHAR(64)` | `claude-opus-4-7`, `gemini-2.5-flash` |
| `input_tokens` | `INT NOT NULL` | |
| `output_tokens` | `INT NOT NULL` | |
| `cost_usd` | `NUMERIC(10,6) NOT NULL` | Calcolato post-call con pricing config |
| `cache_hit` | `BOOLEAN NOT NULL DEFAULT false` | true se la response è stata servita da cache |
| `error_code` | `VARCHAR(32) NULL` | Es. `RATE_LIMIT`, `API_ERROR`, NULL se success |
| `latency_ms` | `INT NOT NULL` | Tempo end-to-end della chiamata |

Indici: `(created_at)`, `(purpose, created_at)`, `(ticker, created_at)`, `(user_id, ticker, created_at)`.

Retention: 90 giorni (`@Scheduled` settimanale cancella row più vecchie).

### 3. Cache user-level (nuovo, v2)

In aggiunta alla cache filing-combo 90gg (US-041) e news sentiment cache (US-042), introduce:

**Cache key `(ticker, user_id, year_month, llm_purpose)`**. Un utente che clicca "Avvia analisi LLM" sullo stesso ticker entro il mese corrente riceve la response cached, **senza** generare una nuova chiamata Claude Opus.

Implementazione: estendere `LlmCallLogger` AOP per controllare cache_hit prima di invocare `AnthropicClient`. Se cache hit, log `cache_hit=true` con costo $0.

### 4. Endpoint admin

| Endpoint | Method | Auth | Funzione |
|---|---|---|---|
| `GET /admin/llm-cost` | GET | `ROLE_ADMIN` | Payload: utilization%, budget cap, total cost mese, breakdown per purpose, history 30gg, freeze status |
| `POST /admin/llm-cost/freeze` | POST | `ROLE_ADMIN` | Imposta `llm.budget.frozen=true` runtime (no restart). Blocca immediatamente tutte le chiamate LLM. |
| `POST /admin/llm-cost/unfreeze` | POST | `ROLE_ADMIN` | Sblocca. |

Freeze attivo → endpoint LLM-dependent restituiscono `HTTP 503 LLM_FROZEN_BY_ADMIN` (ProblemDetail RFC 9457). I client devono mostrare un banner "Analisi LLM temporaneamente disabilitata dall'amministratore". Endpoint non-LLM continuano a funzionare.

### 5. Frontend integration (US-046)

Il pulsante "Avvia analisi LLM" nella scheda dettaglio deve mostrare:

- **Stima costo della chiamata**: es. "≈ $0.49" calcolata da `LlmCostEstimator` (max_tokens × pricing).
- **Utilizzo budget mensile**: es. "Budget mensile usato 23% ($35 / $150)".
- **Cache hit signal**: se l'utente ha già premuto il pulsante per quel ticker nel mese, il pulsante mostra "Mostra analisi precedente" invece di "Avvia analisi LLM" (servita da cache user-level, costo $0).

### 6. Resilience4j chain LLM (invariata da v1, ADR-017 §5)

`BudgetGuard.check()` prima della chain `RateLimiter → CircuitBreaker → Retry → HTTP`:

```
BudgetGuard
   ├─ frozen? → throw LlmFrozenException → HTTP 503 LLM_FROZEN_BY_ADMIN
   ├─ cache hit? → return cached response (no LLM call, log cache_hit=true)
   └─ else → proceed to Resilience4j chain → Anthropic API
```

Nota: nessun "block at 90%" come in v1. Il sistema continua finché:
- Admin non freeza manualmente
- O Anthropic restituisce rate-limit (gestito da Resilience4j retry)

### 7. Alert (80% e 100%)

Cron `@Scheduled` ogni 5 min controlla `llm_cost_counter` del mese corrente:

- **80% del cap** → invia notifica Slack via `llm.budget.alert.slack-webhook-url` (idempotente — alert una sola volta per mese, `alert_80_sent_at` previene duplicati). Log WARN: "LLM monthly budget 80% reached".
- **100% del cap** → log ERROR + Slack alert. Il sistema **continua a funzionare**, ma l'admin riceve segnale forte per valutare freeze manuale o aumento budget.

### 8. Reset mensile

Cron Spring `@Scheduled` `0 0 0 1 * *` UTC: insert nuova row `llm_cost_counter` per il mese corrente con tutti i campi a 0. Idempotente (UPSERT). Reset cache user-level: la `year_month` nel cache key cambia naturalmente → tutti i ticker tornano "fresh" il 1° del mese.

## Pros / Cons of the Options

### Opzione A (scelta)

**Pros**:
- UX migliore (utente non si trova bloccato a fine mese senza preavviso)
- Coerente con policy "LLM solo on-demand manuale"
- Admin mantiene controllo via freeze manuale d'emergenza
- Telemetria full = full observability
- Cache user-level riduce significativamente costo reale a regime
- Implementazione più semplice (no UPSERT-and-check race condition logic)

**Cons**:
- Teoricamente possibile overrun del budget se: (a) admin non monitora gli alert, (b) cache fallisce, (c) utenti aggressivi. Mitigazione: alert 80% + freeze admin endpoint sono sufficienti per uso normale single-tenant.

### Opzione B (kill-switch automatico, rifiutata)

**Pros**: garanzia hard di non superare il budget.
**Cons**: UX scadente a fine mese (utente clicca, riceve 503 generico), creazione di "bug retry loop" lato client, override admin richiede restart container.

## Migration & Rollout

1. **Migration `V0XX__llm_cost_tracking.sql`** (TSK nuovo): crea tabelle `llm_cost_counter` + `llm_call_log` + indici + retention job.
2. **Implementazione `LlmCostCounterService` + `LlmBudgetGuard`** (TSK nuovo, be-dev): UPSERT atomico, freeze check, cache user-level lookup.
3. **Implementazione `LlmCallLogger` AOP `@Around`** (TSK nuovo, be-dev): aspect su `AnthropicClient.complete` + `GeminiClient.complete` (per segnale 3 scout).
4. **Endpoint admin** (TSK nuovo, be-dev): `GET /admin/llm-cost` + `POST /admin/llm-cost/{freeze|unfreeze}`.
5. **Alert Slack** (TSK nuovo, be-dev): `LlmBudgetAlertJob` cron 5min.
6. **Frontend integration** (TSK nuovo, fe-dev): pulsante "Avvia analisi LLM" con stima costo + budget bar + cache hit handling.
7. **Test integration**: WireMock + Testcontainers (counter persistence, freeze toggle, cache hit, alert idempotency).

## Consequences

### Positive
- Containment naturale via UX (utente trigger esplicito + cache + admin freeze).
- Telemetria granulare per debugging picchi di spesa.
- Cache user-level riduce drasticamente costo a regime (~$25/mese realistico).
- Admin freeze permette risposta immediata a incident senza redeploy.

### Negative
- Nessuna garanzia hard sul budget (richiede operatore attento agli alert).
- Implementazione admin endpoint richiede `ROLE_ADMIN` modellato in Spring Security (verifica con ADR-006 / ADR-010).

### Neutral
- Cache user-level richiede schema migration (1 nuova tabella) ma riusa pattern già esistente.
- Endpoint amministrativi `/admin/*` richiedono routing + auth filter aggiornamenti.

## Validation

- Test: counter UPSERT atomico sotto carico concorrente (2 chiamate parallele non producono double-count).
- Test: cache user-level dedup verificato (stesso ticker, stesso user, stesso mese → 1 sola chiamata Opus).
- Test: freeze admin endpoint → ogni successiva chiamata LLM ritorna 503 `LLM_FROZEN_BY_ADMIN`.
- Test: alert 80% inviato una sola volta per mese (idempotenza `alert_80_sent_at`).
- Test: reset mensile crea nuova row e azzera tutti i contatori cumulativi.

## Riferimenti

- [ADR-008](ADR-008-observability-logging.md) — pattern logging + Micrometer
- [ADR-016](ADR-016-fmp-operations-throttling.md) — Resilience4j chain riferimento
- [ADR-017](ADR-017-anthropic-sdk-jvm.md) — Anthropic SDK + Resilience4j chain LLM
- [wiki/concepts/munger-inversion-rag.md](../../wiki/concepts/munger-inversion-rag.md) §"Costo LLM"
- [wiki/concepts/value-investor-bot-architecture.md](../../wiki/concepts/value-investor-bot-architecture.md) §"Strategia LLM Ibrida"
- US implicate: US-041, US-042, US-045, US-046, US-047 (con patch 2026-05-23)
