package com.valueinvesting.webapp.service.exception

import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod

class DcfMethodUnfeasibleException(
    val method: DcfMethod,
    val reason: String,
    val availableYears: Int?,
    val requiredYears: Int?,
) : RuntimeException(
    "DCF method ${method.name} not feasible: $reason (available=$availableYears, required=$requiredYears)",
)
