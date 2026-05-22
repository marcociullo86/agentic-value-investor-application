package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.MoatChecklistEntryRequest
import com.valueinvesting.webapp.api.model.MoatChecklistEntryResponse
import com.valueinvesting.webapp.api.model.MoatChecklistResponse
import com.valueinvesting.webapp.api.model.MoatType
import com.valueinvesting.webapp.persistence.entity.MoatChecklistEntry
import com.valueinvesting.webapp.persistence.entity.Stock
import com.valueinvesting.webapp.persistence.repository.MoatChecklistRepository
import com.valueinvesting.webapp.persistence.repository.StockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Moat checklist orchestration (TSK-026, US-016).
 *
 * GET returns the 4 moat types — entries without a persisted row return with
 * `status = null` (so the FE can show all 4 categories before the user has
 * filled anything in). POST upserts a single entry; the qualitative state is
 * intentionally NOT propagated to RuleEngineResult (US-016 AC).
 *
 * [^src: design_&_architecture/components/backend-components.md §MoatChecklistService]
 * [^src: management/kanban/.../US-016.md §AC §Business Rules]
 */
@Service
class MoatChecklistService(
    private val repository: MoatChecklistRepository,
    private val stockRepository: StockRepository,
    private val clock: Clock,
) {

    @Transactional
    fun getChecklist(userId: UUID, ticker: String): MoatChecklistResponse {
        val normalized = ticker.uppercase()
        val persisted = repository
            .findByUserIdAndTicker(userId, normalized)
            .associateBy { it.moatType }
        val entries = MoatType.ALL.map { type ->
            val row = persisted[type]
            MoatChecklistEntryResponse(
                moatType = type,
                status = row?.status,
                note = row?.note,
                updatedAt = row?.updatedAt,
            )
        }
        return MoatChecklistResponse(ticker = normalized, entries = entries)
    }

    @Transactional
    fun upsertEntry(
        userId: UUID,
        ticker: String,
        request: MoatChecklistEntryRequest,
    ): MoatChecklistEntryResponse {
        val normalized = ticker.uppercase()
        ensureStockExists(normalized)
        val now = Instant.now(clock)
        val existing = repository.findByUserIdAndTickerAndMoatType(
            userId,
            normalized,
            request.moatType,
        )
        val saved = if (existing != null) {
            existing.status = request.status
            existing.note = request.note
            existing.updatedAt = now
            repository.save(existing)
        } else {
            repository.save(
                MoatChecklistEntry(
                    userId = userId,
                    ticker = normalized,
                    moatType = request.moatType,
                    status = request.status,
                    note = request.note,
                    updatedAt = now,
                ),
            )
        }
        return MoatChecklistEntryResponse(
            moatType = saved.moatType,
            status = saved.status,
            note = saved.note,
            updatedAt = saved.updatedAt,
        )
    }

    private fun ensureStockExists(ticker: String) {
        if (!stockRepository.existsById(ticker)) {
            stockRepository.save(Stock(ticker = ticker))
        }
    }
}
