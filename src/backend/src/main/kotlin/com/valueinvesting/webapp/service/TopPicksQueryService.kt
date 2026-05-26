package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.TopPickItemDto
import com.valueinvesting.webapp.api.model.TopPicksPageResponse
import com.valueinvesting.webapp.persistence.entity.TopValuePickEntity
import com.valueinvesting.webapp.persistence.repository.TopValuePickRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDate

// Query-service per il batch output `top_value_picks` (EP-012, US-050).
// Le scritture sono di pertinenza di `TopValuePicksJob` (TSK-131); questo
// servizio è SOLO read-side per l'endpoint pubblico `GET /api/top-picks`.
//
// Risoluzione `runDate`:
//   - se il caller passa un valore, viene usato verbatim (anche se non c'è
//     alcun record per quella data: 200 con total=0 è il comportamento AC#3);
//   - se null, prende l'ultimo `run_date` distinct disponibile via
//     `findDistinctRunDates(PageRequest.of(0, 1))` (sort DESC nel @Query del
//     repository). Se neanche quello esiste → response con runDate=null/total=0.
//
// I filtri `sector` (substring case-insensitive) e `minMos` sono applicati in
// memoria sui ~30 record/giorno (TOP_N cap su TopValuePicksJob): il volume è
// per definizione piccolo e non giustifica una Specification JPA dedicata —
// se il TOP_N crescerà oltre i 200, passare a query JPQL custom con ILIKE.
//
// [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/persistence/repository/TopValuePickRepository.kt]
// [^src: management/kanban/EP-012-batch-top-value-picks/US-050-endpoint-top-picks/TSK-138.md]
@Service
class TopPicksQueryService(
    private val topValuePickRepository: TopValuePickRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun findTopPicks(
        runDate: LocalDate?,
        verdict: String?,
        sector: String?,
        minMos: Double?,
        page: Int,
        size: Int,
    ): TopPicksPageResponse {
        val resolvedDate = runDate ?: latestRunDate()
        log.info(
            "TopPicks query — date={} verdict={} sector={} minMos={} page={} size={}",
            resolvedDate, verdict, sector, minMos, page, size,
        )

        if (resolvedDate == null) {
            return TopPicksPageResponse(
                runDate = null,
                page = page,
                size = size,
                total = 0,
                items = emptyList(),
            )
        }

        val all = if (verdict.isNullOrBlank()) {
            topValuePickRepository.findByRunDateOrderByRankPositionAsc(resolvedDate)
        } else {
            topValuePickRepository.findByRunDateAndVerdettoClasseInOrderByRankPositionAsc(
                resolvedDate,
                listOf(verdict),
            )
        }

        val filtered = all
            .filter { entity ->
                sector.isNullOrBlank() || entity.sector?.contains(sector, ignoreCase = true) == true
            }
            .filter { entity ->
                minMos == null || (entity.marginOfSafety?.toDouble() ?: 0.0) >= minMos
            }

        val total = filtered.size
        val from = page * size
        val to = (from + size).coerceAtMost(total)
        val pageItems = if (from < total) filtered.subList(from, to) else emptyList()

        return TopPicksPageResponse(
            runDate = resolvedDate,
            page = page,
            size = size,
            total = total,
            items = pageItems.map { it.toDto() },
        )
    }

    private fun latestRunDate(): LocalDate? =
        topValuePickRepository.findDistinctRunDates(PageRequest.of(0, 1)).firstOrNull()

    private fun TopValuePickEntity.toDto() = TopPickItemDto(
        ticker = this.ticker,
        rankPosition = this.rankPosition,
        verdettoClasse = this.verdettoClasse,
        marginOfSafety = this.marginOfSafety?.toDouble(),
        sector = this.sector,
        marketCapUsd = this.marketCapUsd,
        source = this.source,
        companyName = this.companyName,
    )
}
