package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// JPA entity for fmp_api_event_log (V005, US-006).
// Append-only audit log; populated asynchronously by FmpEventLogger on every
// resilience event of interest (429, 5xx, circuit-open, fallback-stale,
// ticker-not-found).  No FK enforcement on ticker by the entity layer (column
// is nullable and the DB FK is to stocks(ticker) — events for unknown tickers
// are legitimate, the FK is satisfied by the lazy-population from FmpCacheService
// or null when no ticker context exists, e.g. circuit-breaker state transitions).
// [^src: design_&_architecture/data/er-diagram.md §fmp_api_event_log]
// [^src: design_&_architecture/decisions/ADR-008-observability-logging.md]
@Entity
@Table(name = "fmp_api_event_log")
class FmpApiEventLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Instant = Instant.EPOCH,

    @Column(name = "event_type", length = 40, nullable = false)
    var eventType: String = "",

    @Column(name = "ticker", length = 10)
    var ticker: String? = null,

    @Column(name = "endpoint", length = 40)
    var endpoint: String? = null,

    @Column(name = "http_status")
    var httpStatus: Int? = null,

    @Column(name = "detail")
    var detail: String? = null,
)
