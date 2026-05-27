package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.LoginAttemptEntity
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

    fun countByIpAddressAndAttemptedAtAfter(ipAddress: String, since: Instant): Long

    fun countByAccountEmailAndAttemptedAtAfter(accountEmail: String, since: Instant): Long

    @Query(
        """
        SELECT MIN(l.attemptedAt) FROM LoginAttemptEntity l
        WHERE l.ipAddress = :ipAddress AND l.attemptedAt >= :since
        """,
    )
    fun findOldestAttemptedAtByIpSince(
        @Param("ipAddress") ipAddress: String,
        @Param("since") since: Instant,
    ): Instant?

    @Query(
        """
        SELECT MIN(l.attemptedAt) FROM LoginAttemptEntity l
        WHERE l.accountEmail = :accountEmail AND l.attemptedAt >= :since
        """,
    )
    fun findOldestAttemptedAtByAccountSince(
        @Param("accountEmail") accountEmail: String,
        @Param("since") since: Instant,
    ): Instant?

    @Modifying
    @Query("DELETE FROM LoginAttemptEntity l WHERE l.attemptedAt < :cutoff")
    fun deleteByAttemptedAtBefore(@Param("cutoff") cutoff: Instant): Int
}
