package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Component
import kotlin.math.abs

@Component
class GreenwaldMaintenanceCapexEstimator {

    data class Estimate(
        val ownerEarningsByYear: Map<Int, Double>,
        val ppeRatio: Double?,
        val usable: Boolean,
        val rationale: String,
    )

    fun estimate(dataset: FinancialDataset): Estimate {
        val slices = FinancialYearAligner.align(dataset)
        if (slices.size < 2) {
            return Estimate(
                ownerEarningsByYear = emptyMap(),
                ppeRatio = null,
                usable = false,
                rationale = "Meno di 2 anni allineati — Greenwald non applicabile.",
            )
        }

        val ratios = mutableListOf<Double>()
        for (slice in slices) {
            val revenue = slice.income?.revenue
            val ppe = slice.balance?.grossPpe ?: slice.balance?.propertyPlantEquipmentNet
            if (revenue != null && revenue > 0.0 && ppe != null && ppe >= 0.0) {
                ratios += ppe / revenue
            }
        }

        if (ratios.size < 3) {
            return Estimate(
                ownerEarningsByYear = emptyMap(),
                ppeRatio = null,
                usable = false,
                rationale = "PPE/Revenue insufficienti (${ratios.size} anni) — fallback FCF.",
            )
        }

        val ppeRatio = ratios.average()
        val ownerEarnings = linkedMapOf<Int, Double>()

        for (i in slices.indices) {
            val current = slices[i]
            val previous = slices.getOrNull(i + 1)
            val netIncome = current.income?.netIncome
            val da = current.cashFlow?.depreciationAndAmortization
                ?: current.income?.depreciationAndAmortization
            val totalCapex = absCapEx(current.cashFlow?.capitalExpenditure)
            val revenue = current.income?.revenue

            if (netIncome == null || da == null || totalCapex == null || revenue == null) {
                continue
            }

            val deltaRevenue = if (previous?.income?.revenue != null) {
                revenue - previous.income.revenue
            } else {
                0.0
            }
            val growthCapex = if (deltaRevenue <= 0.0) 0.0 else ppeRatio * deltaRevenue
            val maintenanceCapex = (totalCapex - growthCapex).coerceAtLeast(0.0)
            ownerEarnings[current.calendarYear] = netIncome + da - maintenanceCapex
        }

        if (ownerEarnings.size < MIN_HISTORICAL_YEARS) {
            return Estimate(
                ownerEarningsByYear = ownerEarnings,
                ppeRatio = ppeRatio,
                usable = false,
                rationale = "Owner Earnings calcolabili su ${ownerEarnings.size} anni (< $MIN_HISTORICAL_YEARS).",
            )
        }

        return Estimate(
            ownerEarningsByYear = ownerEarnings,
            ppeRatio = ppeRatio,
            usable = true,
            rationale = "Greenwald: PPE/Revenue medio=${"%.4f".format(ppeRatio)}, ${ownerEarnings.size} anni OE.",
        )
    }

    private fun absCapEx(value: Double?): Double? = value?.let { abs(it) }

    companion object {
        const val MIN_HISTORICAL_YEARS = 5
    }
}
