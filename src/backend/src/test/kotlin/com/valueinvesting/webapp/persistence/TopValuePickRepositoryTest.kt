package com.valueinvesting.webapp.persistence

import com.valueinvesting.webapp.persistence.entity.Stock
import com.valueinvesting.webapp.persistence.entity.TopValuePickEntity
import com.valueinvesting.webapp.persistence.entity.TopValuePickId
import com.valueinvesting.webapp.persistence.repository.StockRepository
import com.valueinvesting.webapp.persistence.repository.TopValuePickRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * JPA integration tests for [TopValuePickRepository] — US-049, TSK-137.
 *
 * Strategy: @DataJpaTest + Testcontainers PostgreSQL 17 (pgvector image).
 * Flyway migrations (including V022__top_value_picks.sql) are applied automatically
 * because `@DataJpaTest` loads the JPA slice which includes Flyway by default
 * when `spring.flyway.enabled=true` in the test application properties.
 *
 * Covered ACs:
 *  1. PK composta (run_date, ticker): save + merge via upsert pattern.
 *  2. findByRunDateOrderByRankPositionAsc: 30 entities returned in rank order.
 *  3. findByRunDateAndVerdettoClasseInOrderByRankPositionAsc: verdict filter.
 *  4. findDistinctRunDates(pageable): returns most recent date first.
 *  5. deleteOlderThan: removes entities older than cutoff (retention 90d).
 *
 * [^src: management/kanban/EP-012-batch-top-value-picks/US-049-persistenza-top-picks/TSK-137.md]
 * [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/persistence/repository/TopValuePickRepository.kt]
 * [^src: src/backend/src/main/resources/db/migration/V022__top_value_picks.sql]
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("TopValuePickRepository — US-049 / TSK-137")
class TopValuePickRepositoryTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_repo_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            // ensure Flyway runs in DataJpaTest slice
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @Autowired
    private lateinit var repository: TopValuePickRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    private val TODAY: LocalDate = LocalDate.of(2026, 5, 26)
    private val YESTERDAY: LocalDate = TODAY.minusDays(1)

    @BeforeEach
    fun cleanSlate() {
        repository.deleteAll()
        stockRepository.deleteAll()
    }

    // FK top_value_picks.ticker → stocks(ticker): parent rows must exist before
    // inserting child picks. saveAllAndFlush forces the INSERT into stocks to
    // hit the DB now — without it, Hibernate may reorder the queued inserts
    // and trigger the FK check before the parent rows are visible.
    private fun seedStocks(vararg tickers: String) {
        stockRepository.saveAllAndFlush(tickers.distinct().map { Stock(ticker = it) })
    }

    // -------------------------------------------------------------------------
    // AC-1 — PK composta: save entity + re-save same PK does not throw (merge)
    // -------------------------------------------------------------------------
    @Test
    fun `PK composta - saving entity with same run_date+ticker performs merge not duplicate insert`() {
        seedStocks("AAPL")
        val entity = buildEntity(TODAY, "AAPL", rank = 1, mos = 35.0)
        repository.save(entity)

        // Modify via save with same composite PK — JPA should merge (update)
        val updated = entity.copy(marginOfSafety = BigDecimal("40.0000"))
        repository.save(updated)

        val all = repository.findByRunDateOrderByRankPositionAsc(TODAY)
        assertThat(all).hasSize(1)
        assertThat(all.first().marginOfSafety).isEqualByComparingTo(BigDecimal("40.0000"))
    }

    // -------------------------------------------------------------------------
    // AC-2 — findByRunDateOrderByRankPositionAsc: 30 entities returned in order
    // -------------------------------------------------------------------------
    @Test
    fun `findByRunDateOrderByRankPositionAsc - returns all 30 entities ordered by rankPosition`() {
        // Insert 30 entities with shuffled order
        val shuffled = (1..30).shuffled()
        seedStocks(*(1..30).map { "TICK$it" }.toTypedArray())
        shuffled.forEach { rank ->
            repository.save(buildEntity(TODAY, "TICK$rank", rank = rank, mos = (100.0 - rank)))
        }

        val result = repository.findByRunDateOrderByRankPositionAsc(TODAY)

        assertThat(result).hasSize(30)
        val ranks = result.map { it.rankPosition }
        assertThat(ranks).isSorted
        assertThat(ranks.first()).isEqualTo(1)
        assertThat(ranks.last()).isEqualTo(30)
    }

    // -------------------------------------------------------------------------
    // AC-3 — findByRunDateAndVerdettoClasseInOrderByRankPositionAsc: verdict filter
    // -------------------------------------------------------------------------
    @Test
    fun `findByRunDateAndVerdettoClasseInOrderByRankPositionAsc - returns only matching verdicts`() {
        seedStocks("A1", "A2", "W1", "W2")
        repository.save(buildEntity(TODAY, "A1", rank = 1, mos = 50.0, verdict = "APPROVATO"))
        repository.save(buildEntity(TODAY, "A2", rank = 2, mos = 45.0, verdict = "APPROVATO_PANIC_BUY"))
        repository.save(buildEntity(TODAY, "W1", rank = 3, mos = 20.0, verdict = "WATCHLIST"))
        repository.save(buildEntity(TODAY, "W2", rank = 4, mos = 15.0, verdict = "WATCHLIST"))

        val result = repository.findByRunDateAndVerdettoClasseInOrderByRankPositionAsc(
            TODAY,
            listOf("APPROVATO"),
        )
        assertThat(result).hasSize(1)
        assertThat(result.first().ticker).isEqualTo("A1")

        val multiResult = repository.findByRunDateAndVerdettoClasseInOrderByRankPositionAsc(
            TODAY,
            listOf("WATCHLIST", "APPROVATO_PANIC_BUY"),
        )
        assertThat(multiResult).hasSize(3)
        assertThat(multiResult.map { it.verdettoClasse }).containsOnly("WATCHLIST", "APPROVATO_PANIC_BUY")
        // Must be ordered by rankPosition
        assertThat(multiResult.map { it.rankPosition }).isSorted
    }

    // -------------------------------------------------------------------------
    // AC-4 — findDistinctRunDates(Pageable): returns most recent date first
    // -------------------------------------------------------------------------
    @Test
    fun `findDistinctRunDates - pageable returns most recent date first`() {
        val date1 = TODAY.minusDays(2)
        val date2 = TODAY.minusDays(1)
        val date3 = TODAY

        seedStocks("X1", "X2", "X3")
        repository.save(buildEntity(date1, "X1", rank = 1, mos = 10.0))
        repository.save(buildEntity(date2, "X2", rank = 1, mos = 20.0))
        repository.save(buildEntity(date3, "X3", rank = 1, mos = 30.0))

        val top1 = repository.findDistinctRunDates(PageRequest.of(0, 1))
        assertThat(top1).hasSize(1)
        assertThat(top1.first()).isEqualTo(date3)

        val all3 = repository.findDistinctRunDates(PageRequest.of(0, 10))
        assertThat(all3).hasSize(3)
        // Descending order: date3 > date2 > date1
        assertThat(all3[0]).isEqualTo(date3)
        assertThat(all3[1]).isEqualTo(date2)
        assertThat(all3[2]).isEqualTo(date1)
    }

    // -------------------------------------------------------------------------
    // AC-5 — deleteOlderThan: retention 90 days
    // -------------------------------------------------------------------------
    @Test
    fun `deleteOlderThan - removes entities with runDate before cutoff`() {
        val old = TODAY.minusDays(91) // older than 90 days
        val boundary = TODAY.minusDays(90) // on the boundary (not deleted)
        val recent = TODAY.minusDays(10) // recent

        seedStocks("OLD1", "OLD2", "BOUND1", "NEW1")
        repository.save(buildEntity(old, "OLD1", rank = 1, mos = 10.0))
        repository.save(buildEntity(old, "OLD2", rank = 2, mos = 9.0))
        repository.save(buildEntity(boundary, "BOUND1", rank = 1, mos = 15.0))
        repository.save(buildEntity(recent, "NEW1", rank = 1, mos = 30.0))

        val cutoff = TODAY.minusDays(90)
        val deleted = repository.deleteOlderThan(cutoff)

        assertThat(deleted).isEqualTo(2)

        val remaining = repository.findAll()
        assertThat(remaining).hasSize(2)
        assertThat(remaining.map { it.ticker }).containsExactlyInAnyOrder("BOUND1", "NEW1")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildEntity(
        runDate: LocalDate,
        ticker: String,
        rank: Int,
        mos: Double,
        verdict: String = "APPROVATO",
    ): TopValuePickEntity = TopValuePickEntity(
        runDate = runDate,
        ticker = ticker,
        verdettoClasse = verdict,
        marginOfSafety = BigDecimal.valueOf(mos).setScale(4, java.math.RoundingMode.HALF_UP),
        posizionamento = null,
        sector = "Technology",
        marketCapUsd = 10_000_000_000L,
        rankPosition = rank,
        source = "SCREENER",
        companyName = "Company $ticker",
        ruleSignalSummary = null,
        createdAt = Instant.now(),
    )
}
