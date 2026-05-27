package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.persistence.entity.LlmBudgetConfigEntity
import com.valueinvesting.webapp.persistence.repository.LlmBudgetConfigRepository
import com.valueinvesting.webapp.persistence.repository.LlmCostCounterRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// ADR-019 §4.bis — admin-controlled runtime configuration for LLM budget.
//
// Responsibilities (post-TSK-156 wire):
//  - Read/update the singleton `llm_budget_config` row (monthly cap), with an
//    in-memory cache invalidated on update + budget-cap-change event.
//  - Read the current month cost from `llm_cost_counter` (read-only; writes
//    happen in [com.valueinvesting.webapp.llm.LlmCostCounterService]).
//  - Track admin freeze flag consumed by [com.valueinvesting.webapp.llm.LlmBudgetGuard].
@Service
class LlmBudgetConfigService(
    private val budgetConfigRepo: LlmBudgetConfigRepository,
    private val costCounterRepo: LlmCostCounterRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cachedCap: BigDecimal? = null

    @Volatile
    private var _frozen: Boolean = false
    val frozen: Boolean get() = _frozen

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
    fun updateBudget(newCapUsd: BigDecimal, userId: java.util.UUID?, reason: String?): BigDecimal {
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
        _frozen = true
        log.warn("LLM budget FROZEN by admin")
    }

    fun unfreeze() {
        _frozen = false
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
