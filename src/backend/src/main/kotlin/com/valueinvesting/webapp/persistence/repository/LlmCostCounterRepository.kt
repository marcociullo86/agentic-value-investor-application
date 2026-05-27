package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.LlmCostCounterEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface LlmCostCounterRepository : JpaRepository<LlmCostCounterEntity, String> {

    // Atomic UPSERT for the per-month aggregate row (ADR-019 §2.1).
    // Native Postgres INSERT ... ON CONFLICT keeps the operation single-statement
    // so two concurrent calls cannot double-count or step on each other.
    @Modifying
    @Query(
        value = """
            INSERT INTO llm_cost_counter (
                year_month, total_cost_usd, total_calls,
                total_tokens_in, total_tokens_out, cache_hits, last_updated
            ) VALUES (
                :yearMonth, :costDelta, 1,
                :inputTokens, :outputTokens,
                CASE WHEN :cacheHit THEN 1 ELSE 0 END,
                now()
            )
            ON CONFLICT (year_month) DO UPDATE SET
                total_cost_usd   = llm_cost_counter.total_cost_usd   + EXCLUDED.total_cost_usd,
                total_calls      = llm_cost_counter.total_calls      + 1,
                total_tokens_in  = llm_cost_counter.total_tokens_in  + EXCLUDED.total_tokens_in,
                total_tokens_out = llm_cost_counter.total_tokens_out + EXCLUDED.total_tokens_out,
                cache_hits       = llm_cost_counter.cache_hits       + EXCLUDED.cache_hits,
                last_updated     = now()
        """,
        nativeQuery = true,
    )
    fun upsertCounter(
        @Param("yearMonth") yearMonth: String,
        @Param("costDelta") costDelta: BigDecimal,
        @Param("inputTokens") inputTokens: Long,
        @Param("outputTokens") outputTokens: Long,
        @Param("cacheHit") cacheHit: Boolean,
    )
}
