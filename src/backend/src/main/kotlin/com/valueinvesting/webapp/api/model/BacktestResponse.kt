package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

// DTO Backtest — payload del nuovo endpoint GET /api/analysis/{ticker}/backtest
// (EP-024 Fase 3, US-105 / TSK-346..348).
//
// Verifica storica del round-trip "compra a sconto + nel momento giusto →
// rivendi" per il singolo ticker. Calcolo DETERMINISTICO pure-function Kotlin,
// nessuna call LLM, nessuna scrittura DB per `equity`.
//
// Disciplina point-in-time (US-105 §"Ricostruzione point-in-time"):
//   - Fondamentali: filtrati per `acceptedDate`/`filingDate` ≤ t (anti
//     look-ahead grossolano).
//   - Indicatori TA: calcolati solo su EOD ≤ t.
//   - Verdetto a t: stesso mapping deterministico US-103 / ADR-030, gate VI
//     primario hardcoded.
//
// Limite residuo (sempre dichiarato in `caveats`):
//   - I fondamentali FMP sono serviti **ristrutturati** → revisioni successive
//     non sono eliminabili. `caveats.lookAheadResidual = true`.
//   - Single ticker, niente equity curve di portafoglio.
//
// [^src: management/kanban/EP-024-.../US-105-.../US-105.md §"Onesta dei limiti"]
// [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"Sintesi: la regola delle due domande"]
// [^src: wiki/syntheses/ta-stop-placement-position-sizing.md §"Il caso motivante: COPART"]
// [^src: memory/semantic/value-investing-design-lens.md]

@Schema(
    name = "BacktestStatus",
    description = """
Stato del backtest:
- OK: backtest completato, metriche presenti.
- INSUFFICIENT_HISTORY: lo storico FMP disponibile non copre la finestra richiesta
  (IPO recente o `years` troppo lunghi rispetto al disponibile); nessun risultato
  parziale, `insufficientHistoryReason` esplicito.
""",
)
enum class BacktestStatus {
    OK,
    INSUFFICIENT_HISTORY,
}

@Schema(
    name = "BacktestStrategy",
    description = """
Strategia simulata sulla stessa finestra di lookback:
- EP024_ENTER_NOW: apre un trade ad ogni `t` con verdetto EP-024 `ENTER_NOW`
  (gate VI + TA favorevole), chiude alla prima tra VI_TARGET / STOP_HIT / HORIZON.
- VI_ONLY: ignora il timing TA — apre un trade ad ogni `t` con gate VI positivo,
  stesse regole di uscita. Baseline per isolare il valore del layer di timing.
- BUY_AND_HOLD: trade unico — compra al primo EOD della finestra, vende all'ultimo.
""",
)
enum class BacktestStrategy {
    EP024_ENTER_NOW,
    VI_ONLY,
    BUY_AND_HOLD,
}

@Schema(
    name = "BacktestExitReason",
    description = """
Causale di uscita dal round-trip simulato (priorita' decrescente):
- VI_TARGET: prezzo >= dcfIntrinsicValue a t — la margin of safety si e' chiusa,
  disciplina di vendita value (Graham/Buffett, margin-of-safety).
- STOP_HIT: prezzo <= stopSuggestion.stopPrice a t — la tesi/struttura si e' rotta
  (Murphy §Page 82, Elder §50).
- HORIZON: raggiunto `horizonMonths` senza target ne stop — chiusura forzata
  al prezzo corrente.
""",
)
enum class BacktestExitReason {
    VI_TARGET,
    STOP_HIT,
    HORIZON,
}

@Schema(
    name = "BacktestTimingEdgeLabel",
    description = """
Etichetta del `timingEdgePct` (EP024_ENTER_NOW.avgReturnPct − VI_ONLY.avgReturnPct):
- POSITIVE_EDGE: timing TA ha aggiunto soldi (>+2pp di edge).
- NEGATIVE_EDGE: timing TA ha distrutto soldi (<-2pp di edge).
- NEUTRAL: edge irrilevante (entro +/- 2pp). Coerente con la lente di valore:
  una feature di timing che e' ricorrentemente NEUTRAL/NEGATIVE_EDGE non sta
  dando valore al verdetto.
""",
)
enum class BacktestTimingEdgeLabel {
    POSITIVE_EDGE,
    NEUTRAL,
    NEGATIVE_EDGE,
}

@Schema(name = "BacktestWindow", description = "Finestra di lookback effettivamente coperta.")
data class BacktestWindow(
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val years: Int,
    val horizonMonths: Int,
)

@Schema(name = "BacktestExitBreakdown", description = "Conteggio dei trade per causale di uscita.")
data class BacktestExitBreakdown(
    @field:Schema(description = "Trade chiusi al raggiungimento del dcfIntrinsicValue (VI target).")
    val viTarget: Int = 0,
    @field:Schema(description = "Trade chiusi per stop loss (struttura rotta).")
    val stopHit: Int = 0,
    @field:Schema(description = "Trade chiusi al raggiungimento dell'orizzonte.")
    val horizon: Int = 0,
)

