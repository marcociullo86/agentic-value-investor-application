package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

// JPA entity mapping `fmp_financial_snapshot` (V003).
// `payload` JSONB is stored as raw JSON string and (de)serialized by the service layer
// via Jackson — see FmpCacheService.  Rationale: keeps entity Spring-Data-friendly
// without pulling in hibernate-types-jackson (extra dep + Hibernate 6 compat caveats);
// SqlTypes.JSON on a String column is the idiomatic Hibernate 6.x path for JSONB.
// [^src: design_&_architecture/data/er-diagram.md §fmp_financial_snapshot]
// [^src: design_&_architecture/decisions/ADR-003-database-postgresql.md] (JSONB rationale)
@Entity
@Table(name = "fmp_financial_snapshot")
data class FmpFinancialSnapshot(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String = "",

    @Column(name = "endpoint", length = 40, nullable = false)
    var endpoint: String = "",

    // JSONB column — held as serialized JSON string. Conversion is performed by
    // FmpCacheService using the application ObjectMapper so the FMP DTOs stay
    // pure data classes (no JPA coupling).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    var payload: String = "[]",

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.EPOCH,

    @Column(name = "is_stale", nullable = false)
    var isStale: Boolean = false,

    @Column(name = "stale_reason")
    var staleReason: String? = null,
)
