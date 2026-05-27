package com.valueinvesting.webapp.llm

import com.valueinvesting.webapp.service.LlmBudgetConfigService
import org.springframework.stereotype.Service

// Pre-call guard for the LLM Resilience4j chain (ADR-019 §6).
//
// Currently enforces the *freeze* switch only: budget cap overrun is alert-only
// (no automatic kill-switch — see ADR-019 §"Decision Drivers" 1 + "Opzione A").
// The guard runs BEFORE the Resilience4j chain so a frozen state short-circuits
// without consuming retry budgets or moving the circuit-breaker.
//
// [^src: design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md §4,§6]
@Service
class LlmBudgetGuard(
    private val budgetConfigService: LlmBudgetConfigService,
) {

    /**
     * Throws [LlmFrozenException] when LLM traffic is frozen by an admin.
     * Returns silently otherwise — callers proceed to invoke the LLM client.
     */
    fun checkOrThrow() {
        if (budgetConfigService.frozen) {
            throw LlmFrozenException()
        }
    }
}
