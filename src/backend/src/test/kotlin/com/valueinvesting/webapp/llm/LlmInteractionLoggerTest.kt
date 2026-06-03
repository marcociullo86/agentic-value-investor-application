package com.valueinvesting.webapp.llm

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * Unit tests for [LlmInteractionLogger] (CQRL TSK-299 F-04 + F-05).
 *
 * Covers the three behavioural branches required by US-088 AC#3:
 * - enabled=false → no log emitted
 * - enabled=true & content ≤ maxChars → logged verbatim
 * - enabled=true & content > maxChars → logged with truncation suffix
 *
 * Plus the F-05 robustness guard: maxChars=0 must fail fast at construction.
 *
 * Uses Logback [ListAppender] (no Spring context) — same pattern as
 * [com.valueinvesting.webapp.service.SecurityEventLoggerTest].
 *
 * [^src: management/kanban/EP-020-trasparenza-analisi-llm/US-088-log-interazioni-llm/TSK-299.md]
 */
class LlmInteractionLoggerTest {

    private val listAppender = ListAppender<ILoggingEvent>()
    private lateinit var logbackLogger: Logger

    @BeforeEach
    fun setUp() {
        logbackLogger = LoggerFactory.getLogger(LlmInteractionLogger::class.java) as Logger
        logbackLogger.level = Level.DEBUG
        listAppender.start()
        logbackLogger.addAppender(listAppender)
    }

    @AfterEach
    fun tearDown() {
        logbackLogger.detachAppender(listAppender)
        listAppender.stop()
        listAppender.list.clear()
    }

    @Test
    fun `enabled=false emits no log event`() {
        val logger = LlmInteractionLogger(enabled = false, maxChars = 100)

        logger.log(
            context = "ctx",
            systemPrompt = "system",
            userPrompt = "user",
            response = "response",
            durationMs = 42L,
        )

        assertThat(listAppender.list).isEmpty()
    }

    @Test
    fun `enabled=true and content within maxChars logs verbatim`() {
        val logger = LlmInteractionLogger(enabled = true, maxChars = 1000)
        val systemPrompt = "you are a financial analyst"
        val userPrompt = "analyze AAPL filing chunk 1"
        val response = """{"items":[{"testo":"risk A","chunk_index":1}]}"""

        logger.log(
            context = "munger-query:AAPL",
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            response = response,
            durationMs = 123L,
        )

        assertThat(listAppender.list).hasSize(1)
        val event = listAppender.list.first()
        assertThat(event.level).isEqualTo(Level.INFO)
        val formatted = event.formattedMessage
        assertThat(formatted).contains("munger-query:AAPL")
        assertThat(formatted).contains("durationMs=123")
        // Verbatim: no truncation suffix anywhere
        assertThat(formatted).doesNotContain("[troncato")
        assertThat(formatted).contains(systemPrompt)
        assertThat(formatted).contains(userPrompt)
        assertThat(formatted).contains(response)
    }

    @Test
    fun `enabled=true and content longer than maxChars logs truncated with suffix`() {
        val maxChars = 50
        val logger = LlmInteractionLogger(enabled = true, maxChars = maxChars)
        // 200-char response → exceeds maxChars=50 → 150 chars truncated
        val longResponse = "x".repeat(200)
        val shortSystem = "sys"
        val shortUser = "usr"

        logger.log(
            context = "munger-synthesis:AAPL",
            systemPrompt = shortSystem,
            userPrompt = shortUser,
            response = longResponse,
            durationMs = 999L,
        )

        assertThat(listAppender.list).hasSize(1)
        val formatted = listAppender.list.first().formattedMessage
        // Truncation suffix present with correct overflow count
        assertThat(formatted).contains("…[troncato 150 char]")
        // Truncated content keeps only the first maxChars of the long response
        assertThat(formatted).contains("x".repeat(maxChars) + "…[troncato 150 char]")
        // Short fields are NOT suffixed (they fit within maxChars)
        assertThat(formatted).contains("--- SYSTEM PROMPT ---\nsys\n")
        assertThat(formatted).contains("--- USER PROMPT ---\nusr\n")
    }

    @Test
    fun `maxChars=0 fails fast at construction (TSK-299 F-05 guard)`() {
        assertThatThrownBy { LlmInteractionLogger(enabled = false, maxChars = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("log-max-chars must be > 0")
    }
}
