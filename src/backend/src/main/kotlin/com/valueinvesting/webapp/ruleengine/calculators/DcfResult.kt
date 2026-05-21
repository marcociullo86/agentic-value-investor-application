package com.valueinvesting.webapp.ruleengine.calculators

data class DcfResult(
    val intrinsicValue: Double?,
    val method: DcfMethod,
    val rationale: String,
)
