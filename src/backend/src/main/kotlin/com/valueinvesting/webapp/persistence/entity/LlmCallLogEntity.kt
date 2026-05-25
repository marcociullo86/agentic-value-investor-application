package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "llm_call_log")
class LlmCallLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "endpoint", length = 64)
    var endpoint: String? = null,

    @Column(name = "purpose", length = 32)
    var purpose: String? = null,

    @Column(name = "ticker", length = 16)
    var ticker: String? = null,

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(name = "request_id")
    var requestId: UUID? = null,

    @Column(name = "model", length = 64)
    var model: String? = null,

    @Column(name = "input_tokens", nullable = false)
    var inputTokens: Int = 0,

    @Column(name = "output_tokens", nullable = false)
    var outputTokens: Int = 0,

    @Column(name = "cost_usd", precision = 10, scale = 6, nullable = false)
    var costUsd: BigDecimal = BigDecimal.ZERO,

    @Column(name = "cache_hit", nullable = false)
    var cacheHit: Boolean = false,

    @Column(name = "error_code", length = 32)
    var errorCode: String? = null,

    @Column(name = "latency_ms", nullable = false)
    var latencyMs: Int = 0,
)
