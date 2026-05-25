---
id: ADR-020
title: ROE lookback policy per Deep Analysis (EP-011) — espone sia 5y sia 10y
status: accepted
created: 2026-05-25
accepted: 2026-05-25
deciders: [lead-architect, marco.ciullo]
consulted: [tpm, be-dev]
pending_clarification: []
---
# ADR-020 — ROE lookback policy per Deep Analysis (EP-011)

## Contesto

L'applicazione presenta una **discrepanza documentata** tra il calcolo del ROE medio nel componente legacy `agent.py` v2.6.1 e il Rule Engine Kotlin attualmente in produzione:

| Componente | Lookback ROE | Uso |
|---|---|---|
| `agent.py` v2.6.1 (Python legacy) | **5 anni** (`ROE_5Y_AVG`) | Bot Buffett-style con preferenza per turnaround / growth signal |
| Rule Engine Kotlin (EP-010 Graham Defensive Investor) | **10 anni** (`ROE_10Y_AVG`) | Vincolo verbatim Graham *Defensive Investor* (continuità ed earnings stability multi-ciclo) |

[^src: wiki/concepts/value-investor-bot-architecture.md §Strategia LLM Ibrida + sezione lookback]
[^src: wiki/concepts/value-investing-rule-engine.md §Defensive Investor ROE 10y]

Trade-off:

- **5y favorisce turnaround / growth** — un'azienda con un ciclo di degrado seguito da ripresa nei 5 anni recenti viene catturata. Più reattivo, ma più rumoroso (può promuovere cyclical late-stage).
- **10y favorisce stabilità Graham defensive** — esclude aziende che non hanno attraversato un ciclo completo. Più conservativo, ma può escludere turnaround validi (es. società risanate post-2020).

EP-011 (Deep Analysis 10-K/10-Q) introduce un'ottica **Munger inversion** che esamina il filing per costruire la "bear thesis" e produce un report LLM. La domanda aperta — non risolta in ADR-005 (Rule Engine) né in ADR-017 (Anthropic SDK) né in ADR-018 (embeddings/RAG) — è **quale lookback ROE alimenti il payload Deep Analysis** e di conseguenza il report Munger LLM.

Gap `agent-py-roe-lookback-policy` (`wiki/gaps.md`) traccia l'assenza di una decisione formale.

## Decisione

**Deep Analysis (EP-011) espone entrambi i segnali ROE come metriche distinte e affiancate**, mai sostituite l'una dall'altra:

| Metrica | Lookback | Origine | Significato semantico |
|---|---|---|---|
| `ROE_5Y_AVG` | 5 anni fiscali più recenti | Porting algoritmo `agent.py` v2.6.1 | Growth / turnaround signal |
| `ROE_10Y_AVG` | 10 anni fiscali più recenti | Rule Engine Kotlin esistente (EP-010) | Stabilità Graham *Defensive Investor* |

### Specifica payload Deep Analysis

Il `verdict_payload` esposto dall'endpoint `GET /deep/{ticker}` (US-045) **deve includere entrambi i campi** in modo esplicito e tipizzato (OpenAPI 3.1):

```json
{
  "ticker": "AAPL",
  "roe": {
    "fiveYearAvg": 0.265,
    "tenYearAvg": 0.312,
    "fiveYearDataPoints": 5,
    "tenYearDataPoints": 10
  },
  ...
}
```

Se per un ticker non sono disponibili 10 anni di dati (es. IPO recente), `tenYearAvg` resta `null` e `tenYearDataPoints` indica l'effettivo numero di anni disponibili (`< 10`). Stessa logica per `fiveYearAvg` su ticker IPO post-2022 (`tenYearDataPoints` può essere `null` o `< 5`).

### Input al report Munger LLM (US-044)

Il prompt LLM per il report Munger riceve **entrambe le metriche** come parte del contesto strutturato pre-RAG, accompagnate da una nota interpretativa:

> "ROE 5y: <value> (growth/turnaround signal). ROE 10y: <value> (Graham defensive stability signal). Discutere divergenza significativa (|5y − 10y| > 5pp) come indicatore di cambio strutturale del business."

Questo permette all'LLM di **commentare la divergenza** invece di dover scegliere arbitrariamente un lookback.

### Invarianza EP-010 (Graham defensive)

**EP-010 (Defensive Investor screener) continua a usare esclusivamente `ROE_10Y_AVG`**, allineato al vincolo verbatim Graham. Nessuna modifica al criterio originale del Rule Engine ([ADR-005](ADR-005-rule-engine-design.md)).

## Motivazioni

