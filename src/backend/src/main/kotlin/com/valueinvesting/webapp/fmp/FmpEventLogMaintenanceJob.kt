package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.FmpEventLogProperties
import com.valueinvesting.webapp.persistence.repository.FmpApiEventLogRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

// Purges fmp_api_event_log rows older than retention (US-027 / TSK-064).
// [^src: design_&_architecture/operations/deploy-runbook-r11.md §Retention fmp_api_event_log]
@Component
class FmpEventLogMaintenanceJob(
    private val repository: FmpApiEventLogRepository,
    private val properties: FmpEventLogProperties,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${fmp.event-log.purge-cron:0 0 3 * * *}",
        zone = "\${fmp.event-log.purge-zone:UTC}",
    )
    @Transactional
    fun purgeExpiredRows() {
        val cutoff = Instant.now(clock).minus(properties.retentionDays, ChronoUnit.DAYS)
        val deleted = repository.deleteByOccurredAtBefore(cutoff)
        if (deleted > 0) {
            log.info("fmp_api_event_log purge: deleted {} rows older than {} days", deleted, properties.retentionDays)
        }
    }
}
