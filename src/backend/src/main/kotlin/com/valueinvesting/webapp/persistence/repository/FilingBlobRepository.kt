package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface FilingBlobRepository : JpaRepository<FilingBlobEntity, Long> {

    fun findByAccessionNumber(accessionNumber: String): FilingBlobEntity?

    fun findByAccessionNumberAndExpiresAtAfter(
        accessionNumber: String,
        now: Instant,
    ): FilingBlobEntity?

    fun findByTickerAndExpiresAtAfterOrderByFilingDateDesc(
        ticker: String,
        now: Instant,
    ): List<FilingBlobEntity>
}
