package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.FmpFinancialSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

// Repository for fmp_financial_snapshot.  The "latest snapshot for (ticker, endpoint)"
// lookup is the hot path — backed by index fmp_fin_snap_lookup_idx (DESC sort).
// Derived query `findFirstByTickerAndEndpointOrderByFetchedAtDesc` translates directly
// to `ORDER BY fetched_at DESC LIMIT 1` which the index serves without a sort step.
// [^src: design_&_architecture/data/er-diagram.md §fmp_financial_snapshot] (Indice)
// [^src: src/backend/src/main/resources/db/migration/V003__create_fmp_cache.sql §fmp_fin_snap_lookup_idx]
@Repository
interface FmpFinancialSnapshotRepository : JpaRepository<FmpFinancialSnapshot, UUID> {

    fun findFirstByTickerAndEndpointOrderByFetchedAtDesc(
        ticker: String,
        endpoint: String,
    ): FmpFinancialSnapshot?
}
