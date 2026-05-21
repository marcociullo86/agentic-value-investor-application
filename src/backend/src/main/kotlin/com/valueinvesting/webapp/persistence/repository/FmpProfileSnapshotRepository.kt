package com.valueinvesting.webapp.persistence.repository

import com.valueinvesting.webapp.persistence.entity.FmpProfileSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

// Repository for fmp_profile_snapshot.  Latest-for-ticker lookup served by index
// fmp_profile_lookup_idx (ticker, fetched_at DESC).
// [^src: design_&_architecture/data/er-diagram.md §fmp_profile_snapshot] (Indice)
// [^src: src/backend/src/main/resources/db/migration/V003__create_fmp_cache.sql §fmp_profile_lookup_idx]
@Repository
interface FmpProfileSnapshotRepository : JpaRepository<FmpProfileSnapshot, UUID> {

    fun findFirstByTickerOrderByFetchedAtDesc(ticker: String): FmpProfileSnapshot?
}
