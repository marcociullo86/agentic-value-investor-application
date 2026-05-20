---
id: ADR-005
title: Rule Engine design — Strategy pattern + DCF Greenwald
status: accepted
created: 2026-05-20
deciders: [lead-architect, marco.ciullo]
---
# ADR-005 — Rule Engine: Strategy pattern + DCF con Greenwald primario

## Contesto

Il Rule Engine e' il cuore logico della WebApp (EP-003 + EP-004). Le regole quantitative sono documentate in [[value-investing-rule-engine]] e nel runbook [[value-investing-rule-engine-runbook]]. La formula Owner Earnings e' stata risolta in [[vi-08-risoluzione-q001-owner-earnings]] (Q_001): Buffett 1986 con Maintenance CapEx via Greenwald primario + FCF fallback.

## Decisione

### 1. Strategy pattern per regole

Modulo `com.valueinvesting.webapp.ruleengine.rules` con interfaccia base:

```
interface ValuationRule {
    val ruleId: String                 // es. "profitability.roe"
    fun evaluate(input: FinancialDataset): RuleSignal
}

data class RuleSignal(
    val ruleId: String,
    val signal: Signal,                // GREEN | YELLOW | RED | INDETERMINATE
    val observedValue: Double?,
    val threshold: String,             // descrizione testuale soglia
    val rationale: String
)
```

Implementazioni MVP R1.0 (US-007..010):

| Rule | Class | Soglie | Fonte |
|---|---|---|---|
| ROE | `RoeRule` | GREEN > 15% (≥8/10 anni), YELLOW 10-15%, RED < 10% | [[value-investing-rule-engine]] §Redditivita' |
| ROIC | `RoicRule` | GREEN > 12-15%, YELLOW 8-12%, RED < 8% | idem |
| Gross Margin | `GrossMarginRule` | GREEN > 40%, YELLOW 30-40%, RED < 30% | §Pricing Power |
| Net Margin | `NetMarginRule` | GREEN > 10%, RED ≤ 10% | idem |
| Current Ratio | `CurrentRatioRule` | GREEN > 2, YELLOW 1.5-2, RED < 1.5 | §Solidita' Finanziaria |
| Debt/Income | `DebtToIncomeRule` | GREEN < 4, YELLOW 4-5, RED > 5; INDETERMINATE se Utile ≤ 0 | idem |
| CapEx/Income | `CapexIntensityRule` | GREEN < 25%, YELLOW 25-30%, RED > 30%; INDETERMINATE se Utile ≤ 0 | §Capitale Intensivo |

**Aggregator**: `RuleEngineService.evaluateAll(ticker)` carica `FinancialDataset` via `FmpCacheService`, invoca tutte le `ValuationRule`, persiste il risultato in `rule_engine_result`.

**Insufficient data**: se < 5 anni disponibili (US-007 AC), il segnale e' `INDETERMINATE` (mai RED per default).

### 2. Calcolo Graham Number (US-011)

`GrahamNumberCalculator.calculate(eps, bvps): GrahamNumberResult`:
- formula: `sqrt(22.5 * EPS * BVPS)` [^src: wiki/concepts/graham-number.md §Formula].
- guard: se `eps <= 0 || bvps <= 0` -> `NotApplicable("EPS o BVPS non positivi")` (US-011 AC).
- input da `key-metrics` ultimo esercizio.

### 3. DCF con Owner Earnings (US-012)

Implementazione composita in `com.valueinvesting.webapp.ruleengine.dcf`:

**Pipeline:**

```
1. Per ogni anno disponibile (max 10):
     ownerEarnings_t = netIncome_t
                       + depreciationAndAmortization_t
                       + nonCashCharges_t
                       - maintenanceCapex_t

2. maintenanceCapex_t = GreenwaldMaintenanceCapexEstimator.estimate(...)
   se eligibile, altrimenti FcfFallbackEstimator.estimate(...)
```

**Greenwald (primario):** [^src: wiki/sources/vi-08-risoluzione-q001-owner-earnings.md §Metodo 1: Il Modello di Bruce Greenwald]

```
ppeRatio_t      = grossPPE_t / revenue_t
growthCapex_t   = ppeRatio_t * max(0, revenue_t - revenue_{t-1})
maintCapex_t    = totalCapex_t - growthCapex_t
```

**FCF Fallback:** [^src: wiki/sources/vi-08-risoluzione-q001-owner-earnings.md §Metodo 3]

```
ownerEarnings_t = operatingCashFlow_t - totalCapex_t  (= FCF standard)
```

**Trigger fallback:**

- `revenue_{t-1}` mancante o `ppeRatio` non calcolabile.
- Override esplicito utente in tabella `dcf_method_override` (settori capital-intensive: Utilities, Telecom).

**Proiezione e attualizzazione:**

| Parametro | Range | Default |
|---|---|---|
| Crescita | 5%-7% capped (media storica) | 6% |
| Discount rate | 9%-10% | 10% |
| Growth perpetuo (terminal) | 2%-3% | 2.5% |
| Periodo proiezione | 10 anni | 10 |

Valori configurabili via `application.yml`. Output: `DcfResult(intrinsicValue, methodUsed, ownerEarningsSeries, projectionParams, isInsufficientData)`.

**Insufficient data**: se < 5 anni di FCF/OE positivi -> `DcfResult.insufficientData()` (US-012 AC).

### 4. Margin of Safety (US-013)

`MarginOfSafetyEvaluator.evaluate(currentPrice, dcfResult)`:

| Condizione | Signal |
|---|---|
| `dcfResult.isInsufficientData` | `NOT_CALCULABLE` |
| `currentPrice < dcfResult.intrinsicValue * 0.70` | GREEN |
| `currentPrice in [0.70x, 1.00x] * intrinsicValue` | YELLOW |
| `currentPrice >= dcfResult.intrinsicValue` | RED |

Prezzo corrente: `FmpAdapter.getProfile(ticker).price` (con cache breve TTL 1h vs 24h dei bilanci — proposta).

### 5. Persistenza risultati

`rule_engine_result` salva (vedi [data/er-diagram.md](../data/er-diagram.md)):

- `id`, `ticker`, `evaluated_at`
- `signals JSONB` (array di `RuleSignal`)
- `graham_number NUMERIC NULL`
- `dcf_intrinsic_value NUMERIC NULL`
- `dcf_method TEXT` (`GREENWALD` | `FCF_FALLBACK` | `NOT_APPLICABLE`)
- `mos_signal TEXT`
- `current_price_at_eval NUMERIC NULL`
- `source_snapshot_fetched_at TIMESTAMPTZ` (tracciabilita' freschezza)

Idempotenza: una nuova `evaluate` su un ticker il cui snapshot e' identico ritorna lo stesso risultato (replicabilita' richiesta da EP-003 valore-business).

## Conseguenze

- EP-003 (US-007..010) implementata con 7 strategy class isolate, testabili in isolamento con fixture FMP statiche.
- EP-004 completamente sbloccata: US-011 (Graham), US-012 (DCF Greenwald + FCF fallback), US-013 (MoS).
- US-014 (pannello Traffic Light) consuma direttamente `rule_engine_result.signals JSONB`.
- Nuovi indicatori (futuro R2) si aggiungono come nuove `ValuationRule` senza modificare core.

## Pagine collegate

- [[value-investing-rule-engine]]
- [[value-investing-rule-engine-runbook]]
- [[vi-08-risoluzione-q001-owner-earnings]]
- [[graham-number]] / [[intrinsic-value]] / [[margin-of-safety]] / [[economic-moat]]
- [overview.md](../overview.md)
- [components/backend-components.md](../components/backend-components.md)
