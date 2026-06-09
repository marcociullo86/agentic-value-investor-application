package com.valueinvesting.webapp.backtest

import com.valueinvesting.webapp.api.model.BacktestExitBreakdown
import com.valueinvesting.webapp.api.model.BacktestExitReason
import com.valueinvesting.webapp.api.model.BacktestStrategy
import com.valueinvesting.webapp.api.model.BacktestStrategyMetrics
import com.valueinvesting.webapp.api.model.BacktestTimingEdge
import com.valueinvesting.webapp.api.model.BacktestTimingEdgeLabel
import com.valueinvesting.webapp.api.model.BacktestTrade
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.summary.SummaryVerdict
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// BacktestRoundTripSimulator — simulazione del round-trip entry → exit
// (EP-024 / US-105 / TSK-347).
//
// Per ogni `t` con segnale di entry valido, apre un trade simulato al close
// EOD di `t` e lo chiude alla **prima** condizione che si verifica:
//
//   1) `VI_TARGET` — prezzo EOD ≥ `dcfIntrinsicValue` calcolato a `t`.
//   2) `STOP_HIT` — prezzo EOD ≤ `stopPrice` dell'advisor US-100 a `t`.
//   3) `HORIZON` — raggiunto `horizonMonths` senza target ne stop.
//
// Vincolo di non-sovrapposizione: se un trade e' aperto, i segnali successivi
// NON aprono nuovi trade (no piramiding artificioso — semantica conservativa,
// coerente con la 2% Rule Elder §50).
//
// Output: metriche aggregate per 3 strategie (EP024_ENTER_NOW / VI_ONLY /
// BUY_AND_HOLD) + `timingEdgePct` = EP024.avgReturnPct − VI_ONLY.avgReturnPct.
//
// Calcolo deterministico pure-function. Niente LLM. Niente persistenza.
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-347.md §Scope]
// [^src: wiki/syntheses/ta-stop-placement-position-sizing.md §"Principio 1"]
// [^src: wiki/concepts/margin-of-safety.md]
@Component
class BacktestRoundTripSimulator {

    /**
     * Risultato della simulazione: metriche per le 3 strategie + timingEdge +
     * lista trade.
     */
    data class SimulationResult(
        val strategies: List<BacktestStrategyMetrics>,
        val timingEdge: BacktestTimingEdge,
        val trades: List<BacktestTrade>,
    )

    /**
     * Simula tutti i round-trip + baseline su una lista di snapshot.
     *
     * @param snapshots snapshot mensili in ordine cronologico (output di
     *                  BacktestEngine.reconstruct).
     * @param fullEodSeries serie EOD completa (per la propagazione price action
     *                      tra entry e exit).
     * @param horizonMonths orizzonte massimo di holding.
     * @param effectiveFrom data iniziale finestra (per BUY_AND_HOLD).
     * @param effectiveTo data finale finestra (per BUY_AND_HOLD).
     */
    fun simulate(
        snapshots: List<BacktestEngine.VerdictSnapshot>,
        fullEodSeries: List<EodPriceRecord>,
        horizonMonths: Int,
        effectiveFrom: LocalDate,
        effectiveTo: LocalDate,
    ): SimulationResult {
        val sortedEod = fullEodSeries
            .filter { it.date != null && it.close != null }
            .sortedBy { it.date }

        // --- Strategia EP024_ENTER_NOW ----------------------------------------
        val ep024Trades = simulateStrategy(
            snapshots = snapshots,
            entrySelector = { it.summaryVerdict == SummaryVerdict.ENTER_NOW },
            strategy = BacktestStrategy.EP024_ENTER_NOW,
            sortedEod = sortedEod,
            horizonMonths = horizonMonths,
        )

        // --- Strategia VI_ONLY (baseline B1) ----------------------------------
        val viOnlyTrades = simulateStrategy(
            snapshots = snapshots,
            entrySelector = { it.viOnlyEnter },
            strategy = BacktestStrategy.VI_ONLY,
            sortedEod = sortedEod,
            horizonMonths = horizonMonths,
        )

        // --- Strategia BUY_AND_HOLD (baseline B2) -----------------------------
        val bhTrade = buildBuyAndHoldTrade(sortedEod, effectiveFrom, effectiveTo)

        // --- Metriche -----------------------------------------------------------
        val ep024Metrics = metricsFor(BacktestStrategy.EP024_ENTER_NOW, ep024Trades)
        val viOnlyMetrics = metricsFor(BacktestStrategy.VI_ONLY, viOnlyTrades)
        val bhMetrics = metricsForBuyAndHold(bhTrade)

        // --- timingEdge ---------------------------------------------------------
        val timingEdge = computeTimingEdge(ep024Metrics, viOnlyMetrics)

        // --- Trade combinati per il payload (BUY_AND_HOLD non emette singoli
        // trade nella lista: e' un trade unico implicito esposto via
        // bhMetrics.totalReturnPct).
        val allTrades = ep024Trades + viOnlyTrades

        return SimulationResult(
            strategies = listOf(ep024Metrics, viOnlyMetrics, bhMetrics),
            timingEdge = timingEdge,
            trades = allTrades,
        )
    }

