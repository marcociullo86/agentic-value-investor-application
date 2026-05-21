package com.valueinvesting.webapp.ruleengine

// Immutable result of a single rule evaluation.
// `observedValue` is the numeric metric the rule computed (e.g. 10y avg ROE),
// nullable when the rule could not compute it (INDETERMINATE / NOT_CALCULABLE).
// `threshold` is a human-readable string (e.g. "> 15%") — display-oriented,
// stable across locales since we never localize the numeric form here.
// [^src: management/kanban/EP-003-rule-engine-quantitativo/US-007-regola-redditivita/TSK-012.md §Scope tecnico]
data class RuleSignal(
    val ruleId: String,
    val signal: Signal,
    val observedValue: Double?,
    val threshold: String,
    val rationale: String,
)
