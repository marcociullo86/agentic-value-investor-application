package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.LoginAttemptEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

// Queries for auth rate limiting (TSK-229) and brute-force protection (TSK-230).
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §Schema DB]
@Repository
interface LoginAttemptRepository : JpaRepository<LoginAttemptEntity, Long> {

    fun countByIpAddressAndFailureReasonAndAttemptedAtAfter(
        ipAddress: String,
        failureReason: String,
        since: Instant,
    ): Long

    fun countByAccountEmailAndFailureReasonAndAttemptedAtAfter(
        accountEmail: String,
        failureReason: String,
        since: Instant,
    ): Long

    @Query(
        """
        SELECT MIN(l.attemptedAt) FROM LoginAttemptEntity l
        WHERE l.ipAddress = :ipAddress AND l.failureReason = :failureReason AND l.attemptedAt >= :since
        """,
    )
    fun findOldestAttemptedAtByIpSince(
        @Param("ipAddress") ipAddress: String,
        @Param("failureReason") failureReason: String,
        @Param("since") since: Instant,
    ): Instant?

    @Query(
        """
        SELECT MIN(l.attemptedAt) FROM LoginAttemptEntity l
        WHERE l.accountEmail = :accountEmail AND l.failureReason = :failureReason AND l.attemptedAt >= :since
        """,
    )
    fun findOldestAttemptedAtByAccountSince(
        @Param("accountEmail") accountEmail: String,
        @Param("failureReason") failureReason: String,
        @Param("since") since: Instant,
    ): Instant?

    // Most-recent (max) attempt for an account_email+reason in a rolling
    // window — used by BruteForceProtectionService (TSK-230) to compute the
    // lockout expiry: lockout_until = max(attemptedAt) + lockoutDuration.
    @Query(
        """
        SELECT MAX(l.attemptedAt) FROM LoginAttemptEntity l
        WHERE l.accountEmail = :accountEmail AND l.failureReason = :failureReason AND l.attemptedAt >= :since
        """,
    )
    fun findLatestAttemptedAtByAccountSince(
        @Param("accountEmail") accountEmail: String,
        @Param("failureReason") failureReason: String,
        @Param("since") since: Instant,
    ): Instant?

    // Last N successful login IPs for an account — used by new-device detection
    // (TSK-230 / ADR-025 §7). Pageable bounds the result set to the configured
    // history size (default 5).
    @Query(
        """
        SELECT l.ipAddress FROM LoginAttemptEntity l
        WHERE l.accountEmail = :accountEmail AND l.success = true
        ORDER BY l.attemptedAt DESC
        """,
    )
    fun findRecentSuccessfulIpsByAccount(
        @Param("accountEmail") accountEmail: String,
        pageable: Pageable,
    ): List<String>

    @Modifying
    @Query("DELETE FROM LoginAttemptEntity l WHERE l.attemptedAt < :cutoff")
    fun deleteByAttemptedAtBefore(@Param("cutoff") cutoff: Instant): Int
}
