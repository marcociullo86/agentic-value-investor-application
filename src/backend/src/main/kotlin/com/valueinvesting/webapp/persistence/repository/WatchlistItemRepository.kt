package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.WatchlistItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WatchlistItemRepository : JpaRepository<WatchlistItem, UUID> {
    fun findByWatchlistIdOrderByAddedAtDesc(watchlistId: UUID): List<WatchlistItem>
    fun findByWatchlistIdAndTicker(watchlistId: UUID, ticker: String): WatchlistItem?
    fun deleteByWatchlistIdAndTicker(watchlistId: UUID, ticker: String): Long
}
