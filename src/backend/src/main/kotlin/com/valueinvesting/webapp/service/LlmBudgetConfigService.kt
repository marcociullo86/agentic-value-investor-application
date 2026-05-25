package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.persistence.entity.LlmBudgetConfigEntity
import com.valueinvesting.webapp.persistence.repository.LlmBudgetConfigRepository
import com.valueinvesting.webapp.persistence.repository.LlmCostCounterRepository
import com.valueinvesting.webapp.persistence.repository.LlmCallLogRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Service
class LlmBudgetConfigService(
    private val budgetConfigRepo: LlmBudgetConfigRepository,
    private val costCounterRepo: LlmCostCounterRepository,
    private val callLogRepo: LlmCallLogRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cachedCap: BigDecimal? = null

    @Volatile
    var frozen: Boolean = false
        internal set

    fun getMonthlyCapUsd(): BigDecimal {
        cachedCap?.let { return it }
        val config = budgetConfigRepo.findById(1).orElse(null)
        val cap = config?.monthlyCapUsd ?: BigDecimal("50.00")
        cachedCap = cap
        return cap
    }

    fun getCurrentMonthCost(): BigDecimal {
        val yearMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        return costCounterRepo.findById(yearMonth)
            .map { it.totalCostUsd }
            .orElse(BigDecimal.ZERO)
    }

    fun getUtilizationPercent(): Double {
        val cap = getMonthlyCapUsd()
        if (cap.compareTo(BigDecimal.ZERO) == 0) return 0.0
        return getCurrentMonthCost().divide(cap, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .toDouble()
    }

    @Transactional
    fun updateBudget(newCapUsd: BigDecimal, userId: Long?, reason: String?): BigDecimal {
        val config = budgetConfigRepo.findById(1).orElse(
            LlmBudgetConfigEntity(id = 1, monthlyCapUsd = BigDecimal("50.00"))
        )
        val oldCap = config.monthlyCapUsd
        if (oldCap.compareTo(newCapUsd) == 0) {
            return oldCap
        }

        config.monthlyCapUsd = newCapUsd
        config.updatedAt = Instant.now()
        config.updatedBy = userId
        budgetConfigRepo.save(config)

        cachedCap = newCapUsd
        eventPublisher.publishEvent(BudgetCapChangedEvent(oldCap, newCapUsd, reason))

        log.info("LLM budget cap changed: {} → {} by user {}", oldCap, newCapUsd, userId)
        return newCapUsd
    }

    fun freeze() {
        frozen = true
        log.warn("LLM budget FROZEN by admin")
    }

    fun unfreeze() {
        frozen = false
        log.info("LLM budget UNFROZEN by admin")
    }

    fun invalidateCache() {
        cachedCap = null
    }
}

data class BudgetCapChangedEvent(
    val oldCapUsd: BigDecimal,
    val newCapUsd: BigDecimal,
    val reason: String?,
)
