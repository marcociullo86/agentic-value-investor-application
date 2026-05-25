package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.PriceActionSnapshotEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface PriceActionSnapshotRepository : JpaRepository<PriceActionSnapshotEntity, Long> {

    fun findByTickerAndCalcDate(ticker: String, calcDate: LocalDate): PriceActionSnapshotEntity?
}
