package com.valueinvesting.webapp.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException

class TickerResetServiceTest {

    private val masterPasswordService: MasterPasswordService = mockk()
    private val jdbcTemplate: JdbcTemplate = mockk(relaxed = true)
    private val service = TickerResetService(masterPasswordService, jdbcTemplate)

    @Test
    fun `invalid master password throws 403`() {
        every { masterPasswordService.verify(any()) } returns false

        val ex = assertThrows<ResponseStatusException> { service.reset("AAPL", "bad") }
        assertThat(ex.statusCode.value()).isEqualTo(403)
    }

    @Test
    fun `valid master password resets all impacted tables for the uppercased ticker`() {
        every { masterPasswordService.verify("ok") } returns true

        val result = service.reset("aapl", "ok")

        assertThat(result.ticker).isEqualTo("AAPL")
        assertThat(result.deletedByTable.keys).containsExactlyInAnyOrder(
            "filing_chunks",
            "filing_blob",
            "deep_analysis_report",
            "deep_analysis_run",
            "deep_analysis_event_log",
            "news_classification",
            "price_action_snapshot",
        )
    }
}
