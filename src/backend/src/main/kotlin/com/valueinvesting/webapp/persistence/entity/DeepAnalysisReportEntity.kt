package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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

    // JSONB column: senza @JdbcTypeCode(SqlTypes.JSON) Hibernate binda il valore
    // come VARCHAR e Postgres rigetta con
    //   ERROR: column "report_json" is of type jsonb but expression is of type character varying
    // Stesso pattern di RuleEngineResultEntity (V015) / FmpFinancialSnapshot (V003) —
    // storage String, (de)serializzazione Jackson lato service.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report_json", columnDefinition = "jsonb", nullable = false)
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
