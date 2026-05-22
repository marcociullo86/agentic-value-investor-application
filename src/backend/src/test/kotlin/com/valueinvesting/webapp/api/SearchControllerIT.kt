package com.valueinvesting.webapp.api

import com.ninjasquad.springmockk.MockkBean
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpTickerNotFoundException
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.fmp.dto.SearchHitDto
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
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

// Integration test for GET /api/search?query={q} and GET /api/search/{ticker} — US-001.
//
// HTTP CLIENT CHOICE — MockMvc (with @AutoConfigureMockMvc):
//   Chosen over TestRestTemplate for these reasons:
//   1. Full DispatcherServlet + filter chain is exercised (same as TestRestTemplate
//      with RANDOM_PORT) but without actual TCP overhead, keeping tests faster.
//   2. The GlobalExceptionHandler (RFC 9457 ProblemDetails) is part of the
//      DispatcherServlet advice chain: MockMvc catches its output transparently.
//   3. `addFilters = false` disables Spring Security filters so we test the
//      search/screener endpoints (Track A, no auth) without coupling to JWT/auth
//      configuration that belongs to Track B.
//   4. Consistent with AnalysisControllerIT pattern already established in this
//      codebase (same @SpringBootTest + @AutoConfigureMockMvc pattern).
//
// TESTCONTAINERS — PostgreSQL IS REQUIRED:
//   FmpCacheService.getOrFetchProfile writes fmp_profile_snapshot + upserts the
//   stocks row (US-005 lazy-populate) inside a @Transactional method backed by JPA.
//   Without a real RDBMS the full Spring context would fail to start (Flyway +
//   Hibernate validate mode + repositories). PostgreSQL 17-alpine matches the
//   tech_stack.md §Database spec.
//
// MOCK STRATEGY — @MockkBean FmpAdapter:
//   Spring resolves @MockkBean by bean type (FmpAdapter).  When multiple beans
//   implement the same interface, Spring picks the @Primary one — here
//   ResilientFmpAdapter — but @MockkBean replaces THE RESOLVED bean (i.e. the
//   @Primary) in the context.  Therefore all calls that go through
//   SearchService → FmpCacheService.fetchFn → fmpAdapter.getProfile(...) hit
//   the mock, bypassing both ResilientFmpAdapter and FmpAdapterRestClient.
//   There are NO real FMP HTTP calls.
//
// [^src: management/kanban/EP-001-ricerca-e-screening/US-001-ricerca-ticker-simbolo/TSK-004.md]
// [^src: design_&_architecture/components/backend-components.md §Testing strategy]
// [^src: design_&_architecture/decisions/ADR-002-backend-stack.md §Testing]
// [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Status codes]
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
class SearchControllerIT {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("value_investing_test")
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

    // @MockkBean replaces the @Primary ResilientFmpAdapter bean resolved for the
    // FmpAdapter interface.  SearchService and FmpCacheService both receive the mock.
    @MockkBean
    private lateinit var fmpAdapter: FmpAdapter

