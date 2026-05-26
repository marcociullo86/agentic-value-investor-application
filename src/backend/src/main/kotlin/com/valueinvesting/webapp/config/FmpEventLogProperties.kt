package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "fmp.event-log")
data class FmpEventLogProperties(
    val retentionDays: Long = DEFAULT_RETENTION_DAYS,
    val purgeCron: String = DEFAULT_PURGE_CRON,
    val purgeZone: String = DEFAULT_PURGE_ZONE,
) {
    companion object {
        const val DEFAULT_RETENTION_DAYS = 90L
        const val DEFAULT_PURGE_CRON = "0 0 3 * * *"
        const val DEFAULT_PURGE_ZONE = "UTC"
    }
}
