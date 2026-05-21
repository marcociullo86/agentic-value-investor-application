package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component

@Component
class DcfCalculator(
    private val greenwaldEstimator: GreenwaldMaintenanceCapexEstimator,
    private val fcfFallbackEstimator: FcfFallbackEstimator,
) {

    fun calculate(
        dataset: FinancialDataset,
        forcedMethod: DcfMethod? = null,
    ): DcfResult {
        val greenwald = greenwaldEstimator.estimate(dataset)
        val fcf = fcfFallbackEstimator.estimate(dataset)

        val method = when (forcedMethod) {
            DcfMethod.GREENWALD -> if (greenwald.usable) DcfMethod.GREENWALD else DcfMethod.FCF_FALLBACK
            DcfMethod.FCF_FALLBACK -> DcfMethod.FCF_FALLBACK
            null -> when {
                greenwald.usable -> DcfMethod.GREENWALD
                fcf.usable -> DcfMethod.FCF_FALLBACK
                else -> DcfMethod.NOT_APPLICABLE
            }
            DcfMethod.NOT_APPLICABLE -> DcfMethod.NOT_APPLICABLE
        }

        val historicalSize = when (method) {
            DcfMethod.GREENWALD -> greenwald.ownerEarningsByYear.size
            DcfMethod.FCF_FALLBACK -> fcf.cashFlowsByYear.size
            DcfMethod.NOT_APPLICABLE -> 0
        }

        if (method == DcfMethod.NOT_APPLICABLE || historicalSize < GreenwaldMaintenanceCapexEstimator.MIN_HISTORICAL_YEARS) {
            return DcfResult(
                intrinsicValue = null,
                method = DcfMethod.NOT_APPLICABLE,
                rationale = "${greenwald.rationale} | ${fcf.rationale}",
            )
        }

        val sortedHistorical = when (method) {
            DcfMethod.GREENWALD -> greenwald.ownerEarningsByYear.entries.sortedByDescending { it.key }.map { it.value }
            DcfMethod.FCF_FALLBACK -> fcf.cashFlowsByYear.entries.sortedByDescending { it.key }.map { it.value }
            else -> emptyList()
        }

        if (sortedHistorical.all { it <= 0.0 }) {
            return DcfResult(
                intrinsicValue = null,
                method = DcfMethod.NOT_APPLICABLE,
                rationale = "Flussi storici non positivi — DCF non calcolabile.",
            )
        }

        val growth = cappedGrowthRate(sortedHistorical)
        val intrinsic = discountedCashFlow(
            baseCashFlow = sortedHistorical.first(),
            growthRate = growth,
            discountRate = DISCOUNT_RATE,
            terminalGrowthRate = TERMINAL_GROWTH_RATE,
            projectionYears = PROJECTION_YEARS,
        )

        return DcfResult(
            intrinsicValue = intrinsic,
            method = method,
            rationale = "DCF($method): g=${"%.2f".format(growth * 100)}%, r=${"%.2f".format(DISCOUNT_RATE * 100)}%, TV g=${"%.2f".format(TERMINAL_GROWTH_RATE * 100)}%. ${greenwald.rationale}",
        )
    }

    private fun cappedGrowthRate(flowsNewestFirst: List<Double>): Double {
        if (flowsNewestFirst.size < 2) return MIN_GROWTH_RATE
        val growths = mutableListOf<Double>()
        for (i in 0 until flowsNewestFirst.size - 1) {
            val newer = flowsNewestFirst[i]
            val older = flowsNewestFirst[i + 1]
            if (older > 0.0 && newer > 0.0) {
                growths += (newer / older) - 1.0
            }
        }
        val raw = if (growths.isEmpty()) MIN_GROWTH_RATE else growths.average()
        return raw.coerceIn(MIN_GROWTH_RATE, MAX_GROWTH_RATE)
    }

    private fun discountedCashFlow(
        baseCashFlow: Double,
        growthRate: Double,
        discountRate: Double,
        terminalGrowthRate: Double,
        projectionYears: Int,
    ): Double {
        var pv = 0.0
        var flow = baseCashFlow
        for (year in 1..projectionYears) {
            flow *= (1.0 + growthRate)
            pv += flow / Math.pow(1.0 + discountRate, year.toDouble())
        }
        val terminalFlow = flow * (1.0 + terminalGrowthRate)
        val terminalValue = terminalFlow / (discountRate - terminalGrowthRate)
        pv += terminalValue / Math.pow(1.0 + discountRate, projectionYears.toDouble())
        return pv
    }

    companion object {
        const val MIN_GROWTH_RATE = 0.05
        const val MAX_GROWTH_RATE = 0.07
        const val DISCOUNT_RATE = 0.095
        const val TERMINAL_GROWTH_RATE = 0.025
        const val PROJECTION_YEARS = 10
    }
}