    // ------------------------------------------------------------------------
    // Strategia generica (EP024 e VI_ONLY differiscono solo nell'entrySelector)
    // ------------------------------------------------------------------------

    private fun simulateStrategy(
        snapshots: List<BacktestEngine.VerdictSnapshot>,
        entrySelector: (BacktestEngine.VerdictSnapshot) -> Boolean,
        strategy: BacktestStrategy,
        sortedEod: List<EodPriceRecord>,
        horizonMonths: Int,
    ): List<BacktestTrade> {
        val trades = mutableListOf<BacktestTrade>()
        var openUntil: LocalDate? = null

        for (snap in snapshots) {
            // Non-overlap: salta i segnali finche' il trade aperto non si chiude.
            if (openUntil != null && !snap.asOf.isAfter(openUntil)) continue
            if (!entrySelector(snap)) continue

            val price = snap.tradingPrice ?: continue
            if (price <= 0.0) continue

            val trade = simulateRoundTrip(
                strategy = strategy,
                entryDate = snap.asOf,
                entryPrice = price,
                dcfIntrinsicValue = snap.dcfIntrinsicValue,
                stopPrice = snap.stopPrice,
                horizonMonths = horizonMonths,
                sortedEod = sortedEod,
            ) ?: continue

            trades += trade
            openUntil = trade.exitDate
        }
        return trades
    }

    /**
     * Simula UN singolo round-trip dal close di `entryDate` al primo trigger
     * tra VI_TARGET, STOP_HIT, HORIZON. Null se non c'e' nessun EOD post-entry
     * (es. entry molto vicino alla fine della serie EOD disponibile).
     */
    internal fun simulateRoundTrip(
        strategy: BacktestStrategy,
        entryDate: LocalDate,
        entryPrice: Double,
        dcfIntrinsicValue: Double?,
        stopPrice: Double?,
        horizonMonths: Int,
        sortedEod: List<EodPriceRecord>,
    ): BacktestTrade? {
        val horizonDate = entryDate.plusMonths(horizonMonths.toLong())

        // Cammina sugli EOD strettamente DOPO entryDate (l'entry stesso e' il
        // close di `entryDate`; chiusure intra-day non sono modellate — solo close).
        val postEntry = sortedEod.asSequence()
            .filter { it.date != null && it.date.isAfter(entryDate) }

        var minClose = entryPrice
        var triggered: Triple<LocalDate, Double, BacktestExitReason>? = null

        for (record in postEntry) {
            val d = record.date ?: continue
            val close = record.close ?: continue
            if (d.isAfter(horizonDate)) {
                // Oltre l'orizzonte: chiudi al PRIMO close >= horizonDate
                // (cioe' usa l'ultimo close ≤ horizonDate). Lo gestiamo dopo.
                break
            }
            if (close < minClose) minClose = close

            // Priority order: 1) VI_TARGET, 2) STOP_HIT. Se entrambi triggerano
            // lo stesso giorno, VI_TARGET vince (semantica conservativa: la
            // vendita value-driven ha la precedenza sulla rottura intraday
            // approssimata da un close — coerente con US-105 §"Simulazione del
            // round-trip" che elenca VI_TARGET prima di STOP).
            if (dcfIntrinsicValue != null && dcfIntrinsicValue > 0.0 && close >= dcfIntrinsicValue) {
                triggered = Triple(d, close, BacktestExitReason.VI_TARGET)
                break
            }
            if (stopPrice != null && stopPrice > 0.0 && close <= stopPrice) {
                triggered = Triple(d, close, BacktestExitReason.STOP_HIT)
                break
            }
        }

        val finalExit: Triple<LocalDate, Double, BacktestExitReason> = triggered
            ?: run {
                // HORIZON: prendi l'ultimo close ≤ horizonDate. Se non esiste
                // (entry troppo recente rispetto allo storico), abortisci il trade.
                val lastEod = sortedEod
                    .asSequence()
                    .filter { it.date != null && it.close != null && it.date.isAfter(entryDate) && !it.date.isAfter(horizonDate) }
                    .sortedByDescending { it.date }
                    .firstOrNull()
                    ?: return null
                Triple(lastEod.date!!, lastEod.close!!, BacktestExitReason.HORIZON)
            }

        val (exitDate, exitPrice, exitReason) = finalExit
        val returnPct = ((exitPrice - entryPrice) / entryPrice) * 100.0
        val holdingDays = ChronoUnit.DAYS.between(entryDate, exitDate).toInt()
        val maxDrawdownPct = if (minClose < entryPrice) {
            ((entryPrice - minClose) / entryPrice) * 100.0
        } else 0.0

        return BacktestTrade(
            strategy = strategy,
            entryDate = entryDate,
            entryPrice = entryPrice,
            exitDate = exitDate,
            exitPrice = exitPrice,
            exitReason = exitReason,
            returnPct = returnPct,
            holdingDays = holdingDays,
            maxIntraTradeDrawdownPct = maxDrawdownPct,
        )
    }

