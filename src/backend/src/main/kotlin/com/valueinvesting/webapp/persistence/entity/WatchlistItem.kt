package com.valueinvesting.webapp.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "watchlist_items")
data class WatchlistItem(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "watchlist_id", nullable = false)
    var watchlistId: UUID,

    @Column(name = "ticker", length = 10, nullable = false)
    var ticker: String,

    @Column(name = "added_at", nullable = false)
    var addedAt: Instant = Instant.now(),
)
