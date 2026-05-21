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

// JPA entity mapping `fmp_profile_snapshot` (V003).  Denormalized price/market_cap
// columns are kept alongside the raw JSONB payload for fast screener queries.
// [^src: design_&_architecture/data/er-diagram.md §fmp_profile_snapshot]
// [^src: design_&_architecture/decisions/ADR-004-fmp-integration.md §Cache layer 24h]
@Entity
@Table(name = "fmp_profile_snapshot")
data class FmpProfileSnapshot(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String = "",

    @Column(name = "price", precision = 18, scale = 4)
    var price: BigDecimal? = null,

    @Column(name = "market_cap", precision = 20, scale = 2)
    var marketCap: BigDecimal? = null,

    @Column(name = "sector", length = 80)
    var sector: String? = null,

    @Column(name = "industry", length = 120)
    var industry: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    var rawPayload: String? = null,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.EPOCH,
)
