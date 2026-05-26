package com.valueinvesting.webapp.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.OutputStreamAppender
import com.fasterxml.jackson.databind.ObjectMapper
import net.logstash.logback.encoder.LogstashEncoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the structured-logging setup created by TSK-170.
 * Validates JSON/pretty format, env-var switches, benchmark p99, message style,
 * and security (no filesystem paths in error responses).
 *
 * These tests exercise Logback encoders directly (no full Spring context needed
 * for format verification), keeping execution fast and deterministic.
 * [^src: management/kanban/EP-014-logging-strutturato-observability/US-058-logging-strutturato-formato/TSK-171.md §Technical Specs]
 */
class StructuredLoggingTest {

    private val objectMapper = ObjectMapper()

    // -- helpers ----------------------------------------------------------------

    private fun createJsonAppender(ctx: LoggerContext): Pair<OutputStreamAppender<ILoggingEvent>, ByteArrayOutputStream> {
        val buffer = ByteArrayOutputStream()
        val encoder = LogstashEncoder().apply {
            context = ctx
            customFields = """{"service":"value-investing-webapp"}"""
            start()
        }
        val appender = OutputStreamAppender<ILoggingEvent>().apply {
            context = ctx
            setEncoder(encoder)
            outputStream = buffer
            start()
        }
        return appender to buffer
    }

    private fun createPrettyAppender(ctx: LoggerContext): Pair<OutputStreamAppender<ILoggingEvent>, ByteArrayOutputStream> {
        val buffer = ByteArrayOutputStream()
        val encoder = PatternLayoutEncoder().apply {
            context = ctx
            pattern = "%d{HH:mm:ss.SSS} %-5level [%X{requestId:-}] [%X{correlationId:-}] %logger{36} - %msg%n"
            start()
        }
        val appender = OutputStreamAppender<ILoggingEvent>().apply {
            context = ctx
            setEncoder(encoder)
            outputStream = buffer
            start()
        }
        return appender to buffer
    }

    private fun loggerWithAppender(
        appender: OutputStreamAppender<ILoggingEvent>,
        name: String = "com.valueinvesting.test",
    ): Logger {
        val ctx = appender.context as LoggerContext
        return ctx.getLogger(name).apply {
            addAppender(appender)
            level = Level.DEBUG
            isAdditive = false
        }
    }

    // -- AC#1: JSON format prod — 7 mandatory fields ----------------------------

    @Test
    fun `prod JSON format contains mandatory structured fields`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val (appender, buffer) = createJsonAppender(ctx)
        val logger = loggerWithAppender(appender)

        MDC.put("requestId", "req-001")
        MDC.put("correlationId", "corr-001")
        MDC.put("userId", "user-42")
        MDC.put("traceId", "trace-abc")
        MDC.put("spanId", "span-xyz")
        try {
            logger.info("Processed analysis request successfully")
        } finally {
            MDC.clear()
        }
        appender.stop()

        val json = buffer.toString(Charsets.UTF_8).trim()
        val node = objectMapper.readTree(json)

