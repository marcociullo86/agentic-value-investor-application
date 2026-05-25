package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.DeepAnalysisReportEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface DeepAnalysisReportRepository : JpaRepository<DeepAnalysisReportEntity, Long> {

    fun findByTickerAndFilingComboHashAndExpiresAtAfter(
        ticker: String,
        filingComboHash: String,
        now: Instant,
    ): DeepAnalysisReportEntity?
}
