package com.valueinvesting.webapp.llm

import java.math.BigDecimal
import java.math.RoundingMode

// Pure, stateless cost calculator for a single LLM call.
// Cost = (input_tokens × input_per_1k_usd + output_tokens × output_per_1k_usd) / 1000.
//
// Returned as BigDecimal scale 6 (matches `llm_call_log.cost_usd NUMERIC(10,6)`).
//
// [^src: design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md §2.2]
object LlmCostCalculator {

    private val THOUSAND = BigDecimal("1000")

    fun computeCostUsd(
        model: String,
        inputTokens: Int,
        outputTokens: Int,
        pricing: LlmPricingProperties,
    ): BigDecimal {
        val rates = pricing.pricingFor(model)
        val input = rates.inputPer1KUsd.multiply(BigDecimal(inputTokens))
        val output = rates.outputPer1KUsd.multiply(BigDecimal(outputTokens))
        return input.add(output)
            .divide(THOUSAND, 6, RoundingMode.HALF_UP)
    }
}