@Schema(
    name = "BacktestStrategyMetrics",
    description = """
Metriche aggregate per strategia. `winRate` / `avgRealizedRewardRisk` /
`exitBreakdown` sono null per la strategia BUY_AND_HOLD (trade unico).
""",
)
data class BacktestStrategyMetrics(
    val strategy: BacktestStrategy,
    @field:Schema(description = "Numero di round-trip aperti nella finestra.")
    val trades: Int,
    @field:Schema(description = "Quota di trade con returnPct > 0. Null se trades=0.")
    val winRate: Double?,
    @field:Schema(description = "Media aritmetica dei `returnPct` per round-trip. Null se trades=0.")
    val avgReturnPct: Double?,
    @field:Schema(description = "Mediana dei `returnPct` per round-trip. Null se trades=0.")
    val medianReturnPct: Double?,
    @field:Schema(description = "Media degli holding days per round-trip. Null se trades=0.")
    val avgHoldingDays: Double?,
    @field:Schema(description = "Media del rapporto (returnPct realizzato / rischio implicito stopDistancePct). Null se trades=0 o non calcolabile.")
    val avgRealizedRewardRisk: Double?,
    @field:Schema(description = "Total return composto della strategia (prodotto dei (1+returnPct) − 1). Null se trades=0.")
    val totalReturnPct: Double?,
    @field:Schema(description = "Drawdown massimo intra-trade osservato sull'intera serie di trade (magnitudo positiva). Null se trades=0.")
    val maxTradeDrawdownPct: Double?,
    @field:Schema(description = "Conteggio dei trade per causale di uscita. Null per BUY_AND_HOLD.")
    val exitBreakdown: BacktestExitBreakdown?,
    @field:Schema(description = "True quando la strategia non ha generato alcun trade nella finestra (es. EP024 senza segnali ENTER_NOW).")
    val noSignalsInPeriod: Boolean = false,
)

@Schema(
    name = "BacktestTimingEdge",
    description = """
Edge del layer di timing TA: differenza in punti percentuali tra `avgReturnPct`
di EP024_ENTER_NOW e di VI_ONLY. E' il cuore della verifica (US-105 §"Baseline
di confronto"): risponde a "il timing TA ha fatto guadagnare di piu' rispetto
a comprare appena il titolo era a sconto?".
""",
)
data class BacktestTimingEdge(
    @field:Schema(description = "avgReturnPct(EP024_ENTER_NOW) − avgReturnPct(VI_ONLY). Null se una delle due strategie non ha trade.")
    val timingEdgePct: Double?,
    val label: BacktestTimingEdgeLabel,
    @field:Schema(description = "True se EP024_ENTER_NOW non ha generato segnali nella finestra (degrada il confronto, label = NEUTRAL).")
    val noSignalsInPeriod: Boolean,
)

@Schema(name = "BacktestTrade", description = "Singolo round-trip simulato (entry → exit).")
data class BacktestTrade(
    val strategy: BacktestStrategy,
    val entryDate: LocalDate,
    val entryPrice: Double,
    val exitDate: LocalDate,
    val exitPrice: Double,
    val exitReason: BacktestExitReason,
    @field:Schema(description = "(exitPrice − entryPrice) / entryPrice × 100.")
    val returnPct: Double,
    @field:Schema(description = "Giorni di calendario tra entryDate e exitDate.")
    val holdingDays: Int,
    @field:Schema(description = "Drawdown massimo intra-trade in % (magnitudo positiva): min(close) − entryPrice rispetto a entryPrice.")
    val maxIntraTradeDrawdownPct: Double,
)

@Schema(
    name = "BacktestCaveats",
    description = """
Onesta dei limiti (US-105 §"Onesta dei limiti"): SEMPRE presente nel payload,
anche per status=OK. Un backtest che nasconde i bias residui e' marketing, non
evidenza.
""",
)
data class BacktestCaveats(
    @field:Schema(description = "Fondamentali FMP serviti ristrutturati: filingDate elimina il look-ahead grossolano ma non le revisioni successive.")
    val lookAheadResidual: Boolean = true,
    @field:Schema(description = "Single ticker — niente survivorship bias, ma risultato NON generalizzabile.")
    val singleTicker: Boolean = true,
    @field:Schema(description = "Verifica storica del timing su QUESTO ticker, NON una equity curve di portafoglio ne una promessa di rendimento futuro.")
    val notPortfolioPerformance: Boolean = true,
)

@Schema(
    name = "BacktestResponse",
    description = """
Payload del nuovo endpoint GET /api/analysis/{ticker}/backtest (EP-024 Fase 3,
US-105).

Misura il round-trip completo della strategia EP-024 (entry sui segnali
`ENTER_NOW`, exit alla prima tra VI_TARGET / STOP_HIT / HORIZON) e lo confronta
con due baseline:
  - `VI_ONLY` — entra a ogni `t` con gate VI positivo, ignorando il timing TA.
  - `BUY_AND_HOLD` — trade unico sulla finestra.

`timingEdgePct` = EP024.avgReturnPct − VI_ONLY.avgReturnPct e' la metrica
chiave che giustifica (o smentisce) il layer di timing.

Calcolo deterministico, nessuna call LLM. `equity` MAI persistita server-side.
`caveats` SEMPRE presente.
""",
)
data class BacktestResponse(
    val ticker: String,
    val evaluatedAt: Instant,
    val status: BacktestStatus,
    @field:Schema(description = "Presente solo quando status = INSUFFICIENT_HISTORY.")
    val insufficientHistoryReason: String? = null,
    val window: BacktestWindow?,
    @field:Schema(description = "Metriche aggregate per le 3 strategie. Null quando status = INSUFFICIENT_HISTORY.")
    val strategies: List<BacktestStrategyMetrics>?,
    @field:Schema(description = "Edge del layer di timing EP024 vs VI_ONLY. Null quando status = INSUFFICIENT_HISTORY.")
    val timingEdge: BacktestTimingEdge?,
    @field:Schema(description = "Trade individuali per EP024_ENTER_NOW e VI_ONLY (BUY_AND_HOLD ha 1 solo trade implicito). Null quando status = INSUFFICIENT_HISTORY.")
    val trades: List<BacktestTrade>?,
    val caveats: BacktestCaveats = BacktestCaveats(),
)
