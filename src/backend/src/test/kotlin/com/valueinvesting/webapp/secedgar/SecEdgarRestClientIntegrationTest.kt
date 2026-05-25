package com.valueinvesting.webapp.secedgar

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.time.Duration

// Integration test per SecEdgarRestClient con WireMock.
//
// ARCHITETTURA: test standalone (no @SpringBootTest) — SecEdgarRestClient e
// ResilientSecEdgarAdapter vengono costruiti programmaticamente con proprietà
// ridotte (retry.maxAttempts=1, waitDurationMs=0) per evitare sleep lunghi nei
// test negativi (429/5xx). La Resilience4j chain è attiva per il test
// rate-limit.
//
// WireMock single server: sia `dataClient` (submissions) che `filesClient`
// (company_tickers) puntano alla stessa WireMock instance — i path sono
// disgiunti (/submissions/CIK*.json vs /files/company_tickers.json).
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-093.md]
// [^src: wiki/concepts/sec-filings-analysis.md §Accesso ai filing]
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecEdgarRestClientIntegrationTest {

    companion object {
        private val wireMockServer: WireMockServer =
            WireMockServer(wireMockConfig().dynamicPort())
                .also { it.start() }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMockServer.stop()
        }
    }

    // Fixtures letti da classpath
    private val tickersFixture: String by lazy {
        ClassPathResource("sec-edgar-fixtures/company_tickers.json")
            .inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
    private val submissionsFixture: String by lazy {
        ClassPathResource("sec-edgar-fixtures/submissions-CIK0000320193.json")
            .inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    // -------------------------------------------------------------------------
    // Costruzione wiring manuale
    // -------------------------------------------------------------------------

    /**
     * Costruisce SecEdgarRestClient con entrambe le URL (baseUrl + filesUrl) che
     * puntano al WireMock port corrente. Cache Caffeine fresca ad ogni test.
     */
    private fun buildRestClient(
        rateLimitPerSecond: Int = 100,          // default alto: no throttling in unit test
        retryMaxAttempts: Int = 1,              // no retry per test negativi veloci
        retryWaitMs: Long = 0L,
    ): Pair<SecEdgarRestClient, ResilientSecEdgarAdapter> {
        val wmUrl = wireMockServer.baseUrl()

        val properties = SecEdgarProperties(
            baseUrl = wmUrl,
            filesUrl = wmUrl,
            rateLimitPerSecond = rateLimitPerSecond,
            rateLimitTimeoutSeconds = 5L,
            userAgent = SecEdgarProperties.UserAgent("test@valueinvesting.example"),
            retry = SecEdgarProperties.Retry(
                maxAttempts = retryMaxAttempts,
                waitDurationMs = retryWaitMs,
            ),
        )

        val objectMapper = ObjectMapper().registerModule(JavaTimeModule())

        val cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(30))
            .maximumSize(1_000)
            .build<String, String>()

        val restClientBuilder = RestClient.builder()

        val restClient = SecEdgarRestClient(
            restClientBuilder = restClientBuilder,
            properties = properties,
            objectMapper = objectMapper,
            tickerToCikCache = cache,
        )

        // Resilience4j chain con gli stessi parametri del properties
        val cbConfig = CircuitBreakerConfig.custom()
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .recordExceptions(
                SecEdgarServiceException::class.java,
                SecEdgarRateLimitException::class.java,
            )
            .ignoreExceptions(SecEdgarAccessDeniedException::class.java)
            .build()
        val cb = CircuitBreaker.of("secEdgar-test", cbConfig)

        val retryConfig = RetryConfig.custom<Any>()
            .maxAttempts(retryMaxAttempts)
            .waitDuration(Duration.ofMillis(retryWaitMs))
            .retryExceptions(
                SecEdgarServiceException::class.java,
                SecEdgarRateLimitException::class.java,
            )
            .ignoreExceptions(SecEdgarAccessDeniedException::class.java)
            .build()
        val retry = Retry.of("secEdgar-test", retryConfig)

        val rlConfig = RateLimiterConfig.custom()
            .limitForPeriod(rateLimitPerSecond)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofSeconds(5L))
            .build()
        val rateLimiter = RateLimiter.of("secEdgar-test", rlConfig)

        val resilient = ResilientSecEdgarAdapter(
            delegate = restClient,
            secEdgarCircuitBreaker = cb,
            secEdgarRetry = retry,
            secEdgarRateLimiter = rateLimiter,
        )

        return Pair(restClient, resilient)
    }

    @BeforeEach
    fun resetWireMock() {
        wireMockServer.resetAll()
    }

    // =========================================================================
    // AC#1 — Rate-limit applicato: 12 chiamate → durata totale ≥ 1 secondo
    // =========================================================================
    //
    // Il RateLimiter Resilience4j (10 req/s) è configurato nel
    // ResilientSecEdgarAdapter. Per verificare il throttling:
    //   - stub WireMock restituisce 200 immediatamente (no delay lato server)
    //   - 12 chiamate logiche consecutive via ResilientSecEdgarAdapter
    //   - le prime 10 passano liberamente nel primo "period" di 1 secondo;
    //     le successive attendono il refresh (≥ 1 secondo)
    //   - durata totale misurata: ≥ 900 ms (tolleranza JVM ±10%)
    //
    // NB: il test usa rateLimitPerSecond=10 (default prod).
    @Test
    fun `AC1 - rate-limit 12 req via ResilientSecEdgarAdapter takes at least 900ms`() {
        // Stub per entrambe le URL (submissions usa dataClient → wmUrl/submissions/CIK*.json)
        wireMockServer.stubFor(
            get(urlPathMatching("/submissions/CIK.+\\.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(submissionsFixture),
                ),
        )

        val (_, resilient) = buildRestClient(rateLimitPerSecond = 10, retryMaxAttempts = 1, retryWaitMs = 0L)

        val cik = "0000320193"
        val requestCount = 12

        val startNs = System.nanoTime()
        repeat(requestCount) {
            resilient.listFilings(cik = cik, formTypes = listOf("10-K", "10-Q"), limit = 5)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L

        // 12 richieste a 10 req/s: al limite del secondo bisogna attendere il secondo period.
        // Tolleranza: ≥ 900 ms per assorbire jitter JVM (il RateLimiter Resilience4j
        // applica sleeps deterministici via token-bucket).
        assertThat(elapsedMs)
            .`as`("12 richieste a 10 req/s devono richiedere ≥ 900 ms (got: ${elapsedMs}ms)")
            .isGreaterThanOrEqualTo(900L)

        // Verifica che WireMock abbia ricevuto tutte e 12 le richieste
        val received = wireMockServer.allServeEvents.count { it.request.url.contains("/submissions/CIK") }
        assertThat(received)
            .`as`("WireMock deve aver ricevuto 12 richieste submissions")
            .isEqualTo(requestCount)
    }

    // =========================================================================
    // AC#2 — Gestione 429: SecEdgarRateLimitException propagata
    // =========================================================================
    //
    // WireMock risponde 429 al primo (e unico, retry=1) tentativo.
    // SecEdgarRestClient mappa 429 → SecEdgarRateLimitException.
    // ResilientSecEdgarAdapter con retry.maxAttempts=1 non ri-prova.
    @Test
    fun `AC2 - 429 from submissions endpoint throws SecEdgarRateLimitException`() {
        wireMockServer.stubFor(
            get(urlPathMatching("/submissions/CIK.+\\.json"))
                .willReturn(
                    aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "1")
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE),
                ),
        )

        val (restClient, _) = buildRestClient()

        assertThatThrownBy {
            restClient.listFilings(
                cik = "0000320193",
                formTypes = listOf("10-K"),
                limit = 5,
            )
        }
            .isInstanceOf(SecEdgarRateLimitException::class.java)
            .hasMessageContaining("429")
    }

    // =========================================================================
    // AC#3 — Gestione 5xx: SecEdgarServiceException con httpStatus=503
    // =========================================================================
    @Test
    fun `AC3 - 503 from submissions endpoint throws SecEdgarServiceException with status 503`() {
        wireMockServer.stubFor(
            get(urlPathMatching("/submissions/CIK.+\\.json"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""{"error":"Service Unavailable"}"""),
                ),
        )

        val (restClient, _) = buildRestClient()

        assertThatThrownBy {
            restClient.listFilings(
                cik = "0000320193",
                formTypes = listOf("10-K"),
                limit = 5,
            )
        }
            .isInstanceOf(SecEdgarServiceException::class.java)
            .satisfies({ ex ->
                assertThat((ex as SecEdgarServiceException).httpStatus).isEqualTo(503)
            })
    }

    // =========================================================================
    // AC#4 — CIK inesistente (404 su submissions): listFilings ritorna emptyList
    // =========================================================================
    //
    // Nota: la logica "ticker non in mappa" → null senza HTTP call riguarda
    // resolveCikFromTicker. Qui testiamo il path distinto: il CIK è strutturalmente
    // valido (10 cifre) ma la risorsa submissions non esiste (404). In questo caso
    // SecEdgarRestClient cattura il sentinel e ritorna emptyList() senza eccezione.
    //
    // Differenza:
    //   - Ticker "UNKNOWN" non è in company_tickers.json → resolveCikFromTicker()
    //     ritorna null SENZA alcuna HTTP call al submissions endpoint.
    //   - CIK "9999999999" (non esistente ma formato valido) → listFilings() fa GET
    //     /submissions/CIK9999999999.json → 404 → emptyList() (SecEdgarCikNotFoundSentinel).
    @Test
    fun `AC4 - 404 on submissions for unknown CIK returns emptyList without exception`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/submissions/CIK9999999999.json"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE),
                ),
        )

        val (restClient, _) = buildRestClient()

        val result = restClient.listFilings(
            cik = "9999999999",
            formTypes = listOf("10-K", "10-Q"),
            limit = 10,
        )

        assertThat(result)
            .`as`("404 su CIK inesistente deve produrre lista vuota, non eccezione")
            .isEmpty()
    }

    // =========================================================================
    // AC#5 — Cache CIK hit: 2 lookup stessa ticker → 1 sola HTTP request
    // =========================================================================
    //
    // La cache Caffeine è bulk-populated al primo miss (cache vuota → download
    // dell'intero company_tickers.json). La seconda chiamata legge dalla cache
    // in-memory → 0 nuovi HTTP request.
    @Test
    fun `AC5 - cache hit - two resolveCikFromTicker calls for AAPL produce exactly one HTTP request`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/files/company_tickers.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(tickersFixture),
                ),
        )

        val (restClient, _) = buildRestClient()

        val cik1 = restClient.resolveCikFromTicker("AAPL")
        val cik2 = restClient.resolveCikFromTicker("AAPL")

        assertThat(cik1).isEqualTo("0000320193")
        assertThat(cik2).isEqualTo("0000320193")

        // Deve esserci esattamente 1 richiesta a /files/company_tickers.json
        val tickerRequests = wireMockServer.allServeEvents
            .count { it.request.url.contains("/files/company_tickers.json") }
        assertThat(tickerRequests)
            .`as`("La seconda chiamata AAPL deve provenire dalla cache — nessuna nuova HTTP request")
            .isEqualTo(1)
    }

    // =========================================================================
    // AC#5b — resolveCikFromTicker per ticker non in mappa → null senza HTTP
    // =========================================================================
    //
    // Dopo il bulk-populate iniziale, un ticker sconosciuto ("UNKNOWN") non è
    // nella cache → resolveCikFromTicker() ritorna null. Poiché la cache NON è
    // vuota (10k entries caricate), NON viene fatto un secondo HTTP GET.
    @Test
    fun `AC5b - unknown ticker returns null without additional HTTP request after cache populated`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/files/company_tickers.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(tickersFixture),
                ),
        )

        val (restClient, _) = buildRestClient()

        // Prima chiamata: warm-up della cache
        restClient.resolveCikFromTicker("AAPL")

        // Seconda chiamata: ticker non presente — deve ritornare null senza HTTP
        val result = restClient.resolveCikFromTicker("UNKNOWN_TICKER_XYZ")

        assertThat(result).isNull()

        // Solo 1 HTTP request totale (quello del warm-up)
        val tickerRequests = wireMockServer.allServeEvents
            .count { it.request.url.contains("/files/company_tickers.json") }
        assertThat(tickerRequests)
            .`as`("Ticker sconosciuto post-cache-populate non deve generare ulteriori HTTP request")
            .isEqualTo(1)
    }

    // =========================================================================
    // AC#6 — User-Agent header obbligatorio su tutte le request SEC
    // =========================================================================
    //
    // SEC fair-access policy: ogni richiesta DEVE portare un User-Agent nel
    // formato "ValueInvesting-App/1.0 {email}". Verificato con WireMock
    // `verify()` su header pattern matching.
    @Test
    fun `AC6 - User-Agent header matches ValueInvesting-App pattern on files request`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/files/company_tickers.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(tickersFixture),
                ),
        )

        val (restClient, _) = buildRestClient()
        restClient.resolveCikFromTicker("AAPL")

        wireMockServer.verify(
            getRequestedFor(urlPathEqualTo("/files/company_tickers.json"))
                .withHeader("User-Agent", matching("ValueInvesting-App/1\\.0 .+")),
        )
    }

    @Test
    fun `AC6b - User-Agent header matches ValueInvesting-App pattern on submissions request`() {
        wireMockServer.stubFor(
            get(urlPathMatching("/submissions/CIK.+\\.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(submissionsFixture),
                ),
        )

        val (restClient, _) = buildRestClient()
        restClient.listFilings(
            cik = "0000320193",
            formTypes = listOf("10-K"),
            limit = 3,
        )

        wireMockServer.verify(
            getRequestedFor(urlPathMatching("/submissions/CIK.+\\.json"))
                .withHeader("User-Agent", matching("ValueInvesting-App/1\\.0 .+")),
        )
    }

    // =========================================================================
    // AC#7 — listFilings parse: 2 filing 10-K + 1 filing 10-Q restituiti
    // =========================================================================
    //
    // Test della logica di parsing + mapping a SecFilingMetadata. Verifica che
    // il filtro per formTypes e il limit siano applicati correttamente.
    @Test
    fun `happy path - listFilings returns filtered SecFilingMetadata from WireMock fixture`() {
        wireMockServer.stubFor(
            get(urlPathMatching("/submissions/CIK.+\\.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(submissionsFixture),
                ),
        )

        val (restClient, _) = buildRestClient()

        // Richiede solo 10-K (2 presenti nel fixture, limit=5)
        val onlyTenK = restClient.listFilings(
            cik = "0000320193",
            formTypes = listOf("10-K"),
            limit = 5,
        )
        assertThat(onlyTenK).hasSize(2)
        assertThat(onlyTenK[0].formType).isEqualTo("10-K")
        assertThat(onlyTenK[0].accessionNumber).isEqualTo("0000320193-24-000123")

        // Richiede 10-K + 10-Q (3 totali), limit=2
        wireMockServer.resetAll()
        wireMockServer.stubFor(
            get(urlPathMatching("/submissions/CIK.+\\.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(submissionsFixture),
                ),
        )
        val withLimit = restClient.listFilings(
            cik = "0000320193",
            formTypes = listOf("10-K", "10-Q"),
            limit = 2,
        )
        assertThat(withLimit).hasSize(2)
    }

    // =========================================================================
    // AC#2b — 429 su /files/company_tickers.json → SecEdgarRateLimitException
    // =========================================================================
    @Test
    fun `AC2b - 429 on company_tickers endpoint throws SecEdgarRateLimitException`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/files/company_tickers.json"))
                .willReturn(
                    aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE),
                ),
        )

        val (restClient, _) = buildRestClient()

        assertThatThrownBy {
            restClient.resolveCikFromTicker("AAPL")
        }.isInstanceOf(SecEdgarRateLimitException::class.java)
    }

    // =========================================================================
    // AC#3b — 5xx su /files/company_tickers.json → SecEdgarServiceException
    // =========================================================================
    @Test
    fun `AC3b - 503 on company_tickers endpoint throws SecEdgarServiceException`() {
        wireMockServer.stubFor(
            get(urlPathEqualTo("/files/company_tickers.json"))
                .willReturn(
                    aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE),
                ),
        )

        val (restClient, _) = buildRestClient()

        assertThatThrownBy {
            restClient.resolveCikFromTicker("AAPL")
        }
            .isInstanceOf(SecEdgarServiceException::class.java)
            .satisfies({ ex ->
                assertThat((ex as SecEdgarServiceException).httpStatus).isEqualTo(503)
            })
    }
}
