package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.MoatChecklistEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MoatChecklistRepository : JpaRepository<MoatChecklistEntry, UUID> {
    fun findByUserIdAndTicker(userId: UUID, ticker: String): List<MoatChecklistEntry>
    fun findByUserIdAndTickerAndMoatType(
        userId: UUID,
        ticker: String,
        moatType: String,
    ): MoatChecklistEntry?
}
