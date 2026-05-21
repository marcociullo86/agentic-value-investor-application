package com.valueinvesting.webapp.ruleengine

// Discrete outcome of a single rule evaluation against a FinancialDataset.
// Closed set: any rule MUST return exactly one of these.
// [^src: design_&_architecture/components/backend-components.md §ruleengine]
// [^src: design_&_architecture/decisions/ADR-005-rule-engine-design.md §Strategy pattern]
// [^src: wiki/concepts/value-investing-rule-engine.md §Output del Rule Engine]
enum class Signal {
    /** Threshold met / strongly positive. */
    GREEN,

    /** Borderline / mixed / partial. */
    YELLOW,

    /** Threshold violated / negative. */
    RED,

    /** Insufficient data (e.g. < 5y history) — different from RED. US-007 AC. */
    INDETERMINATE,

    /** Rule does not apply (no usable inputs at all, e.g. empty dataset). */
    NOT_CALCULABLE,
}
