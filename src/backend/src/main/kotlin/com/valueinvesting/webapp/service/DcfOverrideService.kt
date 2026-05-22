package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.DcfOverrideRequest
import com.valueinvesting.webapp.api.model.DcfOverrideResponse
import com.valueinvesting.webapp.persistence.entity.DcfMethodOverrideEntity
import com.valueinvesting.webapp.persistence.repository.DcfMethodOverrideRepository
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.ruleengine.feasibility.DcfFeasibilityCheck
import com.valueinvesting.webapp.service.exception.DcfMethodUnfeasibleException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DcfOverrideService(
    private val repository: DcfMethodOverrideRepository,
    private val dcfFeasibilityCheck: DcfFeasibilityCheck,
) {

    fun findByUserAndTicker(userId: UUID, ticker: String): DcfOverrideResponse? {
        val normalized = ticker.uppercase()
        val entity = repository.findByUserIdAndTicker(userId, normalized) ?: return null
        return toResponse(entity)
    }

    @Transactional
    fun upsertWithFeasibilityCheck(userId: UUID, request: DcfOverrideRequest): DcfOverrideResponse {
        val method = DcfMethod.valueOf(request.forcedMethod)
        val ticker = request.ticker.uppercase()
        val check = dcfFeasibilityCheck.canApply(ticker, method)
        if (!check.feasible) {
            throw DcfMethodUnfeasibleException(
                method = method,
                reason = check.reason ?: "UNKNOWN",
                availableYears = check.availableYears,
                requiredYears = check.requiredYears,
            )
        }
        return upsert(userId, request)
    }

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
        return toResponse(saved)
    }

    @Transactional
    fun delete(userId: UUID, ticker: String) {
        repository.deleteByUserIdAndTicker(userId, ticker.uppercase())
    }

    private fun toResponse(entity: DcfMethodOverrideEntity): DcfOverrideResponse =
        DcfOverrideResponse(
            ticker = entity.ticker,
            forcedMethod = entity.forcedMethod,
            createdAt = entity.createdAt,
        )
}
