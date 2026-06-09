package com.valueinvesting.webapp.technicalanalysis

import com.valueinvesting.webapp.api.model.PositionSizing
import com.valueinvesting.webapp.api.model.PositionSizingWarning
import com.valueinvesting.webapp.api.model.RewardRiskLabel
import com.valueinvesting.webapp.api.model.RewardRiskRatio
import com.valueinvesting.webapp.api.model.SixPercentRule
import com.valueinvesting.webapp.api.model.StopSuggestion
import com.valueinvesting.webapp.api.model.StopType
import com.valueinvesting.webapp.api.model.TwoPercentRule
import org.springframework.stereotype.Component
import java.util.Locale
import kotlin.math.floor

private fun fmt(value: Double, fmt: String): String = String.format(Locale.ROOT, fmt, value)

// StopPlacementAdvisor + PositionSizingCalculator — EP-024 / US-100 / TSK-330.
//
// Pure-function: niente I/O, niente persistenza. L'`equity` ricevuta come input
// NON viene mai serializzata in DB (US-100 §"Separazione di responsabilita"):
// e' un parametro client-side per il calcolo del singolo trade.
//
// Stop suggestion — 3 candidati in priorita' (Murphy + Elder):
//   1) SUPPORT_BASED   sotto support[0] con buffer 0.5%.
//   2) SMA200_BASED    sotto SMA200 con buffer 0.5%, solo se price > SMA200.
//   3) ATR_BASED       currentPrice − 2×ATR14 (fallback).
//   Se nessuno applicabile → NOT_CALCULABLE con rationale esplicito.
//
// Position sizing — Elder §50 (2% Rule) + Elder §51 (6% Rule).
// Reward/Risk — vs DCF intrinsic value (EP-007), etichette EXCELLENT/...
//
// [^src: wiki/syntheses/ta-stop-placement-position-sizing.md] (Murphy §Page 82)
// [^src: wiki/concepts/elder-risk-management-2pct-6pct.md]
// [^src: wiki/concepts/trend-trendlines-support-resistance.md]
@Component
class StopPlacementAdvisor {

    fun suggestStop(input: StopInput): StopSuggestion {
        // 1) SUPPORT_BASED
        val nearestSupport = input.nearestSupport
        if (nearestSupport != null && nearestSupport > 0.0 && input.currentPrice > nearestSupport) {
            val stop = nearestSupport * (1.0 - BUFFER_PCT)
            return buildStop(
                type = StopType.SUPPORT_BASED,
                currentPrice = input.currentPrice,
                stopPrice = stop,
                anchorReference = "support@${fmt(nearestSupport, "%.2f")}" +
                    (input.nearestSupportLabel?.let { " ($it)" } ?: ""),
                rationale = "Stop ancorato al support strutturale piu' vicino con buffer " +
                    "${fmt(BUFFER_PCT * 100, "%.1f")}% sotto il livello (Murphy §Page 82).",
            )
        }
        // 2) SMA200_BASED
        val sma200 = input.sma200
        if (sma200 != null && sma200 > 0.0 && input.currentPrice > sma200) {
            val stop = sma200 * (1.0 - BUFFER_PCT)
            return buildStop(
                type = StopType.SMA200_BASED,
                currentPrice = input.currentPrice,
                stopPrice = stop,
                anchorReference = "SMA200@${fmt(sma200, "%.2f")}",
                rationale = "Nessun support strutturale utilizzabile sotto il prezzo: stop sotto SMA200 con buffer ${fmt(BUFFER_PCT * 100, "%.1f")}%.",
            )
        }
        // 3) ATR_BASED
        val atr = input.atr14
        if (atr != null && atr > 0.0) {
            val stop = input.currentPrice - ATR_MULTIPLIER * atr
            if (stop > 0.0) {
                return buildStop(
                    type = StopType.ATR_BASED,
                    currentPrice = input.currentPrice,
                    stopPrice = stop,
                    anchorReference = "ATR14=${fmt(atr, "%.2f")}",
                    rationale = "Stop volatility-based: currentPrice − ${ATR_MULTIPLIER.toInt()} × ATR14 (fallback Elder).",
                )
            }
        }
        // NOT_CALCULABLE
        return StopSuggestion(
            type = StopType.NOT_CALCULABLE,
            stopPrice = null,
            stopDistance = null,
            stopDistancePct = null,
            anchorReference = null,
            rationale = "Stop non calcolabile: nessun support sotto il prezzo, prezzo sotto SMA200, e ATR14 non disponibile.",
        )
    }

