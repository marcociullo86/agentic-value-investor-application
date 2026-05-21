package com.valueinvesting.webapp.ruleengine

import com.valueinvesting.webapp.service.FinancialDataset

// Strategy contract for every value-investing rule.
// Spring discovers all @Component implementations and injects them into
// RuleEngineService as List<ValuationRule>. New rules (TSK-013..016) just
// need to declare a @Component implementing this interface — no central
// registry edit required.
// [^src: design_&_architecture/components/backend-components.md §Strategy pattern]
// [^src: design_&_architecture/decisions/ADR-005-rule-engine-design.md]
interface ValuationRule {
    /** Stable machine identifier (e.g. "ROE_10Y_AVG"). Drives ordering + persistence. */
    val ruleId: String

    /** Evaluate the rule against the dataset. MUST be pure and side-effect free. */
    fun evaluate(dataset: FinancialDataset): RuleSignal
}
