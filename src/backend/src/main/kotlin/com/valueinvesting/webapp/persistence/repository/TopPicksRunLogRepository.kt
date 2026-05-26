package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.TopPicksRunLogEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

// Repository per top_picks_run_log (US-048, TSK-136).
//
// `findTopByRunDateOrderByStartedAtDesc` — utile per dashboard "ultimo run di
// oggi" e per il cleanup job che vuole l'ultima esecuzione di una data.
// `findByStatusOrderByStartedAtDesc(status, pageable)` — alert/monitor su run
// FAILED/ABORTED.
//
// [^src: management/kanban/EP-012-batch-top-value-picks/US-049-persistenza-top-picks/TSK-136.md]
@Repository
interface TopPicksRunLogRepository : JpaRepository<TopPicksRunLogEntity, UUID> {

    fun findTopByRunDateOrderByStartedAtDesc(runDate: LocalDate): TopPicksRunLogEntity?

    fun findByStatusOrderByStartedAtDesc(status: String, pageable: Pageable): List<TopPicksRunLogEntity>

    fun findByRunDateOrderByStartedAtDesc(runDate: LocalDate): List<TopPicksRunLogEntity>
}
