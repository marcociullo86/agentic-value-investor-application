package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "price_action_snapshot")
class PriceActionSnapshotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "ticker", length = 20, nullable = false)
    var ticker: String = "",

    @Column(name = "calc_date", nullable = false)
    var calcDate: LocalDate = LocalDate.now(),

    @Column(name = "price_now", precision = 12, scale = 4)
    var priceNow: BigDecimal? = null,

    @Column(name = "max_52w", precision = 12, scale = 4)
    var max52w: BigDecimal? = null,

    @Column(name = "min_52w", precision = 12, scale = 4)
    var min52w: BigDecimal? = null,

    @Column(name = "drawdown_pct", precision = 8, scale = 4)
    var drawdownPct: BigDecimal? = null,

    @Column(name = "trend_3m_pct", precision = 8, scale = 4)
    var trend3mPct: BigDecimal? = null,

    @Column(name = "ma50", precision = 12, scale = 4)
    var ma50: BigDecimal? = null,

    @Column(name = "ma200", precision = 12, scale = 4)
    var ma200: BigDecimal? = null,

    @Column(name = "panic_discount")
    var panicDiscount: Boolean? = null,

    @Column(name = "deterioration_warning")
    var deteriorationWarning: Boolean? = null,

    @Column(name = "series_days")
    var seriesDays: Int? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
