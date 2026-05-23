package com.valueinvesting.webapp.ruleengine.calculators

// `intrinsicValue` is the **per-share** fair value (USD/share), coherent with
// `currentPrice` semantics used by MarginOfSafetyEvaluator. `intrinsicValueTotal`
// retains the aggregate enterprise valuation for audit. `sharesUsed` records
// which weighted-average share count was divided through (diluted preferred,
// basic fallback) so downstream rationale can be transparent.
// [^src: management/kanban/EP-007-hardening-produzione/US-052-dcf-fair-value-per-share/US-052.md]
data class DcfResult(
    val intrinsicValue: Double?,
    val intrinsicValueTotal: Double? = null,
    val sharesUsed: Double? = null,
    val method: DcfMethod,
    val rationale: String,
)
