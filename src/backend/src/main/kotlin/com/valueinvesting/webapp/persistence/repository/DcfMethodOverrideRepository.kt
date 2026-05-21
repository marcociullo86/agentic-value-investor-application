package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.DcfMethodOverrideEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DcfMethodOverrideRepository : JpaRepository<DcfMethodOverrideEntity, UUID> {
    fun findByUserIdAndTicker(userId: UUID, ticker: String): DcfMethodOverrideEntity?
    fun deleteByUserIdAndTicker(userId: UUID, ticker: String): Long
}