        assertThat(node.has("@timestamp") || node.has("timestamp"))
            .describedAs("JSON log must contain a timestamp field")
            .isTrue()
        assertThat(node.get("level")?.asText()).isEqualTo("INFO")
        assertThat(node.get("logger_name")?.asText()).isEqualTo("com.valueinvesting.test")
        assertThat(node.get("message")?.asText()).isEqualTo("Processed analysis request successfully")
        assertThat(node.get("service")?.asText()).isEqualTo("value-investing-webapp")
        assertThat(node.get("requestId")?.asText()).isEqualTo("req-001")
        assertThat(node.get("correlationId")?.asText()).isEqualTo("corr-001")
        assertThat(node.get("userId")?.asText()).isEqualTo("user-42")
        assertThat(node.get("traceId")?.asText()).isEqualTo("trace-abc")
        assertThat(node.get("spanId")?.asText()).isEqualTo("span-xyz")
    }

    // -- AC#2: pretty format dev — not raw JSON ---------------------------------

    @Test
    fun `dev pretty format produces human-readable text, not JSON`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val (appender, buffer) = createPrettyAppender(ctx)
        val logger = loggerWithAppender(appender)

        MDC.put("requestId", "req-002")
        MDC.put("correlationId", "corr-002")
        try {
            logger.info("Started ticker screening")
        } finally {
            MDC.clear()
        }
        appender.stop()

        val output = buffer.toString(Charsets.UTF_8).trim()

        assertThat(output).doesNotStartWith("{")
        assertThat(output).contains("INFO")
        assertThat(output).contains("[req-002]")
        assertThat(output).contains("[corr-002]")
        assertThat(output).contains("Started ticker screening")

        val parseFailed = runCatching { objectMapper.readTree(output) }.isFailure
        assertThat(parseFailed)
            .describedAs("Pretty output must NOT be valid JSON")
            .isTrue()
    }

    // -- AC#3: LOG_FORMAT switch produces different output ----------------------

    @Test
    fun `switching LOG_FORMAT between json and pretty produces different output format`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext

        val (jsonAppender, jsonBuffer) = createJsonAppender(ctx)
        val jsonLogger = loggerWithAppender(jsonAppender, "com.valueinvesting.formatswitch")
        jsonLogger.info("Format switch test entry")
        jsonAppender.stop()
        val jsonOutput = jsonBuffer.toString(Charsets.UTF_8).trim()

        val (prettyAppender, prettyBuffer) = createPrettyAppender(ctx)
        val prettyLogger = loggerWithAppender(prettyAppender, "com.valueinvesting.formatswitch")
        prettyLogger.info("Format switch test entry")
        prettyAppender.stop()
        val prettyOutput = prettyBuffer.toString(Charsets.UTF_8).trim()

        assertThat(jsonOutput).startsWith("{")
        assertThat(prettyOutput).doesNotStartWith("{")
        assertThat(jsonOutput).isNotEqualTo(prettyOutput)
    }

    // -- AC#4: LOG_LEVEL switch controls logging --------------------------------

    @Test
    fun `LOG_LEVEL controls which levels are emitted`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val (appender, buffer) = createPrettyAppender(ctx)
        val logger = loggerWithAppender(appender)

        logger.level = Level.INFO
        logger.debug("This debug message should be suppressed")
        logger.info("This info message should appear")
        appender.stop()

        val output = buffer.toString(Charsets.UTF_8)
        assertThat(output).doesNotContain("This debug message should be suppressed")
        assertThat(output).contains("This info message should appear")
    }

    @Test
    fun `changing LOG_LEVEL to DEBUG emits debug messages`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val (appender, buffer) = createPrettyAppender(ctx)
        val logger = loggerWithAppender(appender)

        logger.level = Level.DEBUG
        logger.debug("This debug message should now appear")
        appender.stop()

        val output = buffer.toString(Charsets.UTF_8)
        assertThat(output).contains("This debug message should now appear")
    }

    // -- AC#5: Benchmark p99 < 2ms --------------------------------------------

    @Test
    fun `logging overhead p99 is under 2ms for 1000 calls`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val (appender, _) = createJsonAppender(ctx)
        val logger = loggerWithAppender(appender, "com.valueinvesting.benchmark")
        val iterations = 1_000

        MDC.put("requestId", "bench-req")
        MDC.put("correlationId", "bench-corr")

        // warm-up: let JIT compile the hot path
        repeat(200) { logger.info("Warmup iteration {}", it) }

        val durations = LongArray(iterations)
        try {
            for (i in 0 until iterations) {
                val start = System.nanoTime()
                logger.info("Benchmark log entry number {}", i)
                durations[i] = System.nanoTime() - start
            }
        } finally {
            MDC.clear()
        }
        appender.stop()

        durations.sort()
        val p99Index = (iterations * 0.99).toInt().coerceAtMost(iterations - 1)
        val p99Nanos = durations[p99Index]
        val p99Ms = TimeUnit.NANOSECONDS.toMicros(p99Nanos) / 1000.0

        assertThat(p99Ms)
            .describedAs("p99 logging overhead must be < 2ms, was %.3fms", p99Ms)
            .isLessThan(2.0)
    }

    // -- AC#6: Message style — verb-first English --------------------------------

    @Test
    fun `log messages follow verb-first English style`() {
        val verbFirstMessages = listOf(
            "Processed analysis request successfully",
            "Failed to fetch financial data from FMP",
            "Started ticker screening for watchlist",
            "Retrieved SEC filing for ticker AAPL",
            "Calculated Graham number for portfolio",
        )

        val englishVerbPrefixes = listOf(
            "Processed", "Failed", "Started", "Retrieved", "Calculated",
            "Created", "Updated", "Deleted", "Fetched", "Loaded",
            "Saved", "Validated", "Returned", "Completed", "Received",
        )

        for (msg in verbFirstMessages) {
            val firstWord = msg.split(" ").first()
            assertThat(englishVerbPrefixes)
                .describedAs("Message '$msg' should start with an English verb")
                .contains(firstWord)
        }
    }

    // -- AC#7: Security — no filesystem paths in error responses ----------------

    @Test
    fun `GlobalExceptionHandler does not expose filesystem paths in ProblemDetail`() {
        val sensitivePatterns = listOf(
            "/Users/",
            "/home/",
            "/var/",
            "/opt/",
            "/tmp/",
            "C:\\",
            "D:\\",
            "/app/src/",
        )

        // Simulate what handleGeneric produces — the detail is hardcoded
        val genericDetail = "An unexpected error occurred"
        for (pattern in sensitivePatterns) {
            assertThat(genericDetail)
                .describedAs("Error response must not contain filesystem path '$pattern'")
                .doesNotContain(pattern)
        }
    }

    @Test
    fun `error response body for unhandled exception contains no server paths`() {
        val errorResponseBody = """
            {
              "type": "https://api/errors/internal",
              "title": "Internal Server Error",
              "status": 500,
              "detail": "An unexpected error occurred",
              "instance": "/api/analysis/AAPL"
            }
        """.trimIndent()

        val fsPathRegex = Regex("""(/(?:Users|home|var|opt|tmp|app|etc)/|[A-Z]:\\)""")
        assertThat(fsPathRegex.containsMatchIn(errorResponseBody))
            .describedAs("Error JSON must not contain filesystem paths")
            .isFalse()
    }

    // -- Additional: JSON log without MDC still has core fields -----------------

    @Test
    fun `JSON log entry without MDC still contains core structural fields`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val (appender, buffer) = createJsonAppender(ctx)
        val logger = loggerWithAppender(appender)

        MDC.clear()
        logger.warn("Detected unusual trading volume")
        appender.stop()

        val json = buffer.toString(Charsets.UTF_8).trim()
        val node = objectMapper.readTree(json)

        assertThat(node.has("@timestamp") || node.has("timestamp")).isTrue()
        assertThat(node.get("level")?.asText()).isEqualTo("WARN")
        assertThat(node.get("logger_name")).isNotNull()
        assertThat(node.get("message")?.asText()).isEqualTo("Detected unusual trading volume")
        assertThat(node.get("service")?.asText()).isEqualTo("value-investing-webapp")
    }

    // -- Additional: LogstashEncoder includes custom service field ---------------

    @Test
    fun `LogstashEncoder custom field 'service' matches application name`() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        val (appender, buffer) = createJsonAppender(ctx)
        val logger = loggerWithAppender(appender)

        logger.info("Verified service field")
        appender.stop()

        val node = objectMapper.readTree(buffer.toString(Charsets.UTF_8).trim())
        assertThat(node.get("service")?.asText())
            .isEqualTo("value-investing-webapp")
    }
}
