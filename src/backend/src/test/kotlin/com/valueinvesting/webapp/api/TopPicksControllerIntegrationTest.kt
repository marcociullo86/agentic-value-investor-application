package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.persistence.entity.TopValuePickEntity
import com.valueinvesting.webapp.persistence.repository.TopValuePickRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Integration + contract tests for `GET /api/top-picks` — US-050, TSK-139.
 *
 * Strategy: `@SpringBootTest(webEnvironment=RANDOM_PORT) @AutoConfigureMockMvc`
 * + Testcontainers PostgreSQL 17. Pre-populates `top_value_picks` with 20 fixture
 * entities across 2 run dates.
 *
 * Covered ACs (from TSK-139):
 *  1.  Happy path GET /api/top-picks → 200 + correct JSON shape.
 *  2.  date param valid → returns picks for that date.
 *  3.  date param malformed → 400 problem+json.
 *  4.  date param future → 400 problem+json.
 *  5.  verdict filter → only items with that verdict.
 *  6.  sector filter → ILIKE substring case-insensitive.
 *  7.  min_mos filter → items with marginOfSafety >= value.
 *  8.  page + size pagination → elements subset.
 *  9.  size > 100 → 400 (validation).
 * 10.  Cache-Control header present: `public, max-age=3600`.
 * 11.  Contract: response shape has runDate, page, size, total, items fields.
 *
 * [^src: management/kanban/EP-012-batch-top-value-picks/US-050-endpoint-top-picks/TSK-139.md]
 * [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/api/TopPicksController.kt]
 * [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/service/TopPicksQueryService.kt]
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("TopPicksController Integration — US-050 / TSK-139")
class TopPicksControllerIntegrationTest {

    companion object {
        private val RUN_DATE_TODAY: LocalDate = LocalDate.of(2026, 5, 26)
        private val RUN_DATE_YESTERDAY: LocalDate = RUN_DATE_TODAY.minusDays(1)

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_toppicks_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var topValuePickRepository: TopValuePickRepository

    @BeforeEach
    fun setUp() {
        topValuePickRepository.deleteAll()
        seedFixtures()
    }

    // -------------------------------------------------------------------------
    // AC-1 — Happy path: GET /api/top-picks → 200 + JSON shape
    // -------------------------------------------------------------------------
    @Test
    fun `happy path - returns 200 with correct response shape`() {
        mockMvc.get("/api/top-picks") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.runDate") { exists() }
            jsonPath("$.page") { exists() }
            jsonPath("$.size") { exists() }
            jsonPath("$.total") { exists() }
            jsonPath("$.items") { isArray() }
        }
    }

