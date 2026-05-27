package com.valueinvesting.webapp.llm

// Thrown by [LlmBudgetGuard] when the admin has frozen LLM traffic (ADR-019 §4).
//
// Not part of the [LlmException] sealed hierarchy — the freeze is a precondition
// check ahead of the Resilience4j chain, so we do NOT want it to be counted as
// a circuit-breaker failure or retried.
//
// Maps to HTTP 503 `LLM_FROZEN_BY_ADMIN` via [com.valueinvesting.webapp.api.error.GlobalExceptionHandler].
//
// [^src: design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md §4]
class LlmFrozenException : RuntimeException("LLM traffic is frozen by admin")
