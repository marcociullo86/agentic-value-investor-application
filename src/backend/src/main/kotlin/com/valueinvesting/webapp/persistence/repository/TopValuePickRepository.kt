package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.TopValuePickEntity
import com.valueinvesting.webapp.persistence.entity.TopValuePickId
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

// Repository per top_value_picks (US-049, TSK-136).
//
// Method names:
//  - findByRunDateOrderByRankPositionAsc — listing del run-date richiesto
//    (consumer principale: TopPicksController endpoint GET /api/top-picks).
//  - findByRunDateAndVerdettoClasseInOrderByRankPositionAsc — filtro verdetto
//    per il filtro UI (es. mostrare solo APPROVATO + APPROVATO_PANIC_BUY).
//  - findDistinctRunDates(Pageable) — dropdown date disponibili (cap via
//    PageRequest.of(0, N) lato controller).
//  - deleteOlderThan(cutoff) — retention rolling 90gg (TSK-137 cleanup job).
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-049-persistenza-top-picks/TSK-136.md]
@Repository
interface TopValuePickRepository : JpaRepository<TopValuePickEntity, TopValuePickId> {

    fun findByRunDateOrderByRankPositionAsc(runDate: LocalDate): List<TopValuePickEntity>

    fun findByRunDateAndVerdettoClasseInOrderByRankPositionAsc(
        runDate: LocalDate,
        verdettoClasses: List<String>,
    ): List<TopValuePickEntity>

    @Query("SELECT DISTINCT t.runDate FROM TopValuePickEntity t ORDER BY t.runDate DESC")
    fun findDistinctRunDates(pageable: Pageable): List<LocalDate>

    @Modifying
    @Query("DELETE FROM TopValuePickEntity t WHERE t.runDate < :cutoff")
    fun deleteOlderThan(cutoff: LocalDate): Int
}
