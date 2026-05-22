package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "rule_engine_result")
data class RuleEngineResultEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String,

    @Column(name = "evaluated_at", nullable = false)
    var evaluatedAt: Instant = Instant.now(),

    // JSONB column: without @JdbcTypeCode(SqlTypes.JSON) Hibernate binds the
    // value as VARCHAR and Postgres rejects with
    //   ERROR: column "signals" is of type jsonb but expression is of type character varying
    // Pattern mirrors FmpFinancialSnapshot.payload (V003) — String storage,
    // (de)serialization handled by the service layer via Jackson.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signals", nullable = false, columnDefinition = "jsonb")
    var signalsJson: String,

    @Column(name = "graham_number", precision = 18, scale = 4)
    var grahamNumber: BigDecimal? = null,

    @Column(name = "dcf_intrinsic_value", precision = 18, scale = 4)
    var dcfIntrinsicValue: BigDecimal? = null,

    @Column(name = "dcf_method", length = 32)
    var dcfMethod: String? = null,

    @Column(name = "mos_signal", length = 32, nullable = false)
    var mosSignal: String,

    @Column(name = "current_price_at_eval", precision = 18, scale = 4)
    var currentPriceAtEval: BigDecimal? = null,

    @Column(name = "source_snapshot_fetched_at")
    var sourceSnapshotFetchedAt: Instant? = null,
)
