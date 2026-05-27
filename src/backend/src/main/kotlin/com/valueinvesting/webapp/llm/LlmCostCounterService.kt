package com.valueinvesting.webapp.llm

import com.valueinvesting.webapp.persistence.entity.LlmCallLogEntity
import com.valueinvesting.webapp.persistence.repository.LlmCallLogRepository
import com.valueinvesting.webapp.persistence.repository.LlmCostCounterRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

// Post-call telemetry sink for every LLM completion (ADR-019 §2).
//
// Records a row in `llm_call_log` (per-call granularity) and atomically UPSERTs
// the monthly aggregate row in `llm_cost_counter`. The single-statement UPSERT
// keeps concurrent callers safe without explicit locking.
//
// Failures here are logged but never rethrown: a telemetry hiccup must NEVER
// break a paying LLM request. The Anthropic response has already been delivered
// when this service runs.
//
// [^src: design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md §2.1,§2.2]
// [^src: code_quality/reports/TSK-156-iter-1.md §Finding 2]
@Service
class LlmCostCounterService(
    private val callLogRepository: LlmCallLogRepository,
    private val counterRepository: LlmCostCounterRepository,
    private val pricing: LlmPricingProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Records a successful (or cached) LLM call into telemetry tables.
     */
    @Transactional
    fun recordCall(
        model: String,
        endpoint: String? = null,
        purpose: String? = null,
        inputTokens: Int,
        outputTokens: Int,
        latencyMs: Int,
        ticker: String? = null,
        userId: UUID? = null,
        requestId: UUID? = null,
        errorCode: String? = null,
        cacheHit: Boolean = false,
    ) {
        val costUsd = if (cacheHit) {
            BigDecimal.ZERO
        } else {
            LlmCostCalculator.computeCostUsd(model, inputTokens, outputTokens, pricing)
        }

        try {
            callLogRepository.save(
                LlmCallLogEntity(
                    createdAt = Instant.now(),
                    endpoint = endpoint,
                    purpose = purpose,
                    ticker = ticker,
                    userId = userId,
                    requestId = requestId,
                    model = model,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    costUsd = costUsd,
                    cacheHit = cacheHit,
                    errorCode = errorCode,
                    latencyMs = latencyMs,
                ),
            )

            counterRepository.upsertCounter(
                yearMonth = currentYearMonth(),
                costDelta = costUsd,
                inputTokens = inputTokens.toLong(),
                outputTokens = outputTokens.toLong(),
                cacheHit = cacheHit,
            )
        } catch (ex: Exception) {
            log.warn(
                "Failed to persist LLM telemetry (model={}, endpoint={}, tokens={}+{}): {}",
                model, endpoint, inputTokens, outputTokens, ex.message,
            )
        }
    }

    private fun currentYearMonth(): String =
        YearMonth.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM"))
}
