package com.valueinvesting.webapp.ruleengine.feasibility

data class FeasibilityResult(
    val feasible: Boolean,
    val reason: String?,
    val availableYears: Int?,
    val requiredYears: Int?,
)
