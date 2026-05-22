package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.Watchlist
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WatchlistRepository : JpaRepository<Watchlist, UUID> {
    fun findByUserIdAndIsDefaultTrue(userId: UUID): Watchlist?
}
