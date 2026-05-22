package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByTokenValue(tokenValue: String): RefreshToken?
}