    // -------------------------------------------------------------------------
    // AC-11 — Contract: response shape matches TopPicksPageResponse
    // -------------------------------------------------------------------------
    @Test
    fun `contract - response contains all required TopPicksPageResponse fields`() {
        mockMvc.get("/api/top-picks") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.runDate") { isString() }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(30) }
            jsonPath("$.total") { isNumber() }
            jsonPath("$.items[0].ticker") { isString() }
            jsonPath("$.items[0].rankPosition") { isNumber() }
            jsonPath("$.items[0].verdettoClasse") { isString() }
            jsonPath("$.items[0].source") { isString() }
        }
    }

    // -------------------------------------------------------------------------
    // AC-2 — date param valid → returns picks for that date
    // -------------------------------------------------------------------------
    @Test
    fun `date param valid - returns picks for the specified date`() {
        mockMvc.get("/api/top-picks") {
            param("date", RUN_DATE_YESTERDAY.toString())
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.runDate") { value(RUN_DATE_YESTERDAY.toString()) }
            jsonPath("$.total") { value(5) } // 5 fixtures for yesterday
        }
    }

    // -------------------------------------------------------------------------
    // AC-3 — date param malformed → 400 problem+json
    // -------------------------------------------------------------------------
    @Test
    fun `date param malformed - returns 400`() {
        mockMvc.get("/api/top-picks") {
            param("date", "not-a-date")
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // -------------------------------------------------------------------------
    // AC-4 — date param future → 400
    // -------------------------------------------------------------------------
    @Test
    fun `date param future - returns 400`() {
        val futureDate = LocalDate.now().plusDays(1).toString()
        mockMvc.get("/api/top-picks") {
            param("date", futureDate)
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // -------------------------------------------------------------------------
    // AC-5 — verdict filter → only items with that verdict
    // -------------------------------------------------------------------------
    @Test
    fun `verdict filter - returns only items with specified verdict`() {
        mockMvc.get("/api/top-picks") {
            param("date", RUN_DATE_TODAY.toString())
            param("verdict", "APPROVATO_PANIC_BUY")
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            // 3 fixtures with APPROVATO_PANIC_BUY for today
            jsonPath("$.total") { value(3) }
            jsonPath("$.items[0].verdettoClasse") { value("APPROVATO_PANIC_BUY") }
        }
    }

    // -------------------------------------------------------------------------
    // AC-6 — sector filter → ILIKE substring case-insensitive
    // -------------------------------------------------------------------------
    @Test
    fun `sector filter - case-insensitive substring match`() {
        mockMvc.get("/api/top-picks") {
            param("date", RUN_DATE_TODAY.toString())
            param("sector", "energy") // fixtures use "Energy" — should match case-insensitive
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(2) } // 2 Energy fixtures for today
        }
    }

    // -------------------------------------------------------------------------
    // AC-7 — min_mos filter → items with marginOfSafety >= value
    // -------------------------------------------------------------------------
    @Test
    fun `min_mos filter - returns only items with MoS above threshold`() {
        // Today fixtures: rank 1-10 have MoS 10..100 step 10
        // min_mos=50 → items with mos >= 50 → ranks 5..10 → 6 items
        mockMvc.get("/api/top-picks") {
            param("date", RUN_DATE_TODAY.toString())
            param("min_mos", "50.0")
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            // Each item must have marginOfSafety >= 50
        }.andReturn().response.contentAsString.let { body ->
            val itemsNode = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .readTree(body).get("items")
            assertThat(itemsNode).isNotNull
            itemsNode.forEach { item ->
                val mos = item.get("marginOfSafety")?.asDouble() ?: 0.0
                assertThat(mos).isGreaterThanOrEqualTo(50.0)
            }
        }
    }

    // -------------------------------------------------------------------------
    // AC-8 — page + size pagination
    // -------------------------------------------------------------------------
    @Test
    fun `pagination - page=1 size=3 returns items 4-6 for today`() {
        mockMvc.get("/api/top-picks") {
            param("date", RUN_DATE_TODAY.toString())
            param("page", "1")
            param("size", "3")
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(3) }
            jsonPath("$.items.length()") { value(3) }
        }
    }

    // -------------------------------------------------------------------------
    // AC-9 — size > 100 → 400
    // -------------------------------------------------------------------------
    @Test
    fun `size over 100 - returns 400`() {
        mockMvc.get("/api/top-picks") {
            param("size", "101")
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // -------------------------------------------------------------------------
    // AC-10 — Cache-Control header
    // -------------------------------------------------------------------------
    @Test
    fun `cache-control header - public max-age=3600 is present`() {
        mockMvc.get("/api/top-picks") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            header { string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=3600")) }
            header { string("Cache-Control", org.hamcrest.Matchers.containsString("public")) }
        }
    }

    // -------------------------------------------------------------------------
    // AC-extra — Empty result: unknown date → 200 total=0
    // -------------------------------------------------------------------------
    @Test
    fun `unknown date - returns 200 with total=0`() {
        mockMvc.get("/api/top-picks") {
            param("date", "2020-01-01")
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(0) }
            jsonPath("$.items") { isEmpty() }
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures: 10 entities for TODAY + 5 entities for YESTERDAY
    //
    // TODAY distribution:
    //   - rank 1-3:  APPROVATO_PANIC_BUY, sector=Technology, MoS=10-30%
    //   - rank 4-8:  APPROVATO, sector=Technology + Energy mix, MoS=40-80%
    //   - rank 9-10: WATCHLIST, sector=Energy, MoS=90-100%
    // YESTERDAY: 5 APPROVATO Technology entities
    // -------------------------------------------------------------------------
    private fun seedFixtures() {
        val todayEntities = listOf(
            // APPROVATO_PANIC_BUY
            buildEntity(RUN_DATE_TODAY, "TICK1", 1, "APPROVATO_PANIC_BUY", "Technology", 10.0),
            buildEntity(RUN_DATE_TODAY, "TICK2", 2, "APPROVATO_PANIC_BUY", "Technology", 20.0),
            buildEntity(RUN_DATE_TODAY, "TICK3", 3, "APPROVATO_PANIC_BUY", "Technology", 30.0),
            // APPROVATO
            buildEntity(RUN_DATE_TODAY, "TICK4", 4, "APPROVATO", "Technology", 40.0),
            buildEntity(RUN_DATE_TODAY, "TICK5", 5, "APPROVATO", "Technology", 50.0),
            buildEntity(RUN_DATE_TODAY, "TICK6", 6, "APPROVATO", "Technology", 60.0),
            buildEntity(RUN_DATE_TODAY, "TICK7", 7, "APPROVATO", "Energy", 70.0),
            buildEntity(RUN_DATE_TODAY, "TICK8", 8, "APPROVATO", "Energy", 80.0),
            // WATCHLIST
            buildEntity(RUN_DATE_TODAY, "TICK9", 9, "WATCHLIST", "Technology", 90.0),
            buildEntity(RUN_DATE_TODAY, "TICK10", 10, "WATCHLIST", "Technology", 100.0),
        )
        val yesterdayEntities = (1..5).map { i ->
            buildEntity(RUN_DATE_YESTERDAY, "YTICK$i", i, "APPROVATO", "Technology", (i * 10.0))
        }
        topValuePickRepository.saveAll(todayEntities + yesterdayEntities)
    }

    private fun buildEntity(
        runDate: LocalDate,
        ticker: String,
        rank: Int,
        verdict: String,
        sector: String,
        mosPct: Double,
    ): TopValuePickEntity = TopValuePickEntity(
        runDate = runDate,
        ticker = ticker,
        verdettoClasse = verdict,
        marginOfSafety = BigDecimal.valueOf(mosPct).setScale(4, java.math.RoundingMode.HALF_UP),
        posizionamento = null,
        sector = sector,
        marketCapUsd = 10_000_000_000L,
        rankPosition = rank,
        source = "SCREENER",
        companyName = "Company $ticker",
        ruleSignalSummary = null,
        createdAt = Instant.now(),
    )
}