    // ------------------------------------------------------------------------
    // BUY_AND_HOLD (baseline B2)
    // ------------------------------------------------------------------------

    private fun buildBuyAndHoldTrade(
        sortedEod: List<EodPriceRecord>,
        from: LocalDate,
        to: LocalDate,
    ): BacktestTrade? {
        val first = sortedEod.firstOrNull { it.date != null && it.close != null && !it.date.isBefore(from) }
            ?: return null
        val last = sortedEod.lastOrNull { it.date != null && it.close != null && !it.date.isAfter(to) }
            ?: return null
        if (first.date == last.date) return null

        val entryPrice = first.close!!
        val exitPrice = last.close!!
        val returnPct = ((exitPrice - entryPrice) / entryPrice) * 100.0
        val holdingDays = ChronoUnit.DAYS.between(first.date!!, last.date!!).toInt()

        // Max drawdown intra-trade per BUY_AND_HOLD: minimo close nell'intera
        // finestra rispetto al prezzo di entrata.
        val minClose = sortedEod
            .asSequence()
            .filter { it.date != null && it.close != null && !it.date.isBefore(first.date) && !it.date.isAfter(last.date) }
            .mapNotNull { it.close }
            .min()
        val maxDrawdownPct = if (minClose < entryPrice) {
            ((entryPrice - minClose) / entryPrice) * 100.0
        } else 0.0

        return BacktestTrade(
            strategy = BacktestStrategy.BUY_AND_HOLD,
            entryDate = first.date!!,
            entryPrice = entryPrice,
            exitDate = last.date!!,
            exitPrice = exitPrice,
            exitReason = BacktestExitReason.HORIZON,
            returnPct = returnPct,
            holdingDays = holdingDays,
            maxIntraTradeDrawdownPct = maxDrawdownPct,
        )
    }

    // ------------------------------------------------------------------------
    // Metriche aggregate
    // ------------------------------------------------------------------------

