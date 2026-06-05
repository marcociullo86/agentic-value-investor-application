package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenValue(tokenValue: String): RefreshToken?
    fun deleteAllByUserId(userId: UUID): Long

    /**
     * Bulk-revokes every still-active refresh token of [userId] (cascade
     * revocation on reuse detection — ADR-027 §2). Sets `revoked_at = :now`
     * on rows where `revoked_at IS NULL`. Returns the number of rows updated
     * so callers can surface the count in security events.
     *
     * Hits the existing `refresh_tokens_user_active_idx (user_id, revoked_at)`
     * index (V001), so no migration is required. Idempotent by construction:
     * a second invocation on the same user yields zero updated rows.
     *
     * The query is `@Modifying`, so JPA clears the persistence context after
     * execution (`clearAutomatically = true`) to avoid stale managed entities
     * shadowing the bulk update, and flushes pending changes first
     * (`flushAutomatically = true`) to keep the transactional view coherent.
     *
     * [^src: design_&_architecture/decisions/ADR-027-refresh-token-cascade-revocation.md §2]
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE RefreshToken t SET t.revokedAt = :now " +
            "WHERE t.userId = :userId AND t.revokedAt IS NULL"
    )
    fun revokeAllActiveByUserId(@Param("userId") userId: UUID, @Param("now") now: Instant): Int
}
