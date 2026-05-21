package com.valueinvesting.webapp.ruleengine.calculators

enum class DcfMethod {
    GREENWALD,
    FCF_FALLBACK,
    NOT_APPLICABLE,
    ;

    fun toApiValue(): String = name
}
