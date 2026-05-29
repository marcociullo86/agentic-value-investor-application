package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.DeepAnalysisRunEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

// Repository per deep_analysis_run (V027).
//
// `findFirstByTickerOrderByRequestedAtDesc` — usato dal GET /latest per
// recuperare l'ultima esecuzione per ticker (qualunque status).
// `findFirstByTickerAndStatusOrderByRequestedAtDesc` — usato dall'enqueue
// per dedupe: se esiste una run RUNNING per il ticker non ne creiamo una
// nuova.
@Repository
interface DeepAnalysisRunRepository : JpaRepository<DeepAnalysisRunEntity, UUID> {

    fun findFirstByTickerOrderByRequestedAtDesc(ticker: String): DeepAnalysisRunEntity?

    fun findFirstByTickerAndStatusOrderByRequestedAtDesc(
        ticker: String,
        status: String,
    ): DeepAnalysisRunEntity?
}
