package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

// JPA entity for the lazy-populated catalog of known tickers.
// PK is the ticker itself (uppercase, max 10) — see V002__create_stocks.sql.
// Populated by FmpCacheService when a profile is fetched for the first time
// (US-005 lazy population catalogo).
// [^src: design_&_architecture/data/er-diagram.md §stocks]
// [^src: design_&_architecture/components/backend-components.md §persistence]
@Entity
@Table(name = "stocks")
data class Stock(
    @Id
    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String = "",

    @Column(name = "company_name", length = 255)
    var companyName: String? = null,

    @Column(name = "sector", length = 80)
    var sector: String? = null,

    @Column(name = "industry", length = 120)
    var industry: String? = null,

    @Column(name = "market_cap_usd", precision = 20, scale = 2)
    var marketCapUsd: BigDecimal? = null,

    @Column(name = "last_refreshed_at")
    var lastRefreshedAt: Instant? = null,
)
