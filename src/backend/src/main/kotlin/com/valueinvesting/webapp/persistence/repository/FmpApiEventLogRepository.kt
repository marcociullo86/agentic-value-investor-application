package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.FmpApiEventLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

// Append-only repository for fmp_api_event_log.  Lookups (dashboard "ultimi N
// per tipo", retention/cleanup) are served by index fmp_api_event_log_type_time_idx
// (event_type, occurred_at DESC) — derived query name reflects that order.
// [^src: design_&_architecture/data/er-diagram.md §fmp_api_event_log] (Indice)
// [^src: src/backend/src/main/resources/db/migration/V005__create_fmp_api_event_log.sql]
@Repository
interface FmpApiEventLogRepository : JpaRepository<FmpApiEventLog, Long> {

    fun findFirst20ByEventTypeOrderByOccurredAtDesc(eventType: String): List<FmpApiEventLog>

    fun countByEventType(eventType: String): Long
}
