package com.valueinvesting.webapp.logging

import ch.qos.logback.classic.LoggerContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [PiiRedactionEncoder] covering all 6 PII categories,
 * recursive (nested/JSON-in-string) redaction, relaxed-mode bypass,
 * and p99 latency benchmark.
 *
 * [^src: management/kanban/EP-014-logging-strutturato-observability/US-060-redazione-pii-log/TSK-176.md §Technical Specs]
 * [^src: management/kanban/EP-014-logging-strutturato-observability/US-060-redazione-pii-log/US-060.md §AC]
 */
class PiiRedactionEncoderTest {

    private lateinit var encoder: PiiRedactionEncoder

    @BeforeEach
    fun setUp() {
        val ctx = LoggerFactory.getILoggerFactory() as LoggerContext
        encoder = PiiRedactionEncoder().apply {
            context = ctx
            relaxedMode = false
            start()
        }
    }

    // ── Category 1: PAN ─────────────────────────────────────────────────

    @Nested
    inner class PanRedaction {

        @Test
        fun `PAN top-level is redacted to BIN plus last 4`() {
            val input = """{"message":"Card 4111111111111111 used for payment"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("411111****1111")
            assertThat(result).doesNotContain("4111111111111111")
        }

        @Test
        fun `PAN nested in JSON object context is redacted`() {
            val input = """{"context":{"cardNumber":"4111111111111111","amount":100}}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("411111****1111")
            assertThat(result).doesNotContain("4111111111111111")
        }

        @Test
        fun `PAN in JSON-in-string field is redacted recursively`() {
            val innerJson = """{"pan":"4111111111111111","merchant":"ACME"}"""
            val escaped = innerJson.replace("\"", "\\\"")
            val input = """{"message":"$escaped"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).doesNotContain("4111111111111111")
            assertThat(result).contains("411111****1111")
        }

        @Test
        fun `PAN with 13 digits (Visa old format) is redacted`() {
            val input = """{"message":"Card 4222222222222 used"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).doesNotContain("4222222222222")
        }
    }

    // ── Category 2: JWT / secrets ────────────────────────────────────────

