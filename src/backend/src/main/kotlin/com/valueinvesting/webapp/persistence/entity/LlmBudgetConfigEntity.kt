package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "llm_budget_config")
class LlmBudgetConfigEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: Short = 1,

    @Column(name = "monthly_cap_usd", precision = 10, scale = 2, nullable = false)
    var monthlyCapUsd: BigDecimal = BigDecimal("50.00"),

    @Column(name = "alert_threshold_percent", nullable = false)
    var alertThresholdPercent: Short = 80,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by")
    var updatedBy: Long? = null,
)
