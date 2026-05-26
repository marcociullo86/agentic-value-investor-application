package com.valueinvesting.webapp.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * Unit tests for [SecurityEventLogger] verifying that all 10 event categories
 * produce correctly formatted structured logs with the SECURITY_EVENT marker,
 * appropriate log level, and expected structured arguments.
 *
 * Uses Logback [ListAppender] to capture [ILoggingEvent]s in-memory — no Spring
 * context needed. SecurityEventLogger is instantiated directly.
 *
 * [^src: management/kanban/EP-014-logging-strutturato-observability/US-062-security-events-logging/TSK-180.md §Technical Specs]
 */
class SecurityEventLoggerTest {

    private val securityLogger = SecurityEventLogger()
    private val listAppender = ListAppender<ILoggingEvent>()
    private lateinit var logbackLogger: Logger

    @BeforeEach
    fun setUp() {
        logbackLogger = LoggerFactory.getLogger("SECURITY") as Logger
        logbackLogger.level = Level.DEBUG
        listAppender.start()
        logbackLogger.addAppender(listAppender)
    }

    @AfterEach
    fun tearDown() {
        logbackLogger.detachAppender(listAppender)
        listAppender.stop()
        listAppender.list.clear()
        MDC.clear()
    }

    private fun lastEvent(): ILoggingEvent = listAppender.list.last()

    private fun structuredArgs(event: ILoggingEvent): Map<String, String> =
        (event.argumentArray ?: emptyArray())
            .filterIsInstance<net.logstash.logback.argument.StructuredArgument>()
            .associate { arg ->
                val s = arg.toString()
                val eq = s.indexOf('=')
                if (eq >= 0) s.substring(0, eq) to s.substring(eq + 1)
                else s to ""
            }

    // -- Login Success --------------------------------------------------------

