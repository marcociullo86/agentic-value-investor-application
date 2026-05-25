package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "deep_analysis_report")
class DeepAnalysisReportEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "ticker", length = 20, nullable = false)
    var ticker: String = "",

    @Column(name = "filing_combo_hash", length = 64, nullable = false)
    var filingComboHash: String = "",

    @Column(name = "report_json", columnDefinition = "JSONB", nullable = false)
    var reportJson: String = "",

    @Column(name = "livello_rischio", length = 30, nullable = false)
    var livelloRischio: String = "",

    @Column(name = "generated_at", nullable = false)
    var generatedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now(),

    @Column(name = "llm_calls_count")
    var llmCallsCount: Int? = null,
)