1. **Vincolo verbatim Graham (PATTERN §11)** — il criterio ROE 10y del Defensive Investor è normativo e non sostituibile. EP-010 deve restare invariato.
2. **Valore informativo agent.py legacy** — l'algoritmo 5y di `agent.py` v2.6.1 cattura un segnale reale (turnaround) che gli utenti Buffett-style apprezzano. Eliminarlo perderebbe valore.
3. **No false dichotomy** — non c'è un "lookback giusto" universale; esiste un trade-off legittimo. Esporre entrambi delega all'analista (e all'LLM Munger) la sintesi qualitativa.
4. **Coerenza con `[[value-investor-bot-architecture]]`** — la wiki cita entrambi i lookback come parte della "Strategia LLM Ibrida" senza arbitrare. Questo ADR formalizza l'esposizione duale.
5. **Costo computazionale trascurabile** — calcolare entrambi richiede una sola fetch FMP `income-statement` (già caching 24h via ADR-003/v2 + ADR-014); i due AVG sono O(n) su dataset locale.

## Alternative considerate

### Opzione A — Esporre solo `ROE_5Y_AVG` in Deep Analysis (porting `agent.py` puro)

**Pro**: semplicità, fedeltà al legacy.
**Contro**: incoerente con EP-010 Graham; analisti che usano Deep Analysis perdono il contesto della stabilità multi-ciclo. **Rifiutata.**

### Opzione B — Esporre solo `ROE_10Y_AVG` (uniformità con EP-010)

**Pro**: coerenza interna applicativa.
**Contro**: perde il valore del segnale turnaround che `agent.py` aveva incorporato; downgrade rispetto al legacy. **Rifiutata.**

### Opzione C — Scelta utente runtime (parametro query `?roeLookback=5|10`)

**Pro**: massima flessibilità.
**Contro**: complica payload, costringe FE a scelta esplicita che la maggior parte degli utenti non vuole fare, e impedisce all'LLM Munger di commentare la divergenza. **Rifiutata.**

### Opzione D — Esporre entrambi (scelta)

**Pro**: ricchezza informativa, no perdita di valore legacy, LLM Munger può commentare divergenza, EP-010 invariato.
**Contro**: leggero aumento dimensione payload (+2 campi numerici).
**Scelta.**

## Conseguenze

### Aggiornamenti US esistenti

- **US-044 (Munger LLM report)** — il payload di input al prompt LLM include sia `ROE_5Y_AVG` sia `ROE_10Y_AVG`, con la nota interpretativa sulla divergenza (vedi §"Input al report Munger LLM"). [^src: management/kanban/EP-011/US-044/US-044.md (da aggiornare)]
- **US-045 (`/deep/{ticker}` endpoint)** — schema OpenAPI 3.1 del response include il blocco `roe` con `fiveYearAvg`, `tenYearAvg`, `fiveYearDataPoints`, `tenYearDataPoints` (vedi §"Specifica payload Deep Analysis"). [^src: management/kanban/EP-011/US-045/US-045.md (da aggiornare)]

### EP-010 invariato

EP-010 (Graham Defensive Investor) continua a usare **esclusivamente** `ROE_10Y_AVG`. Nessun TSK di refactor su EP-010.

### TSK proposti per EP-011

Da risolvere a numerazione concreta in fase tpm:

- **TSK-EP011-A — Calcolo `ROE_5Y_AVG` lato Rule Engine Kotlin** (be-dev): porting algoritmo da `agent.py` v2.6.1, riusa la stessa fetch FMP `income-statement` già caching-aware; aggiungi metodo `RoeCalculator.fiveYearAverage(...)` accanto a `tenYearAverage(...)` esistente. Aggiungere test unitari su edge case (IPO recente, anni mancanti, ROE negativo) e contract test su payload `/deep/{ticker}`.
- **TSK-EP011-B — Estensione payload `/deep/{ticker}`** (be-dev): aggiornamento DTO + OpenAPI 3.1 + ProblemDetail flatten compliance (ADR-012). Verifica backward-compat se EP-011 già rilasciato.
- **TSK-EP011-C — Prompt LLM Munger include nota interpretativa divergenza** (be-dev / qa-dev): aggiornamento template prompt + golden test su output strutturato.

Verifica preventiva: se `RoeCalculator.fiveYearAverage` è **già presente** nel Rule Engine Kotlin (porting parziale già eseguito), TSK-EP011-A si riduce a esposizione del dato nel payload senza nuovo calcolo. Da confermare in fase tpm tramite ispezione codice (`/dev` o `state-scan`).

### Costo / performance

- Nessuna nuova chiamata FMP esterna (stesso dataset `income-statement` 10y già fetchato).
- Payload Deep Analysis incrementa di 4 campi numerici (~80 byte JSON): trascurabile.
- Cache filing-combo 90gg (ADR-019) invalida correttamente: le metriche ROE non dipendono dal filing 10-K specifico ma dalla serie income-statement, già gestita da `FmpCacheService`.

### Wiki

- `wiki-keeper` aggiorna `[[value-investor-bot-architecture]]` (sezione "Strategia LLM Ibrida") e `[[value-investing-rule-engine]]` per riflettere la decisione duale di lookback.
- Gap `agent-py-roe-lookback-policy` si considera risolto a L4; chiusura formale a cura di `wiki-keeper`.

## Pagine collegate

- [[value-investor-bot-architecture]]
- [[value-investing-rule-engine]]
- [ADR-005](ADR-005-rule-engine-design.md) — Rule Engine design (EP-010 invariato)
- [ADR-012](ADR-012-problemdetail-rfc9457-flatten.md) — ProblemDetail RFC 9457 flatten
- [ADR-017](ADR-017-anthropic-sdk-jvm.md) — Anthropic SDK + Resilience4j (LLM Munger)
- [ADR-019](ADR-019-llm-cost-budget-telemetry.md) — LLM cost budget (impatta on-demand LLM Munger)
- `wiki/gaps.md` §agent-py-roe-lookback-policy
- US-044, US-045 (EP-011)