    @Nested
    inner class LoginSuccess {

        @Test
        fun `emits INFO level`() {
            securityLogger.loginSuccess(userId = 42L, ip = "10.0.0.1", userAgent = "Mozilla/5.0")
            assertThat(lastEvent().level).isEqualTo(Level.INFO)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.loginSuccess(userId = 42L, ip = "10.0.0.1", userAgent = "Mozilla/5.0")
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId, ip, and userAgent in structured arguments`() {
            securityLogger.loginSuccess(userId = 42L, ip = "10.0.0.1", userAgent = "Mozilla/5.0")
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "42")
            assertThat(args).containsEntry("ip", "10.0.0.1")
            assertThat(args).containsEntry("userAgent", "Mozilla/5.0")
        }
    }

    // -- Login Failure --------------------------------------------------------

    @Nested
    inner class LoginFailure {

        @Test
        fun `emits WARN level`() {
            securityLogger.loginFailure(email = "user@example.com", reason = "invalid_credentials")
            assertThat(lastEvent().level).isEqualTo(Level.WARN)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.loginFailure(email = "user@example.com", reason = "invalid_credentials")
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains email and reason in structured arguments`() {
            securityLogger.loginFailure(email = "user@example.com", reason = "invalid_credentials")
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("email", "user@example.com")
            assertThat(args).containsEntry("reason", "invalid_credentials")
        }
    }

    // -- Password Changed -----------------------------------------------------

    @Nested
    inner class PasswordChanged {

        @Test
        fun `emits INFO level`() {
            securityLogger.passwordChanged(userId = 99L)
            assertThat(lastEvent().level).isEqualTo(Level.INFO)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.passwordChanged(userId = 99L)
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId in structured arguments`() {
            securityLogger.passwordChanged(userId = 99L)
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "99")
        }

        @Test
        fun `never contains the word password as a value in arguments`() {
            securityLogger.passwordChanged(userId = 99L)
            val event = lastEvent()
            val args = event.argumentArray ?: emptyArray()
            for (arg in args) {
                val value = arg.toString()
                assertThat(value.lowercase())
                    .describedAs("Argument value should not contain a raw password")
                    .doesNotContain("secret")
            }
            assertThat(event.formattedMessage).doesNotContainIgnoringCase("secret")
        }
    }

    // -- Password Reset Requested ---------------------------------------------

    @Nested
    inner class PasswordResetRequested {

        @Test
        fun `emits INFO level`() {
            securityLogger.passwordResetRequested(userId = 101L)
            assertThat(lastEvent().level).isEqualTo(Level.INFO)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.passwordResetRequested(userId = 101L)
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId in structured arguments`() {
            securityLogger.passwordResetRequested(userId = 101L)
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "101")
        }
    }

    // -- MFA Enabled ----------------------------------------------------------

    @Nested
    inner class MfaEnabled {

        @Test
        fun `emits INFO level`() {
            securityLogger.mfaEnabled(userId = 55L, method = "TOTP")
            assertThat(lastEvent().level).isEqualTo(Level.INFO)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.mfaEnabled(userId = 55L, method = "TOTP")
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId and method in structured arguments`() {
            securityLogger.mfaEnabled(userId = 55L, method = "TOTP")
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "55")
            assertThat(args).containsEntry("method", "TOTP")
        }
    }

    // -- MFA Disabled ---------------------------------------------------------

    @Nested
    inner class MfaDisabled {

        @Test
        fun `emits INFO level`() {
            securityLogger.mfaDisabled(userId = 55L)
            assertThat(lastEvent().level).isEqualTo(Level.INFO)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.mfaDisabled(userId = 55L)
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId in structured arguments`() {
            securityLogger.mfaDisabled(userId = 55L)
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "55")
        }
    }

    // -- MFA Fallback ---------------------------------------------------------

    @Nested
    inner class MfaFallback {

        @Test
        fun `emits INFO level`() {
            securityLogger.mfaFallback(userId = 60L, method = "recovery_code")
            assertThat(lastEvent().level).isEqualTo(Level.INFO)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.mfaFallback(userId = 60L, method = "recovery_code")
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId and method in structured arguments`() {
            securityLogger.mfaFallback(userId = 60L, method = "recovery_code")
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "60")
            assertThat(args).containsEntry("method", "recovery_code")
        }
    }

    // -- Permission Granted ---------------------------------------------------

    @Nested
    inner class PermissionGranted {

        @Test
        fun `emits INFO level`() {
            securityLogger.permissionGranted(userId = 10L, role = "ADMIN", grantedBy = 1L)
            assertThat(lastEvent().level).isEqualTo(Level.INFO)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.permissionGranted(userId = 10L, role = "ADMIN", grantedBy = 1L)
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId, role, and grantedBy in structured arguments`() {
            securityLogger.permissionGranted(userId = 10L, role = "ADMIN", grantedBy = 1L)
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "10")
            assertThat(args).containsEntry("role", "ADMIN")
            assertThat(args).containsEntry("grantedBy", "1")
        }
    }

    // -- Permission Revoked ---------------------------------------------------

    @Nested
    inner class PermissionRevoked {

        @Test
        fun `emits INFO level`() {
            securityLogger.permissionRevoked(userId = 10L, role = "ADMIN", revokedBy = 2L)
            assertThat(lastEvent().level).isEqualTo(Level.INFO)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.permissionRevoked(userId = 10L, role = "ADMIN", revokedBy = 2L)
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId, role, and revokedBy in structured arguments`() {
            securityLogger.permissionRevoked(userId = 10L, role = "ADMIN", revokedBy = 2L)
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "10")
            assertThat(args).containsEntry("role", "ADMIN")
            assertThat(args).containsEntry("revokedBy", "2")
        }
    }

    // -- Access Denied --------------------------------------------------------

    @Nested
    inner class AccessDenied {

        @Test
        fun `emits WARN level`() {
            securityLogger.accessDenied(userId = 77L, resource = "/api/admin/users", currentRole = "USER")
            assertThat(lastEvent().level).isEqualTo(Level.WARN)
        }

        @Test
        fun `carries SECURITY_EVENT marker`() {
            securityLogger.accessDenied(userId = 77L, resource = "/api/admin/users", currentRole = "USER")
            assertThat(lastEvent().markerList?.any { it.name == "SECURITY_EVENT" }).isTrue()
        }

        @Test
        fun `contains userId, resource, and currentRole in structured arguments`() {
            securityLogger.accessDenied(userId = 77L, resource = "/api/admin/users", currentRole = "USER")
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("userId", "77")
            assertThat(args).containsEntry("resource", "/api/admin/users")
            assertThat(args).containsEntry("currentRole", "USER")
        }

        @Test
        fun `handles null userId gracefully`() {
            securityLogger.accessDenied(userId = null, resource = "/api/admin/config", currentRole = null)
            assertThat(lastEvent().level).isEqualTo(Level.WARN)
            val args = structuredArgs(lastEvent())
            assertThat(args).containsEntry("resource", "/api/admin/config")
            assertThat(args).containsEntry("userId", "null")
        }
    }

    // -- Cross-cutting concerns -----------------------------------------------

    @Nested
    inner class CrossCutting {

        @Test
        fun `all 10 event methods produce log entries with SECURITY_EVENT marker`() {
            securityLogger.loginSuccess(1L, "127.0.0.1", "agent")
            securityLogger.loginFailure("a@b.com", "locked")
            securityLogger.passwordChanged(2L)
            securityLogger.passwordResetRequested(3L)
            securityLogger.mfaEnabled(4L, "TOTP")
            securityLogger.mfaDisabled(5L)
            securityLogger.mfaFallback(6L, "recovery_code")
            securityLogger.permissionGranted(7L, "ADMIN", 1L)
            securityLogger.permissionRevoked(8L, "ADMIN", 2L)
            securityLogger.accessDenied(9L, "/resource", "USER")

            assertThat(listAppender.list).hasSize(10)
            listAppender.list.forEach { event ->
                assertThat(event.markerList?.any { it.name == "SECURITY_EVENT" })
                    .describedAs("Event '${event.message}' must have SECURITY_EVENT marker")
                    .isTrue()
            }
        }

        @Test
        fun `at least 6 distinct event categories are covered`() {
            securityLogger.loginSuccess(1L, "ip", "ua")
            securityLogger.loginFailure("e", "r")
            securityLogger.passwordChanged(2L)
            securityLogger.mfaEnabled(3L, "TOTP")
            securityLogger.permissionGranted(4L, "ROLE", 1L)
            securityLogger.accessDenied(5L, "/res", "R")

            val distinctEvents = listAppender.list
                .mapNotNull { event ->
                    event.argumentArray
                        ?.filterIsInstance<net.logstash.logback.argument.StructuredArgument>()
                        ?.firstOrNull { it.toString().startsWith("event=") }
                        ?.toString()
                }
                .toSet()

            assertThat(distinctEvents).hasSizeGreaterThanOrEqualTo(6)
        }

        @Test
        fun `correlationId from MDC is present in log events when set`() {
            MDC.put("correlationId", "corr-sec-001")
            securityLogger.loginSuccess(42L, "10.0.0.1", "TestAgent")

            val event = lastEvent()
            assertThat(event.mdcPropertyMap).containsEntry("correlationId", "corr-sec-001")
        }

        @Test
        fun `correlationId is absent when MDC is empty`() {
            MDC.clear()
            securityLogger.loginSuccess(42L, "10.0.0.1", "TestAgent")

            val event = lastEvent()
            assertThat(event.mdcPropertyMap).doesNotContainKey("correlationId")
        }

        @Test
        fun `password change events never leak password values`() {
            securityLogger.passwordChanged(userId = 1L)
            securityLogger.passwordResetRequested(userId = 2L)

            listAppender.list.forEach { event ->
                val formatted = event.formattedMessage
                val args = event.argumentArray ?: emptyArray()
                args.forEach { arg ->
                    assertThat(arg.toString().lowercase())
                        .describedAs("No argument should contain a raw password value")
                        .doesNotMatch(".*password\\s*=\\s*(?!PASSWORD_CHANGED|PASSWORD_RESET_REQUESTED)\\S+.*")
                }
                assertThat(formatted).doesNotContainIgnoringCase("secret")
                assertThat(formatted).doesNotContain("p@ss")
            }
        }
    }
}
