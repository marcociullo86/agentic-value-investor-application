package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component
import kotlin.math.abs

@Component
class FcfFallbackEstimator {

    data class Estimate(
        val cashFlowsByYear: Map<Int, Double>,
        val usable: Boolean,
        val rationale: String,
    )

    fun estimate(dataset: FinancialDataset): Estimate {
        val slices = FinancialYearAligner.align(dataset)
        val flows = linkedMapOf<Int, Double>()

        for (slice in slices) {
            val cf = slice.cashFlow ?: continue
            val fcf = cf.freeCashFlow
                ?: run {
                    val ocf = cf.operatingCashFlow ?: cf.netCashProvidedByOperatingActivities
                    val capex = absCapEx(cf.capitalExpenditure)
                    if (ocf != null && capex != null) ocf - capex else null
                }
            if (fcf != null) {
                flows[slice.calendarYear] = fcf
            }
        }

        val usable = flows.size >= GreenwaldMaintenanceCapexEstimator.MIN_HISTORICAL_YEARS
        return Estimate(
            cashFlowsByYear = flows,
            usable = usable,
            rationale = if (usable) {
                "FCF fallback: ${flows.size} anni di flussi."
            } else {
                "FCF fallback: solo ${flows.size} anni (< ${GreenwaldMaintenanceCapexEstimator.MIN_HISTORICAL_YEARS})."
            },
        )
    }

    private fun absCapEx(value: Double?): Double? = value?.let { abs(it) }
}
