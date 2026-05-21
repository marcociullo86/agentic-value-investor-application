package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.DcfOverrideRequest
import com.valueinvesting.webapp.api.model.DcfOverrideResponse
import com.valueinvesting.webapp.persistence.entity.DcfMethodOverrideEntity
import com.valueinvesting.webapp.persistence.repository.DcfMethodOverrideRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DcfOverrideService(
    private val repository: DcfMethodOverrideRepository,
) {

    @Transactional
    fun upsert(userId: UUID, request: DcfOverrideRequest): DcfOverrideResponse {
        val ticker = request.ticker.uppercase()
        val existing = repository.findByUserIdAndTicker(userId, ticker)
        val entity = if (existing != null) {
            existing.forcedMethod = request.forcedMethod
            existing
        } else {
            DcfMethodOverrideEntity(
                userId = userId,
                ticker = ticker,
                forcedMethod = request.forcedMethod,
                createdAt = Instant.now(),
            )
        }
        val saved = repository.save(entity)
        return DcfOverrideResponse(
            ticker = saved.ticker,
            forcedMethod = saved.forcedMethod,
            createdAt = saved.createdAt,
        )
    }

    @Transactional
    fun delete(userId: UUID, ticker: String) {
        repository.deleteByUserIdAndTicker(userId, ticker.uppercase())
    }
}