    internal fun metricsFor(
        strategy: BacktestStrategy,
        trades: List<BacktestTrade>,
    ): BacktestStrategyMetrics {
        if (trades.isEmpty()) {
            return BacktestStrategyMetrics(
                strategy = strategy,
                trades = 0,
                winRate = null,
                avgReturnPct = null,
                medianReturnPct = null,
                avgHoldingDays = null,
                avgRealizedRewardRisk = null,
                totalReturnPct = null,
                maxTradeDrawdownPct = null,
                exitBreakdown = BacktestExitBreakdown(0, 0, 0),
                noSignalsInPeriod = true,
            )
        }

        val returns = trades.map { it.returnPct }
        val wins = trades.count { it.returnPct > 0.0 }
        val winRate = wins.toDouble() / trades.size.toDouble()
        val avg = returns.average()
        val median = median(returns)
        val avgDays = trades.map { it.holdingDays.toDouble() }.average()

        // avgRealizedRewardRisk: media(returnPct realizzato / drawdown intra-trade).
        // Drawdown 0 (trade in profitto monotono) → rapporto N/A su quel trade.
        // Calcoliamo la media sui trade con drawdown > 0 (proxy del rischio realizzato);
        // se nessun trade ha drawdown > 0, ritorniamo null.
        val rrSamples = trades.mapNotNull {
            if (it.maxIntraTradeDrawdownPct > 0.0) it.returnPct / it.maxIntraTradeDrawdownPct else null
        }
        val avgRr = if (rrSamples.isEmpty()) null else rrSamples.average()

        // totalReturnPct: ritorno composto. Se gli holding non si sovrappongono
        // (vincolo di non-sovrapposizione gia' garantito), la composizione e'
        // sequenziale: ∏(1 + r_i) − 1.
        val totalReturnPct = trades.fold(1.0) { acc, t -> acc * (1.0 + t.returnPct / 100.0) }.let { (it - 1.0) * 100.0 }

        val maxDdPct = trades.maxOf { it.maxIntraTradeDrawdownPct }

        val breakdown = BacktestExitBreakdown(
            viTarget = trades.count { it.exitReason == BacktestExitReason.VI_TARGET },
            stopHit = trades.count { it.exitReason == BacktestExitReason.STOP_HIT },
            horizon = trades.count { it.exitReason == BacktestExitReason.HORIZON },
        )

        return BacktestStrategyMetrics(
            strategy = strategy,
            trades = trades.size,
            winRate = winRate,
            avgReturnPct = avg,
            medianReturnPct = median,
            avgHoldingDays = avgDays,
            avgRealizedRewardRisk = avgRr,
            totalReturnPct = totalReturnPct,
            maxTradeDrawdownPct = maxDdPct,
            exitBreakdown = breakdown,
            noSignalsInPeriod = false,
        )
    }

    internal fun metricsForBuyAndHold(trade: BacktestTrade?): BacktestStrategyMetrics {
        if (trade == null) {
            return BacktestStrategyMetrics(
                strategy = BacktestStrategy.BUY_AND_HOLD,
                trades = 0,
                winRate = null,
                avgReturnPct = null,
                medianReturnPct = null,
                avgHoldingDays = null,
                avgRealizedRewardRisk = null,
                totalReturnPct = null,
                maxTradeDrawdownPct = null,
                exitBreakdown = null,
                noSignalsInPeriod = true,
            )
        }
        return BacktestStrategyMetrics(
            strategy = BacktestStrategy.BUY_AND_HOLD,
            trades = 1,
            winRate = if (trade.returnPct > 0.0) 1.0 else 0.0,
            avgReturnPct = trade.returnPct,
            medianReturnPct = trade.returnPct,
            avgHoldingDays = trade.holdingDays.toDouble(),
            avgRealizedRewardRisk = null,
            totalReturnPct = trade.returnPct,
            maxTradeDrawdownPct = trade.maxIntraTradeDrawdownPct,
            exitBreakdown = null,
            noSignalsInPeriod = false,
        )
    }

    // ------------------------------------------------------------------------
    // timingEdge — il cuore della verifica
    // ------------------------------------------------------------------------

    internal fun computeTimingEdge(
        ep024: BacktestStrategyMetrics,
        viOnly: BacktestStrategyMetrics,
    ): BacktestTimingEdge {
        val ep024Avg = ep024.avgReturnPct
        val viAvg = viOnly.avgReturnPct
        val noSignals = ep024.noSignalsInPeriod || ep024.trades == 0

        if (ep024Avg == null || viAvg == null) {
            return BacktestTimingEdge(
                timingEdgePct = null,
                label = BacktestTimingEdgeLabel.NEUTRAL,
                noSignalsInPeriod = noSignals,
            )
        }
        val edge = ep024Avg - viAvg
        val label = when {
            edge > TIMING_EDGE_POSITIVE_THRESHOLD_PCT -> BacktestTimingEdgeLabel.POSITIVE_EDGE
            edge < TIMING_EDGE_NEGATIVE_THRESHOLD_PCT -> BacktestTimingEdgeLabel.NEGATIVE_EDGE
            else -> BacktestTimingEdgeLabel.NEUTRAL
        }
        return BacktestTimingEdge(
            timingEdgePct = edge,
            label = label,
            noSignalsInPeriod = noSignals,
        )
    }

    // ------------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------------

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        if (n == 0) return 0.0
        return if (n % 2 == 1) sorted[n / 2]
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    companion object {
        /** Soglie verbatim TSK-347 §"Campo timingEdge". */
        const val TIMING_EDGE_POSITIVE_THRESHOLD_PCT: Double = 2.0
        const val TIMING_EDGE_NEGATIVE_THRESHOLD_PCT: Double = -2.0
    }
}
