package com.valueinvesting.webapp.ruleengine

import com.valueinvesting.webapp.service.FinancialDataset
import org.springframework.stereotype.Service

// Aggregates all ValuationRule strategies and runs them against a single dataset.
//
// Design notes:
// - Spring auto-collects every @Component : ValuationRule into the injected list.
//   Adding TSK-013/014/015/016 rules requires NO change to this class.
// - Output is sorted by ruleId for deterministic ordering (stable contract for
//   downstream serialization, snapshot tests, and the analysis endpoint of TSK-019).
// - This service is stateless and thread-safe.
// [^src: design_&_architecture/components/backend-components.md §ruleengine]
// [^src: design_&_architecture/decisions/ADR-005-rule-engine-design.md §Strategy pattern]
@Service
class RuleEngineService(
    private val rules: List<ValuationRule>,
) {
    fun evaluateAll(dataset: FinancialDataset): List<RuleSignal> =
        rules
            .map { it.evaluate(dataset) }
            .sortedBy { it.ruleId }
}
