package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.ruleengine.feasibility.FeasibilityResult
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

    fun isFeasible(dataset: FinancialDataset): FeasibilityResult {
        val fcfYears = countNonNullFcfYears(dataset)
        val required = MIN_FCF_YEARS
        return if (fcfYears >= required) {
            FeasibilityResult(true, null, fcfYears, required)
        } else {
            FeasibilityResult(
                feasible = false,
                reason = "FCF_HISTORY_INSUFFICIENT",
                availableYears = fcfYears,
                requiredYears = required,
            )
        }
    }

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

    private fun countNonNullFcfYears(dataset: FinancialDataset): Int {
        val slices = FinancialYearAligner.align(dataset)
        var years = 0
        for (slice in slices) {
            val cf = slice.cashFlow ?: continue
            val fcf = cf.freeCashFlow
                ?: run {
                    val ocf = cf.operatingCashFlow ?: cf.netCashProvidedByOperatingActivities
                    val capex = absCapEx(cf.capitalExpenditure)
                    if (ocf != null && capex != null) ocf - capex else null
                }
            if (fcf != null) {
                years++
            }
        }
        return years
    }

    companion object {
        const val MIN_FCF_YEARS = 1
    }
}
