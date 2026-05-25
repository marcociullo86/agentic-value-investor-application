package com.valueinvesting.webapp.fmp

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.valueinvesting.webapp.fmp.FmpEventLogger.EventType
import com.valueinvesting.webapp.persistence.entity.Stock
import com.valueinvesting.webapp.persistence.repository.FmpApiEventLogRepository
import com.valueinvesting.webapp.persistence.repository.StockRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

// US-030 / TSK-070: WireMock simulates FMP 429 then 200; Resilience4j retry succeeds;
// fmp_api_event_log receives FMP_429_RATE_LIMITED (ADR-016).
// [^src: management/kanban/EP-009-throttling-fmp-runbook/US-030-throttling-backend-fmp/TSK-070.md]
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class Fmp429RetryWireMockIT {

    companion object {
        private const val API_KEY = "wiremock-test-key"
        private const val SCENARIO = "fmp-429-then-200"

        private val wireMockServer: WireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            .also { it.start() }

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("app.fmp.base-url", wireMockServer::baseUrl)
            registry.add("app.fmp.api-key") { API_KEY }
            registry.add("app.fmp.mock") { false }
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMockServer.stop()
        }
    }

    @Autowired
    private lateinit var fmpAdapter: FmpAdapter

    @Autowired
    private lateinit var eventLogRepository: FmpApiEventLogRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    @BeforeEach
    fun resetStubsAndEventLog() {
        wireMockServer.resetAll()
        eventLogRepository.deleteAll()
        stockRepository.deleteAll()
        // fmp_api_event_log.ticker FK → stocks(ticker) (V008)
        stockRepository.save(Stock(ticker = "AAPL", companyName = "Apple Inc."))
    }

    @Test
    fun `429 from FMP mock is retried then succeeds and persists FMP_429_RATE_LIMITED event`() {
        val incomeFixture = ClassPathResource("fmp-fixtures/income-statement-aapl.json")
            .inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        // Audit 2026-05-23: post-migrazione /stable (TSK-050) FmpAdapterRestClient
        // passa il ticker come query param `symbol`, NON più nel path. Il path
        // canonico stable è `/income-statement?symbol=AAPL`. Lo stub WireMock va
        // adeguato al nuovo schema, altrimenti il match fallisce e WireMock
        // risponde 404 (interpretato dal client come FmpTickerNotFoundException).
        wireMockServer.stubFor(
            get(urlPathEqualTo("/income-statement"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("apikey", equalTo(API_KEY))
                .withQueryParam("limit", equalTo("10"))
                .inScenario(SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("after-429")
                .willReturn(
                    aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE),
                ),
        )
        wireMockServer.stubFor(
            get(urlPathEqualTo("/income-statement"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("apikey", equalTo(API_KEY))
                .withQueryParam("limit", equalTo("10"))
                .inScenario(SCENARIO)
                .whenScenarioStateIs("after-429")
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(incomeFixture),
                ),
        )

        val result = fmpAdapter.getIncomeStatement("AAPL")

        assertThat(result).isNotEmpty()
        assertThat(result[0].calendarYear).isEqualTo("2024")

        wireMockServer.verify(
            2,
            getRequestedFor(urlPathEqualTo("/income-statement"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("apikey", equalTo(API_KEY))
                .withQueryParam("limit", equalTo("10")),
        )

        val events = await429Events()
        assertThat(events).isNotEmpty()
        val event = events.first()
        assertThat(event.eventType).isEqualTo(EventType.FMP_429_RATE_LIMITED.name)
        assertThat(event.ticker).isEqualTo("AAPL")
        assertThat(event.endpoint).isEqualTo("income-statement")
        assertThat(event.httpStatus).isEqualTo(429)
    }

    private fun await429Events(): List<com.valueinvesting.webapp.persistence.entity.FmpApiEventLog> {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val rows = eventLogRepository.findFirst20ByEventTypeOrderByOccurredAtDesc(
                EventType.FMP_429_RATE_LIMITED.name,
            )
            if (rows.isNotEmpty()) return rows
            Thread.sleep(50)
        }
        return eventLogRepository.findFirst20ByEventTypeOrderByOccurredAtDesc(
            EventType.FMP_429_RATE_LIMITED.name,
        )
    }
}
