package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.FmpEventLogProperties
import com.valueinvesting.webapp.persistence.repository.FmpApiEventLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FmpEventLogMaintenanceJobTest {

    @Test
    fun `purge deletes rows older than retention days`() {
        val repository = mockk<FmpApiEventLogRepository>()
        every { repository.deleteByOccurredAtBefore(any()) } returns 3

        val fixed = Clock.fixed(Instant.parse("2026-05-22T12:00:00Z"), ZoneOffset.UTC)
        val job = FmpEventLogMaintenanceJob(
            repository = repository,
            properties = FmpEventLogProperties(retentionDays = 90),
            clock = fixed,
        )

        job.purgeExpiredRows()

        verify(exactly = 1) {
            repository.deleteByOccurredAtBefore(Instant.parse("2026-02-21T12:00:00Z"))
        }
    }
}
