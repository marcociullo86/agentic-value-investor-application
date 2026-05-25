package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "deep_analysis_event_log")
class DeepAnalysisEventLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "ticker", length = 20, nullable = false)
    var ticker: String = "",

    @Column(name = "generated_at", nullable = false)
    var generatedAt: Instant = Instant.now(),

    @Column(name = "cache_hits")
    var cacheHits: Int? = null,

    @Column(name = "llm_calls")
    var llmCalls: Int? = null,

    @Column(name = "total_duration_ms")
    var totalDurationMs: Long? = null,
)