    @Nested
    inner class JwtAndSecretRedaction {

        @Test
        fun `JWT token is fully redacted`() {
            val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
            val input = """{"token":"$jwt"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("[REDACTED]")
            assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9")
        }

        @Test
        fun `password field value is redacted`() {
            val input = """{"password":"s3cr3t_P@ss!"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("[REDACTED]")
            assertThat(result).doesNotContain("s3cr3t_P@ss!")
        }

        @Test
        fun `api_key field value is redacted`() {
            val input = """{"api_key":"abc123-xyz-789-secret"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("[REDACTED]")
            assertThat(result).doesNotContain("abc123-xyz-789-secret")
        }

        @Test
        fun `secret field value is redacted`() {
            val input = """{"secret":"myTopSecretValue123"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("[REDACTED]")
            assertThat(result).doesNotContain("myTopSecretValue123")
        }

        @Test
        fun `refresh_token field value is redacted`() {
            val input = """{"refresh_token":"rt_a1b2c3d4e5f6"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("[REDACTED]")
            assertThat(result).doesNotContain("rt_a1b2c3d4e5f6")
        }

        @Test
        fun `authorization header value is redacted`() {
            val input = """{"authorization":"Bearer some-opaque-token"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("[REDACTED]")
            assertThat(result).doesNotContain("some-opaque-token")
        }
    }

    // ── Category 3: IPv4 ────────────────────────────────────────────────

    @Nested
    inner class Ipv4Redaction {

        @Test
        fun `IPv4 last octet is zeroed`() {
            val input = """{"clientIp":"192.168.1.42"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("192.168.1.0")
            assertThat(result).doesNotContain("192.168.1.42")
        }

        @Test
        fun `IPv4 with single-digit last octet is zeroed`() {
            val input = """{"ip":"10.0.0.1"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("10.0.0.0")
            assertThat(result).doesNotContain("10.0.0.1")
        }
    }

    // ── Category 4: IBAN (strict vs relaxed) ────────────────────────────

    @Nested
    inner class IbanRedaction {

        @Test
        fun `IBAN in prod mode shows only country code and last 4`() {
            val input = """{"iban":"IT60X0542811101000000123456"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("IT****3456")
            assertThat(result).doesNotContain("IT60X0542811101000000123456")
        }

        @Test
        fun `IBAN in relaxed mode is kept as-is`() {
            val input = """{"iban":"IT60X0542811101000000123456"}"""

            val result = encoder.redact(input, relaxed = true)

            assertThat(result).contains("IT60X0542811101000000123456")
        }

        @Test
        fun `German IBAN in prod mode is redacted`() {
            val input = """{"iban":"DE89370400440532013000"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("DE****3000")
            assertThat(result).doesNotContain("DE89370400440532013000")
        }
    }

    // ── Category 5: Email (strict vs relaxed) ───────────────────────────

    @Nested
    inner class EmailRedaction {

        @Test
        fun `email in prod mode shows only domain`() {
            val input = """{"email":"user@example.com"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("***@example.com")
            assertThat(result).doesNotContain("user@example.com")
        }

        @Test
        fun `email in relaxed mode is kept as-is`() {
            val input = """{"email":"user@example.com"}"""

            val result = encoder.redact(input, relaxed = true)

            assertThat(result).contains("user@example.com")
        }

        @Test
        fun `email with subdomain in prod mode shows only domain`() {
            val input = """{"contact":"admin@mail.corp.example.com"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).contains("***@mail.corp.example.com")
            assertThat(result).doesNotContain("admin@mail.corp.example.com")
        }
    }

    // ── Category 6: Recursive / nested (3+ levels) ─────────────────────

    @Nested
    inner class RecursiveRedaction {

        @Test
        fun `3-level nested object has all PII redacted`() {
            val input = """{"l1":{"l2":{"l3":{"card":"4111111111111111","ip":"10.0.0.99"}}}}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result)
                .doesNotContain("4111111111111111")
                .contains("411111****1111")
            assertThat(result)
                .doesNotContain("10.0.0.99")
                .contains("10.0.0.0")
        }

        @Test
        fun `deeply nested object with mixed PII categories is fully redacted`() {
            val input = """{"request":{"user":{"profile":{"email":"deep@test.com","payment":{"card":"5500000000000004","iban":"FR7630006000011234567890189"}}}}}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).doesNotContain("deep@test.com")
            assertThat(result).doesNotContain("5500000000000004")
            assertThat(result).doesNotContain("FR7630006000011234567890189")
            assertThat(result).contains("***@test.com")
            assertThat(result).contains("550000****0004")
            assertThat(result).contains("FR****0189")
        }

        @Test
        fun `multiple PII values in single message are all redacted`() {
            val input = """{"message":"User user@example.com paid with 4111111111111111 from 192.168.1.42"}"""

            val result = encoder.redact(input, relaxed = false)

            assertThat(result).doesNotContain("user@example.com")
            assertThat(result).doesNotContain("4111111111111111")
            assertThat(result).doesNotContain("192.168.1.42")
        }
    }

    // ── Enabled/disabled flag ───────────────────────────────────────────

    @Test
    fun `redaction with relaxed false still applies always-patterns`() {
        val input = """{"password":"leaked","card":"4111111111111111"}"""

        val result = encoder.redact(input, relaxed = true)

        assertThat(result).doesNotContain("leaked")
        assertThat(result).doesNotContain("4111111111111111")
        assertThat(result)
            .describedAs("always-patterns (PAN, password) apply even in relaxed mode")
            .contains("[REDACTED]")
            .contains("411111****1111")
    }

    // ── Benchmark ───────────────────────────────────────────────────────

    @Test
    fun `redaction p99 latency under 1ms for 1000 iterations`() {
        val piiInput = buildString {
            append("""{"message":"User user@example.com card 4111111111111111""")
            append(""" from 192.168.1.42 IBAN IT60X0542811101000000123456",""")
            append(""""password":"secret123","api_key":"key-abc-123"}""")
        }

        repeat(500) { encoder.redact(piiInput, relaxed = false) }

        val iterations = 1_000
        val durations = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            encoder.redact(piiInput, relaxed = false)
            durations[i] = System.nanoTime() - start
        }

        durations.sort()
        val p99Index = (iterations * 0.99).toInt().coerceAtMost(iterations - 1)
        val p99Nanos = durations[p99Index]
        val p99Ms = TimeUnit.NANOSECONDS.toMicros(p99Nanos) / 1000.0

        assertThat(p99Ms)
            .describedAs("p99 redaction latency must be < 1ms, was %.3fms", p99Ms)
            .isLessThan(1.0)
    }
}
