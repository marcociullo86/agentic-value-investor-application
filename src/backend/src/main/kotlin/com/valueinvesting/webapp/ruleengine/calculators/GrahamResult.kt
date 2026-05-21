package com.valueinvesting.webapp.ruleengine.calculators

// Result of the Graham Number computation.
//
// Shape:
// - `value`     : computed Graham Number, or null when not applicable.
// - `applicable`: true when both EPS and BVPS were usable (> 0); false otherwise.
//                 NEVER set `applicable = true` together with a null `value`, and vice versa.
// - `rationale` : human-readable explanation (Italian, matching the RuleSignal conventions
//                 already in use across the rule engine).
//
// Design notes:
// - This is a scalar-valued result, NOT a RuleSignal, because Graham Number is an
//   intrinsic-value aggregate rather than a Signal (GREEN/YELLOW/RED) outcome.
//   The Rule Engine signal-based contract is intentionally untouched (Opzione B).
// - Persistence into `rule_engine_result.graham_number` happens downstream (TSK-019).
// [^src: management/kanban/EP-004-valore-intrinseco-margin-of-safety/US-011-graham-number/TSK-016.md §Scope tecnico]
// [^src: wiki/concepts/graham-number.md]
data class GrahamResult(
    val value: Double?,
    val applicable: Boolean,
    val rationale: String,
)