    @BeforeEach
    fun resetMocks() {
        // Clear recorded calls (not stubs) between tests so verify() counts are isolated.
        clearMocks(fmpAdapter, answers = false, recordedCalls = true)
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — GET /api/search?query=AAPL → 200 + non-empty list (US-001 AC)
    // -------------------------------------------------------------------------

    @Test
    fun `GET search with valid query returns 200 and non-empty items list`() {
        // Stub: FMP returns three hits for "AAPL".
        every { fmpAdapter.searchSymbol("AAPL", any()) } returns listOf(
            SearchHitDto(symbol = "AAPL", name = "Apple Inc.", currency = "USD",
                stockExchange = "NASDAQ Global Select", exchangeShortName = "NASDAQ"),
            SearchHitDto(symbol = "APC", name = "Apple Corp Holdings", currency = "USD",
                stockExchange = "New York Stock Exchange", exchangeShortName = "NYSE"),
            SearchHitDto(symbol = "AAPL.NE", name = "Apple Inc. (CDR)", currency = "CAD",
                stockExchange = "NEO", exchangeShortName = "NEO"),
        )

        mockMvc.get("/api/search?query=AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            // Items list is non-empty (DoD US-001 AC).
            jsonPath("$.items.length()") { value(3) }
            // All tickers are uppercase (SearchService normalisation AC).
            jsonPath("$.items[0].ticker") { value("AAPL") }
            jsonPath("$.items[0].companyName") { value("Apple Inc.") }
            // sector + marketCapUsd are null: FMP /search does not return them.
            jsonPath("$.items[0].sector") { doesNotExist() }
            jsonPath("$.items[0].marketCapUsd") { doesNotExist() }
        }

        // No real FMP financial calls were made — guard against scope creep.
        verify(atLeast = 1) { fmpAdapter.searchSymbol("AAPL", any()) }
        verify(exactly = 0) { fmpAdapter.getIncomeStatement(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — GET /api/search?query=XXXXXXXX → 200 + empty list (US-001 AC)
    // -------------------------------------------------------------------------

    @Test
    fun `GET search for non-existent query returns 200 with empty items`() {
        every { fmpAdapter.searchSymbol("XXXXXXXX", any()) } returns emptyList()

        mockMvc.get("/api/search?query=XXXXXXXX") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            // Empty list — NOT 404 (OpenAPI spec: "list may be empty on no match").
            jsonPath("$.items") { isArray() }
            jsonPath("$.items.length()") { value(0) }
        }

        verify(atLeast = 1) { fmpAdapter.searchSymbol("XXXXXXXX", any()) }
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — GET /api/search/AAPL → 200 + StockProfile (US-001 AC)
    // -------------------------------------------------------------------------

    @Test
    fun `GET search by ticker for existing ticker returns 200 with StockProfile`() {
        val profileDto = ProfileDto(
            symbol = "AAPL",
            price = 150.0,
            marketCap = 3_000_000_000_000.0,
            companyName = "Apple Inc.",
            sector = "Technology",
            industry = "Consumer Electronics",
            currency = "USD",
            exchange = "NASDAQ",
            country = "US",
        )
        // FmpCacheService.getOrFetchProfile invokes the fetchFn lambda when there
        // is no cached snapshot.  The lambda calls fmpAdapter.getProfile(ticker).
        every { fmpAdapter.getProfile("AAPL") } returns profileDto

        mockMvc.get("/api/search/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            // Required fields (OpenAPI StockProfile schema).
            jsonPath("$.ticker") { value("AAPL") }
            jsonPath("$.companyName") { value("Apple Inc.") }
            // Optional populated fields.
            jsonPath("$.sector") { value("Technology") }
            jsonPath("$.industry") { value("Consumer Electronics") }
            jsonPath("$.currentPrice") { value(150.0) }
            // dataSnapshotAt must be present (US-005 cache timestamp AC).
            jsonPath("$.dataSnapshotAt") { exists() }
        }

        // The X-Data-Snapshot-At header is written by AnalysisController but NOT by
        // SearchController — verify dataSnapshotAt in body is sufficient here.
        verify(atLeast = 1) { fmpAdapter.getProfile("AAPL") }
        // No financial statement calls — guard against unintended fanout.
        verify(exactly = 0) { fmpAdapter.getIncomeStatement(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Scenario 4 — GET /api/search/XXXXXXXX → 404 + ProblemDetails (RFC 9457)
    // -------------------------------------------------------------------------

    @Test
    fun `GET search by ticker for unknown ticker returns 404 ProblemDetails`() {
        // FmpAdapter.getProfile throws FmpTickerNotFoundException (documented contract).
        every { fmpAdapter.getProfile("XXXXXXXX") } throws FmpTickerNotFoundException("XXXXXXXX")

        mockMvc.get("/api/search/XXXXXXXX") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isNotFound() }
            // RFC 9457 requires content-type application/problem+json.
            content { contentType("application/problem+json") }
            // Mandatory RFC 9457 fields.
            jsonPath("$.status") { value(404) }
            jsonPath("$.title") { value("Ticker not found") }
            // Extension field "ticker" set by GlobalExceptionHandler.handleFmpTickerNotFound.
            // Spring 6.x ProblemDetail serializes extensions under `properties`
            // (not flattened per RFC 9457 §3.2). See gap `be-problemdetail-flatten`.
            jsonPath("$.properties.ticker") { value("XXXXXXXX") }
            // type URI convention from GlobalExceptionHandler.
            jsonPath("$.type") { value("https://api/errors/ticker-not-found") }
        }

        verify(atLeast = 1) { fmpAdapter.getProfile("XXXXXXXX") }
    }

    // -------------------------------------------------------------------------
    // Scenario 5 — GET /api/search?query= (blank) → 400 + ProblemDetails
    // -------------------------------------------------------------------------

    @Test
    fun `GET search with blank query returns 400 ProblemDetails`() {
        // No mock setup: SearchService.normalizeQuery raises IllegalArgumentException
        // before touching the adapter — verifiable via verify(exactly = 0).

        mockMvc.get("/api/search?query=") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.title") { value("Bad Request") }
            jsonPath("$.detail") { value("query must not be blank") }
        }

        // Guard: no FMP call was made for a blank query.
        verify(exactly = 0) { fmpAdapter.searchSymbol(any(), any()) }
    }

    // -------------------------------------------------------------------------
    // Guard test — no real FMP calls across any scenario
    // -------------------------------------------------------------------------

    @Test
    fun `search flow never calls getIncomeStatement confirming FMP boundary is mocked`() {
        every { fmpAdapter.searchSymbol("AAPL", any()) } returns listOf(
            SearchHitDto(symbol = "AAPL", name = "Apple Inc.", currency = "USD",
                stockExchange = "NASDAQ Global Select", exchangeShortName = "NASDAQ"),
        )

        mockMvc.get("/api/search?query=AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }

        // Financial statement endpoints must NEVER be called from the search flow.
        verify(exactly = 0) { fmpAdapter.getIncomeStatement(any(), any()) }
        verify(exactly = 0) { fmpAdapter.getBalanceSheet(any(), any()) }
        verify(exactly = 0) { fmpAdapter.getCashFlow(any(), any()) }
        verify(exactly = 0) { fmpAdapter.getKeyMetrics(any(), any()) }
    }
}
