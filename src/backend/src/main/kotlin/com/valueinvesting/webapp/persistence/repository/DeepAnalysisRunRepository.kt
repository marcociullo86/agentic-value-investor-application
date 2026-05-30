package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.DeepAnalysisRunEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

// Repository per deep_analysis_run (V027 + V028 kind split).
//
// Dopo lo split INGEST vs ANALYSIS (V028) le query "latest" devono essere
// filtrate per `kind` — un GET /latest dell'ANALYSIS non deve mai vedere
// una run di tipo INGEST, e viceversa. La query legacy
// `findFirstByTickerOrderByRequestedAtDesc` (senza filtro) è mantenuta perché
// è usata da test/code paths storici e non è più chiamata dal service.
//
// `findFirstByTickerAndKindOrderByRequestedAtDesc` — ultima run per (ticker,
// kind) qualunque status; usato da `getLatestAnalysis` / `getLatestIngest`.
// `findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc` — usato dal
// dedupe dell'enqueue per evitare due RUNNING dello stesso kind sullo
// stesso ticker.
@Repository
interface DeepAnalysisRunRepository : JpaRepository<DeepAnalysisRunEntity, UUID> {

    fun findFirstByTickerOrderByRequestedAtDesc(ticker: String): DeepAnalysisRunEntity?

    fun findFirstByTickerAndStatusOrderByRequestedAtDesc(
        ticker: String,
        status: String,
    ): DeepAnalysisRunEntity?

    fun findFirstByTickerAndKindOrderByRequestedAtDesc(
        ticker: String,
        kind: String,
    ): DeepAnalysisRunEntity?

    fun findFirstByTickerAndKindAndStatusOrderByRequestedAtDesc(
        ticker: String,
        kind: String,
        status: String,
    ): DeepAnalysisRunEntity?
}