    fun computePositionSizing(
        equity: Double,
        currentPrice: Double,
        stopSuggestion: StopSuggestion,
    ): PositionSizing {
        require(equity > 0.0) { "equity must be > 0" }
        require(currentPrice > 0.0) { "currentPrice must be > 0" }

        val maxRisk = equity * TWO_PERCENT
        val stopDistance = stopSuggestion.stopDistance

        val sharesUncapped: Long = if (stopDistance != null && stopDistance > 0.0) {
            floor(maxRisk / stopDistance).toLong().coerceAtLeast(0L)
        } else 0L

        val maxSharesByEquity: Long = floor(equity / currentPrice).toLong().coerceAtLeast(0L)
        val warning: PositionSizingWarning? = if (sharesUncapped > maxSharesByEquity) {
            PositionSizingWarning.POSITION_EXCEEDS_EQUITY
        } else null
        val shares = if (warning != null) maxSharesByEquity else sharesUncapped

        val positionValue = shares * currentPrice
        val positionPctEquity = if (equity > 0.0) positionValue / equity else 0.0

        return PositionSizing(
            twoPercentRule = TwoPercentRule(
                equity = equity,
                maxRiskAllowed = maxRisk,
                stopDistance = stopDistance,
                sharesRecommended = shares,
                positionValueRecommended = positionValue,
                positionPctEquity = positionPctEquity,
                warning = warning,
            ),
            sixPercentRule = SixPercentRule(
                maxAggregateRiskPerMonth = equity * SIX_PERCENT,
                disclaimer = "Questo singolo trade impegna fino a ${fmt(maxRisk, "%.2f")} USD sul budget mensile di ${fmt(equity * SIX_PERCENT, "%.2f")} USD (6% Rule). Conferma di NON superare il 6% aggregato su tutti i trade aperti.",
            ),
        )
    }

    fun computeRewardRisk(
        currentPrice: Double,
        dcfIntrinsicValue: Double?,
        stopSuggestion: StopSuggestion,
    ): RewardRiskRatio {
        val stopDistance = stopSuggestion.stopDistance
        if (dcfIntrinsicValue == null || dcfIntrinsicValue <= currentPrice || stopDistance == null || stopDistance <= 0.0) {
            val reason = when {
                dcfIntrinsicValue == null -> "DCF intrinsic value non disponibile."
                dcfIntrinsicValue <= currentPrice -> "DCF intrinsic value (≤ prezzo corrente): nessun upside fondamentale residuo."
                stopDistance == null -> "Stop non calcolabile: rapporto reward/risk non valutabile."
                else -> "Distanza stop non positiva."
            }
            return RewardRiskRatio(
                upside = null,
                downside = null,
                value = null,
                label = RewardRiskLabel.NOT_APPLICABLE,
                rationale = reason,
            )
        }
        val upside = dcfIntrinsicValue - currentPrice
        val ratio = upside / stopDistance
        val label = when {
            ratio >= 3.0 -> RewardRiskLabel.EXCELLENT
            ratio >= 2.0 -> RewardRiskLabel.ACCEPTABLE
            ratio >= 1.0 -> RewardRiskLabel.MARGINAL
            else -> RewardRiskLabel.UNFAVORABLE
        }
        val rationale = when (label) {
            RewardRiskLabel.EXCELLENT -> "Eccellente: ${fmt(ratio, "%.1f")}:1 (≥ 3:1)."
            RewardRiskLabel.ACCEPTABLE -> "Accettabile: ${fmt(ratio, "%.1f")}:1 (Elder §54 raccomanda minimo 2:1)."
            RewardRiskLabel.MARGINAL -> "Marginale: ${fmt(ratio, "%.1f")}:1 — valutare se aspettare prezzo migliore."
            RewardRiskLabel.UNFAVORABLE -> "Sfavorevole: stop piu' lontano dell'upside fondamentale residuo."
            RewardRiskLabel.NOT_APPLICABLE -> ""
        }
        return RewardRiskRatio(
            upside = upside,
            downside = stopDistance,
            value = ratio,
            label = label,
            rationale = rationale,
        )
    }

    private fun buildStop(
        type: StopType,
        currentPrice: Double,
        stopPrice: Double,
        anchorReference: String,
        rationale: String,
    ): StopSuggestion {
        val distance = currentPrice - stopPrice
        val pct = if (currentPrice > 0.0) (distance / currentPrice) * 100.0 else 0.0
        return StopSuggestion(
            type = type,
            stopPrice = stopPrice,
            stopDistance = distance,
            stopDistancePct = pct,
            anchorReference = anchorReference,
            rationale = rationale,
        )
    }

    /**
     * Input minimi per il calcolo. `nearestSupportLabel` e' la stringa human-readable
     * del tipo di livello (es. "SWING_LOW", "RETRACEMENT_50") per l'`anchorReference`.
     */
    data class StopInput(
        val currentPrice: Double,
        val nearestSupport: Double?,
        val nearestSupportLabel: String?,
        val sma200: Double?,
        val atr14: Double?,
    )

    private companion object {
        const val BUFFER_PCT: Double = 0.005    // 0.5% sotto il livello
        const val ATR_MULTIPLIER: Double = 2.0  // currentPrice − 2 × ATR14
        const val TWO_PERCENT: Double = 0.02
        const val SIX_PERCENT: Double = 0.06
    }
}
