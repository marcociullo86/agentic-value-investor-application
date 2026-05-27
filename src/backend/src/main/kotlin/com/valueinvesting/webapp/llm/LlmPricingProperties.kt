package com.valueinvesting.webapp.llm

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

// Typed pricing configuration for LLM cost telemetry (ADR-019 §1).
//
// Pricing is expressed in USD per 1K tokens to match the Anthropic/Google
// public pricing pages (`$15/1M` Opus input → `0.015/1K`).
//
// Defaults track ADR-019 §1 maggio 2026 pricing snapshot. Override via env
// vars `LLM_COST_INPUT_PER_1K_USD_OPUS` etc. (see application.yml).
//
// [^src: design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md §1]
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-055-llm-budget-admin-config/TSK-156.md]
@ConfigurationProperties(prefix = "llm.budget.cost")
data class LlmPricingProperties(
    val opus: ModelPricing = ModelPricing(
        inputPer1KUsd = BigDecimal("0.015"),
        outputPer1KUsd = BigDecimal("0.075"),
    ),
    val geminiFlash: ModelPricing = ModelPricing(
        inputPer1KUsd = BigDecimal("0.0003"),
        outputPer1KUsd = BigDecimal("0.0025"),
    ),
) {
    data class ModelPricing(
        val inputPer1KUsd: BigDecimal,
        val outputPer1KUsd: BigDecimal,
    )

    /** Routes a model identifier to its pricing entry; defaults to Opus pricing. */
    fun pricingFor(model: String): ModelPricing = when {
        model.contains("gemini", ignoreCase = true) -> geminiFlash
        else -> opus
    }
}
