package com.valueinvesting.webapp.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for the PII leak-detection regex patterns used by the
 * `piiLeakDetection` Gradle task (build.gradle.kts).
 *
 * The patterns are replicated here verbatim so any drift between the Gradle
 * task and these tests is itself a signal (review the Gradle task if a test
 * starts failing unexpectedly).
 *
 * [^src: design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md §5]
 */
@DisplayName("PII Leak Detection — regex pattern coverage")
class PiiLeakDetectionTest {

    private val patterns: Map<String, Regex> = mapOf(
        "PAN" to Regex("\\b\\d{13,19}\\b"),
        "CVV" to Regex("\"cvv\"\\s*:\\s*\"\\d{3,4}\"", RegexOption.IGNORE_CASE),
        "IBAN" to Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}[A-Z0-9]{7,27}\\b"),
        "JWT" to Regex("eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]+"),
        "API_KEY" to Regex("\"(api[_-]?key|apikey)\"\\s*:\\s*\"[^\"]{8,}\"", RegexOption.IGNORE_CASE),
        "PASSWORD" to Regex("\"password\"\\s*:\\s*\"[^\"]+\"", RegexOption.IGNORE_CASE),
    )

    private fun scanLine(line: String): List<Pair<String, MatchResult>> =
        patterns.flatMap { (category, pattern) ->
            pattern.findAll(line).map { category to it }
        }

    // ------------------------------------------------------------------
    // Detection: un-redacted PII must be caught
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Un-redacted PII → detected")
    inner class UnredactedDetection {

        @Test
        fun `PAN 16 digits detected`() {
            val line = """{"message":"Payment processed","card":"4111111111111111"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "PAN" }
        }

        @ParameterizedTest(name = "PAN with {0} digits detected")
        @ValueSource(strings = [
            "4111111111111",       // 13 digits (Visa old)
            "41111111111111111",   // 17 digits
            "4111111111111111111", // 19 digits
        ])
        fun `PAN various lengths detected`(pan: String) {
            val hits = scanLine("""card=$pan""")
            assertThat(hits).anyMatch { it.first == "PAN" }
        }

        @Test
        fun `CVV detected`() {
            val line = """{"cvv":"123"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "CVV" }
        }

        @Test
        fun `CVV 4-digit detected`() {
            val line = """{"cvv" : "1234"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "CVV" }
        }

        @Test
        fun `IBAN detected`() {
            val line = """{"iban":"IT60X0542811101000000123456"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "IBAN" }
        }

        @Test
        fun `IBAN German format detected`() {
            val line = """account=DE89370400440532013000"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "IBAN" }
        }

        @Test
        fun `JWT detected`() {
            val line = """{"token":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "JWT" }
        }

        @Test
        fun `API_KEY detected`() {
            val line = """{"api_key":"sk-abc123456789"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "API_KEY" }
        }

        @Test
        fun `API_KEY with hyphen variant detected`() {
            val line = """{"api-key":"prod-key-abcdef123"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "API_KEY" }
        }

        @Test
        fun `API_KEY case-insensitive detected`() {
            val line = """{"API_KEY":"sk-abc123456789"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "API_KEY" }
        }

        @Test
        fun `PASSWORD detected`() {
            val line = """{"password":"MySecret123"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "PASSWORD" }
        }

        @Test
        fun `PASSWORD case-insensitive detected`() {
            val line = """{"Password":"SuperS3cret!"}"""
            val hits = scanLine(line)
            assertThat(hits).anyMatch { it.first == "PASSWORD" }
        }
    }

    // ------------------------------------------------------------------
    // Redacted PII must NOT trigger false positives
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Redacted PII → not detected (no false positives)")
    inner class RedactedNoFalsePositive {

        @Test
        fun `PAN masked with asterisks not detected`() {
            val line = """{"card":"411111****1111"}"""
            val hits = scanLine(line).filter { it.first == "PAN" }
            assertThat(hits).isEmpty()
        }

        @Test
        fun `PAN fully redacted marker not detected`() {
            val line = """{"card":"[REDACTED]"}"""
            val hits = scanLine(line).filter { it.first == "PAN" }
            assertThat(hits).isEmpty()
        }

        @Test
        fun `CVV redacted not detected`() {
            val line = """{"cvv":"***"}"""
            val hits = scanLine(line).filter { it.first == "CVV" }
            assertThat(hits).isEmpty()
        }

        @Test
        fun `CVV REDACTED marker not detected`() {
            val line = """{"cvv":"[REDACTED]"}"""
            val hits = scanLine(line).filter { it.first == "CVV" }
            assertThat(hits).isEmpty()
        }

        @Test
        fun `IBAN partially masked not detected`() {
            val line = """{"iban":"IT60****123456"}"""
            val hits = scanLine(line).filter { it.first == "IBAN" }
            assertThat(hits).isEmpty()
        }

        @Test
        fun `JWT redacted marker not detected`() {
            val line = """{"token":"[REDACTED]"}"""
            val hits = scanLine(line).filter { it.first == "JWT" }
            assertThat(hits).isEmpty()
        }

        @Test
        fun `API key short redacted not detected`() {
            val line = """{"api_key":"sk-***"}"""
            val hits = scanLine(line).filter { it.first == "API_KEY" }
            assertThat(hits).isEmpty()
        }

        @Test
        fun `IP address not flagged by any pattern`() {
            val line = """{"remote_ip":"192.168.1.100"}"""
            val hits = scanLine(line)
            assertThat(hits).isEmpty()
        }
    }

    // ------------------------------------------------------------------
    // PASSWORD special case: "[REDACTED]" as the value is still a non-empty
    // string match — the PiiRedactionEncoder prevents the real password
    // from reaching the log in the first place, so the PASSWORD regex
    // matching `"password":"[REDACTED]"` is expected and acceptable.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("PASSWORD redaction — expected regex behaviour")
    inner class PasswordRedactionBehaviour {

        @Test
        fun `password REDACTED marker still matches regex — expected`() {
            val line = """{"password":"[REDACTED]"}"""
            val hits = scanLine(line).filter { it.first == "PASSWORD" }
            assertThat(hits)
                .describedAs(
                    "The PASSWORD regex matches any non-empty value. " +
                    "The PiiRedactionEncoder ensures real passwords never " +
                    "reach the log, so the string [REDACTED] appearing as " +
                    "the value is a non-issue in practice."
                )
                .hasSize(1)
        }

        @Test
        fun `password empty value not detected`() {
            val line = """{"password":""}"""
            val hits = scanLine(line).filter { it.first == "PASSWORD" }
            assertThat(hits).isEmpty()
        }
    }

    // ------------------------------------------------------------------
    // Report format: the Gradle task builds "[$CATEGORY] file:line: content"
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Violation report format")
    inner class ReportFormat {

        private fun buildViolationReport(
            lines: List<String>,
            fileName: String = "test-output.log",
        ): List<String> {
            val violations = mutableListOf<String>()
            lines.forEachIndexed { lineNum, line ->
                patterns.forEach { (category, pattern) ->
                    if (pattern.containsMatchIn(line)) {
                        violations.add("[$category] $fileName:${lineNum + 1}: ${line.take(200)}")
                    }
                }
            }
            return violations
        }

        @Test
        fun `report contains category, filename, line number, and content`() {
            val lines = listOf(
                """{"level":"INFO","message":"ok"}""",
                """{"level":"ERROR","card":"4111111111111111"}""",
            )
            val report = buildViolationReport(lines)

            assertThat(report).hasSize(1)
            assertThat(report[0]).startsWith("[PAN]")
            assertThat(report[0]).contains("test-output.log:2:")
            assertThat(report[0]).contains("4111111111111111")
        }

        @Test
        fun `report truncates long lines to 200 chars`() {
            val longPayload = "x".repeat(300)
            val lines = listOf("""{"password":"$longPayload"}""")
            val report = buildViolationReport(lines)

            assertThat(report).hasSize(1)
            assertThat(report[0].substringAfter(": ")).hasSizeLessThanOrEqualTo(200)
        }

        @Test
        fun `multiple violations on same line produce separate entries`() {
            val line = """{"password":"secret","cvv":"123"}"""
            val report = buildViolationReport(listOf(line))

            assertThat(report).hasSize(2)
            val categories = report.map { it.substringBefore("]").removePrefix("[") }.toSet()
            assertThat(categories).containsExactlyInAnyOrder("PASSWORD", "CVV")
        }

        @Test
        fun `clean log produces empty report`() {
            val lines = listOf(
                """{"level":"INFO","message":"Application started"}""",
                """{"level":"DEBUG","action":"health-check","status":"ok"}""",
            )
            val report = buildViolationReport(lines)
            assertThat(report).isEmpty()
        }

        @Test
        fun `all 6 categories detected in multi-line log`() {
            val lines = listOf(
                """card=4111111111111111""",
                """{"cvv":"123"}""",
                """iban=IT60X0542811101000000123456""",
                """token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.dBjftJeZ4CVP""",
                """{"api_key":"sk-abc123456789"}""",
                """{"password":"MySecret123"}""",
            )
            val report = buildViolationReport(lines)
            val categories = report.map { it.substringBefore("]").removePrefix("[") }.toSet()
            assertThat(categories).containsExactlyInAnyOrder(
                "PAN", "CVV", "IBAN", "JWT", "API_KEY", "PASSWORD"
            )
        }
    }
}
