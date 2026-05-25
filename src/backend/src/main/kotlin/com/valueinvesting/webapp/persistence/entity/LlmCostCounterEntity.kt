package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "llm_cost_counter")
class LlmCostCounterEntity(
    @Id
    @Column(name = "year_month", columnDefinition = "VARCHAR(7)", nullable = false)
    var yearMonth: String = "",

    @Column(name = "total_cost_usd", precision = 10, scale = 4, nullable = false)
    var totalCostUsd: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_calls", nullable = false)
    var totalCalls: Long = 0,

    @Column(name = "total_tokens_in", nullable = false)
    var totalTokensIn: Long = 0,

    @Column(name = "total_tokens_out", nullable = false)
    var totalTokensOut: Long = 0,

    @Column(name = "cache_hits", nullable = false)
    var cacheHits: Long = 0,

    @Column(name = "alert_80_sent_at")
    var alert80SentAt: Instant? = null,

    @Column(name = "alert_100_sent_at")
    var alert100SentAt: Instant? = null,

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: Instant = Instant.now(),
)
