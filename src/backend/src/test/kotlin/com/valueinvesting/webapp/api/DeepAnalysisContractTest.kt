package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpFixtureFactory
import com.valueinvesting.webapp.persistence.entity.FilingBlobEntity
import com.valueinvesting.webapp.persistence.repository.FilingBlobRepository
import com.valueinvesting.webapp.persistence.repository.FilingChunkRepository
import com.valueinvesting.webapp.service.Filing10KQDownloaderService
import com.valueinvesting.webapp.service.FilingRagService
import com.valueinvesting.webapp.service.IndexResult
import com.valueinvesting.webapp.service.LivelloRischio
import com.valueinvesting.webapp.service.MungerInversionAnalyzer
import com.valueinvesting.webapp.service.MungerInversionReport
import com.valueinvesting.webapp.service.NewsClassificationSummary
import com.valueinvesting.webapp.service.NewsSentimentResult
import com.valueinvesting.webapp.service.NewsSentimentService
import com.valueinvesting.webapp.service.PriceActionAnalyzer
import com.valueinvesting.webapp.service.PriceActionSnapshot
import com.valueinvesting.webapp.service.SentimentClass
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
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
import java.nio.file.Path
import java.time.LocalDate

/**
 * Contract test: OpenAPI drift guard for GET /api/analysis/{ticker}/deep (TSK-163).
 *
 * Verifies that the runtime JSON response structure matches the canonical
 * openapi.yaml schema for DeepAnalysisResponse and its nested RoeBlock.
 * Prevents silent schema drift between implementation and contract.
 *
 * [^src: design_&_architecture/api/openapi.yaml §DeepAnalysisResponse]
 * [^src: design_&_architecture/decisions/ADR-020-roe-lookback-policy-deep-analysis.md §Specifica payload Deep Analysis]
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@Tag("contract")
class DeepAnalysisContractTest {

    companion object {
        private val JSON: ObjectMapper = jacksonObjectMapper()
        private val YAML: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_deep_contract_test")
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

    @MockkBean
    private lateinit var fmpAdapter: FmpAdapter

    @MockkBean
    private lateinit var filing10KQDownloaderService: Filing10KQDownloaderService

    @MockkBean
    private lateinit var filingRagService: FilingRagService

    // Post EP-011 split (V028): mockati come negli altri test integration
    // perché analyze() ora interroga questi due repo invece di chiamare
    // fetchAndCache + indexFiling.
    @MockkBean
    private lateinit var filingBlobRepository: FilingBlobRepository

    @MockkBean
    private lateinit var filingChunkRepository: FilingChunkRepository

    @MockkBean
    private lateinit var priceActionAnalyzer: PriceActionAnalyzer

    // Mockati per il contract test "invoke_llm=true" (TSK-308 F1): l'analyze
    // con ramo LLM invoca questi due collaboratori; per asserire la shape di
    // newsSentiment.items a runtime li stubbiamo qui senza toccare i bean
    // reali (LLM/embedding/FMP).
    @MockkBean
    private lateinit var mungerInversionAnalyzer: MungerInversionAnalyzer

    @MockkBean
    private lateinit var newsSentimentService: NewsSentimentService

    @Value("\${contract.openapi.canonical}")
    private lateinit var canonicalOpenApiPath: String

    @BeforeEach
    fun resetMocks() {
        clearMocks(fmpAdapter, filing10KQDownloaderService, filingRagService,
            filingBlobRepository, filingChunkRepository, priceActionAnalyzer,
            mungerInversionAnalyzer, newsSentimentService,
            answers = false, recordedCalls = true)
        FmpFixtureFactory.stubSuccessfulFmp(fmpAdapter, "AAPL")
        stubDeepAnalysisDeps("AAPL")
    }

    @Test
    @DisplayName("GET /api/analysis/{ticker}/deep returns 200 with application/json")
    fun `deep endpoint returns 200 OK`() {
        mockMvc.get("/api/analysis/AAPL/deep") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
        }
    }

    @Test
    @DisplayName("Response contains top-level fields: ticker, generatedAt, roe, verdict, priceAction (schema conformance)")
    fun `response has required top-level fields per OpenAPI schema`() {
        val body = callDeepAndParse("AAPL")

        assertThat(body.has("ticker")).isTrue()
        assertThat(body.has("generatedAt")).isTrue()
        assertThat(body.has("roe")).isTrue()
        assertThat(body.has("verdict")).isTrue()
        assertThat(body.has("priceAction")).isTrue()
        assertThat(body.has("ruleEngineResults")).isTrue()
        assertThat(body.has("llmStatus")).isTrue()

        assertThat(body.get("ticker").isTextual).isTrue()
        assertThat(body.get("generatedAt").isTextual).isTrue()
        assertThat(body.get("roe").isObject).isTrue()
    }

    @Test
    @DisplayName("RoeBlock contains exactly the 4 fields defined in openapi.yaml: fiveYearAvg, tenYearAvg, fiveYearDataPoints, tenYearDataPoints")
    fun `roe block has all four fields per OpenAPI RoeBlock schema`() {
        val body = callDeepAndParse("AAPL")
        val roe = body.get("roe")

        assertThat(roe.has("fiveYearAvg")).isTrue()
        assertThat(roe.has("tenYearAvg")).isTrue()
        assertThat(roe.has("fiveYearDataPoints")).isTrue()
        assertThat(roe.has("tenYearDataPoints")).isTrue()
    }

    @Test
    @DisplayName("RoeBlock field types conform to OpenAPI: avg fields are number|null, dataPoints are integer")
    fun `roe block field types match OpenAPI schema`() {
        val body = callDeepAndParse("AAPL")
        val roe = body.get("roe")

        assertThat(roe.get("fiveYearAvg").isNumber || roe.get("fiveYearAvg").isNull)
            .withFailMessage("fiveYearAvg must be number or null, got: ${roe.get("fiveYearAvg")}")
            .isTrue()
        assertThat(roe.get("tenYearAvg").isNumber || roe.get("tenYearAvg").isNull)
            .withFailMessage("tenYearAvg must be number or null, got: ${roe.get("tenYearAvg")}")
            .isTrue()
        assertThat(roe.get("fiveYearDataPoints").isInt)
            .withFailMessage("fiveYearDataPoints must be integer, got: ${roe.get("fiveYearDataPoints")}")
            .isTrue()
        assertThat(roe.get("tenYearDataPoints").isInt)
            .withFailMessage("tenYearDataPoints must be integer, got: ${roe.get("tenYearDataPoints")}")
            .isTrue()
    }

    @Test
    @DisplayName("fiveYearDataPoints in [0,5] and tenYearDataPoints in [0,10] per schema bounds")
    fun `roe block dataPoints within schema bounds`() {
        val body = callDeepAndParse("AAPL")
        val roe = body.get("roe")

        val fivePts = roe.get("fiveYearDataPoints").asInt()
        val tenPts = roe.get("tenYearDataPoints").asInt()

        assertThat(fivePts).isBetween(0, 5)
        assertThat(tenPts).isBetween(0, 10)
    }

    @Test
    @DisplayName("Runtime OpenAPI includes DeepAnalysisResponse and RoeBlock schemas")
    fun `runtime springdoc exposes DeepAnalysisResponse and RoeBlock schemas`() {
        val runtimeDoc = mockMvc.get("/api/openapi.json") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val root = JSON.readTree(runtimeDoc.response.contentAsString)
        val schemas = root.path("components").path("schemas")

        assertThat(schemas.has("DeepAnalysisResponse"))
            .withFailMessage("Runtime OpenAPI missing DeepAnalysisResponse schema")
            .isTrue()
        assertThat(schemas.has("RoeBlock"))
            .withFailMessage("Runtime OpenAPI missing RoeBlock schema")
            .isTrue()
    }

    @Test
    @DisplayName("Runtime RoeBlock schema fields match canonical openapi.yaml definition")
    fun `runtime RoeBlock schema matches canonical`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val canonicalRoeBlock = canonicalDoc.path("components").path("schemas").path("RoeBlock")
        val canonicalProps = canonicalRoeBlock.path("properties").fieldNames().asSequence().toSet()

        val runtimeDoc = mockMvc.get("/api/openapi.json") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        val runtimeRoot = JSON.readTree(runtimeDoc.response.contentAsString)
        val runtimeRoeBlock = runtimeRoot.path("components").path("schemas").path("RoeBlock")
        val runtimeProps = runtimeRoeBlock.path("properties").fieldNames().asSequence().toSet()

        assertThat(runtimeProps)
            .withFailMessage(
                "RoeBlock property drift:\n  canonical: $canonicalProps\n  runtime: $runtimeProps\n" +
                    "  missing in runtime: ${canonicalProps - runtimeProps}\n  extra in runtime: ${runtimeProps - canonicalProps}",
            )
            .containsAll(canonicalProps)
    }

    @Test
    @DisplayName("Response ticker is uppercased regardless of input casing")
    fun `ticker is uppercased in response`() {
        stubDeepAnalysisDeps("aapl")
        val body = callDeepAndParse("aapl")

        assertThat(body.get("ticker").asText()).isEqualTo("AAPL")
    }

    @Test
    @DisplayName("Response Cache-Control header is no-store")
    fun `response has no-store cache control`() {
        mockMvc.get("/api/analysis/AAPL/deep") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
            header { string("Cache-Control", "no-store") }
        }
    }

    @Test
    @DisplayName("Default invoke_llm=false produces llmStatus=NOT_INVOKED with null LLM blocks")
    fun `default invoke_llm false produces NOT_INVOKED status`() {
        val body = callDeepAndParse("AAPL")

        assertThat(body.get("llmStatus").asText()).isEqualTo("NOT_INVOKED")
        assertThat(body.get("mungerReport").isNull).isTrue()
        assertThat(body.get("newsSentiment").isNull).isTrue()
    }

    // ---- VerdictBlock contract tests (TSK-121) ----

    @Test
    @DisplayName("VerdictBlock contains all required fields per OpenAPI VerdictBlock schema")
    fun `verdict block has all required fields`() {
        val body = callDeepAndParse("AAPL")
        val verdict = body.get("verdict")

        assertThat(verdict.isObject).isTrue()
        assertThat(verdict.has("verdettoClasse")).isTrue()
        assertThat(verdict.has("positionSizePct")).isTrue()
        assertThat(verdict.has("partialBasis")).isTrue()
        assertThat(verdict.has("motivazioneAggregata")).isTrue()
        assertThat(verdict.has("ruleCountGreen")).isTrue()
        assertThat(verdict.has("ruleCountYellow")).isTrue()
        assertThat(verdict.has("ruleCountRed")).isTrue()
        assertThat(verdict.has("livelloRischio")).isTrue()
        assertThat(verdict.has("newsSentimentDominante")).isTrue()
    }

    @Test
    @DisplayName("VerdictBlock field types match OpenAPI schema")
    fun `verdict block field types match schema`() {
        val body = callDeepAndParse("AAPL")
        val verdict = body.get("verdict")

        assertThat(verdict.get("verdettoClasse").isTextual).isTrue()
        assertThat(verdict.get("positionSizePct").isNumber).isTrue()
        assertThat(verdict.get("partialBasis").isBoolean).isTrue()
        assertThat(verdict.get("motivazioneAggregata").isTextual).isTrue()
        assertThat(verdict.get("ruleCountGreen").isInt).isTrue()
        assertThat(verdict.get("ruleCountYellow").isInt).isTrue()
        assertThat(verdict.get("ruleCountRed").isInt).isTrue()
        assertThat(verdict.get("livelloRischio").isTextual).isTrue()
        assertThat(verdict.get("newsSentimentDominante").isTextual).isTrue()
    }

    @Test
    @DisplayName("Runtime VerdictBlock schema fields match canonical openapi.yaml — drift guard")
    fun `runtime VerdictBlock schema matches canonical`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val canonicalBlock = canonicalDoc.path("components").path("schemas").path("VerdictBlock")
        val canonicalProps = canonicalBlock.path("properties").fieldNames().asSequence().toSet()

        val runtimeDoc = mockMvc.get("/api/openapi.json") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        val runtimeRoot = JSON.readTree(runtimeDoc.response.contentAsString)
        val runtimeBlock = runtimeRoot.path("components").path("schemas").path("VerdictBlock")
        val runtimeProps = runtimeBlock.path("properties").fieldNames().asSequence().toSet()

        assertThat(runtimeProps)
            .withFailMessage(
                "VerdictBlock property drift:\n  canonical: $canonicalProps\n  runtime: $runtimeProps\n" +
                    "  missing in runtime: ${canonicalProps - runtimeProps}\n  extra in runtime: ${runtimeProps - canonicalProps}",
            )
            .containsAll(canonicalProps)
    }

    @Test
    @DisplayName("Drift guard: verdettoClasse exists in canonical spec (removal would break this assertion)")
    fun `drift guard verdettoClasse present in canonical spec`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val verdictBlock = canonicalDoc.path("components").path("schemas").path("VerdictBlock")
        val required = verdictBlock.path("required").map { it.asText() }.toSet()
        val properties = verdictBlock.path("properties").fieldNames().asSequence().toSet()

        assertThat(properties)
            .withFailMessage("verdettoClasse removed from VerdictBlock properties — contract broken")
            .contains("verdettoClasse")
        assertThat(required)
            .withFailMessage("verdettoClasse removed from VerdictBlock required — contract broken")
            .contains("verdettoClasse")
    }

    // ---- PriceActionBlock contract tests (TSK-121) ----

    @Test
    @DisplayName("PriceActionBlock contains required fields per OpenAPI schema")
    fun `priceAction block has required fields`() {
        val body = callDeepAndParse("AAPL")
        val pa = body.get("priceAction")

        assertThat(pa.isObject).isTrue()
        assertThat(pa.has("panicDiscount")).isTrue()
        assertThat(pa.has("deteriorationWarning")).isTrue()
        assertThat(pa.has("seriesDays")).isTrue()
        assertThat(pa.has("priceNow")).isTrue()
        assertThat(pa.has("max52w")).isTrue()
        assertThat(pa.has("min52w")).isTrue()
        assertThat(pa.has("drawdownPct")).isTrue()
        assertThat(pa.has("trend3mPct")).isTrue()
        assertThat(pa.has("ma50")).isTrue()
        assertThat(pa.has("ma200")).isTrue()
    }

    @Test
    @DisplayName("PriceActionBlock field types conform to OpenAPI: booleans + integer + nullable numbers")
    fun `priceAction block field types match schema`() {
        val body = callDeepAndParse("AAPL")
        val pa = body.get("priceAction")

        assertThat(pa.get("panicDiscount").isBoolean).isTrue()
        assertThat(pa.get("deteriorationWarning").isBoolean).isTrue()
        assertThat(pa.get("seriesDays").isInt).isTrue()
        assertThat(pa.get("priceNow").isNumber || pa.get("priceNow").isNull).isTrue()
        assertThat(pa.get("max52w").isNumber || pa.get("max52w").isNull).isTrue()
        assertThat(pa.get("min52w").isNumber || pa.get("min52w").isNull).isTrue()
    }

    @Test
    @DisplayName("Runtime PriceActionBlock schema fields match canonical openapi.yaml — drift guard")
    fun `runtime PriceActionBlock schema matches canonical`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val canonicalBlock = canonicalDoc.path("components").path("schemas").path("PriceActionBlock")
        val canonicalProps = canonicalBlock.path("properties").fieldNames().asSequence().toSet()

        val runtimeDoc = mockMvc.get("/api/openapi.json") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        val runtimeRoot = JSON.readTree(runtimeDoc.response.contentAsString)
        val runtimeBlock = runtimeRoot.path("components").path("schemas").path("PriceActionBlock")
        val runtimeProps = runtimeBlock.path("properties").fieldNames().asSequence().toSet()

        assertThat(runtimeProps)
            .withFailMessage(
                "PriceActionBlock property drift:\n  canonical: $canonicalProps\n  runtime: $runtimeProps\n" +
                    "  missing in runtime: ${canonicalProps - runtimeProps}\n  extra in runtime: ${runtimeProps - canonicalProps}",
            )
            .containsAll(canonicalProps)
    }

    // ---- PositionSizeBlock contract tests (TSK-121) ----

    @Test
    @DisplayName("Response contains positionSize field (nullable per schema)")
    fun `response has positionSize field`() {
        val body = callDeepAndParse("AAPL")
        assertThat(body.has("positionSize")).isTrue()
    }

    @Test
    @DisplayName("Runtime OpenAPI exposes PositionSizeBlock schema with all canonical fields — drift guard")
    fun `runtime PositionSizeBlock schema matches canonical`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val canonicalBlock = canonicalDoc.path("components").path("schemas").path("PositionSizeBlock")
        val canonicalProps = canonicalBlock.path("properties").fieldNames().asSequence().toSet()

        val runtimeDoc = mockMvc.get("/api/openapi.json") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        val runtimeRoot = JSON.readTree(runtimeDoc.response.contentAsString)
        val runtimeBlock = runtimeRoot.path("components").path("schemas").path("PositionSizeBlock")

        assertThat(runtimeBlock.isMissingNode)
            .withFailMessage("Runtime OpenAPI missing PositionSizeBlock schema")
            .isFalse()

        val runtimeProps = runtimeBlock.path("properties").fieldNames().asSequence().toSet()
        assertThat(runtimeProps)
            .withFailMessage(
                "PositionSizeBlock property drift:\n  canonical: $canonicalProps\n  runtime: $runtimeProps\n" +
                    "  missing in runtime: ${canonicalProps - runtimeProps}",
            )
            .containsAll(canonicalProps)
    }

    // ---- MungerReportBlock contract tests (TSK-121) ----

    @Test
    @DisplayName("Runtime OpenAPI exposes MungerReportBlock schema with all canonical fields — drift guard")
    fun `runtime MungerReportBlock schema matches canonical`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val canonicalBlock = canonicalDoc.path("components").path("schemas").path("MungerReportBlock")
        val canonicalProps = canonicalBlock.path("properties").fieldNames().asSequence().toSet()

        val runtimeDoc = mockMvc.get("/api/openapi.json") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        val runtimeRoot = JSON.readTree(runtimeDoc.response.contentAsString)
        val runtimeBlock = runtimeRoot.path("components").path("schemas").path("MungerReportBlock")

        assertThat(runtimeBlock.isMissingNode)
            .withFailMessage("Runtime OpenAPI missing MungerReportBlock schema")
            .isFalse()

        val runtimeProps = runtimeBlock.path("properties").fieldNames().asSequence().toSet()
        assertThat(runtimeProps)
            .withFailMessage(
                "MungerReportBlock property drift:\n  canonical: $canonicalProps\n  runtime: $runtimeProps\n" +
                    "  missing in runtime: ${canonicalProps - runtimeProps}",
            )
            .containsAll(canonicalProps)
    }

    // ---- NewsSentimentBlock contract tests (TSK-121) ----

    @Test
    @DisplayName("Runtime OpenAPI exposes NewsSentimentBlock schema with all canonical fields — drift guard")
    fun `runtime NewsSentimentBlock schema matches canonical`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val canonicalBlock = canonicalDoc.path("components").path("schemas").path("NewsSentimentBlock")
        val canonicalProps = canonicalBlock.path("properties").fieldNames().asSequence().toSet()

        val runtimeDoc = mockMvc.get("/api/openapi.json") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        val runtimeRoot = JSON.readTree(runtimeDoc.response.contentAsString)
        val runtimeBlock = runtimeRoot.path("components").path("schemas").path("NewsSentimentBlock")

        assertThat(runtimeBlock.isMissingNode)
            .withFailMessage("Runtime OpenAPI missing NewsSentimentBlock schema")
            .isFalse()

        val runtimeProps = runtimeBlock.path("properties").fieldNames().asSequence().toSet()
        assertThat(runtimeProps)
            .withFailMessage(
                "NewsSentimentBlock property drift:\n  canonical: $canonicalProps\n  runtime: $runtimeProps\n" +
                    "  missing in runtime: ${canonicalProps - runtimeProps}",
            )
            .containsAll(canonicalProps)
    }

    // ---- FilingRef contract tests (TSK-121) ----

    @Test
    @DisplayName("filingsUsed is a non-empty array with FilingRef fields: accessionNumber, formType, filingDate")
    fun `filingsUsed contains FilingRef objects with required fields`() {
        val body = callDeepAndParse("AAPL")
        val filingsUsed = body.get("filingsUsed")

        assertThat(filingsUsed.isArray).isTrue()
        assertThat(filingsUsed.size()).isGreaterThan(0)

        val first = filingsUsed.get(0)
        assertThat(first.has("accessionNumber")).isTrue()
        assertThat(first.has("formType")).isTrue()
        assertThat(first.has("filingDate")).isTrue()
        assertThat(first.get("accessionNumber").isTextual).isTrue()
        assertThat(first.get("formType").isTextual).isTrue()
        assertThat(first.get("filingDate").isTextual).isTrue()
    }

    @Test
    @DisplayName("Runtime OpenAPI exposes FilingRef schema with all canonical fields — drift guard")
    fun `runtime FilingRef schema matches canonical`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val canonicalBlock = canonicalDoc.path("components").path("schemas").path("FilingRef")
        val canonicalProps = canonicalBlock.path("properties").fieldNames().asSequence().toSet()

        val runtimeDoc = mockMvc.get("/api/openapi.json") {
            accept(MediaType.APPLICATION_JSON)
        }.andReturn()
        val runtimeRoot = JSON.readTree(runtimeDoc.response.contentAsString)
        val runtimeBlock = runtimeRoot.path("components").path("schemas").path("FilingRef")

        assertThat(runtimeBlock.isMissingNode)
            .withFailMessage("Runtime OpenAPI missing FilingRef schema")
            .isFalse()

        val runtimeProps = runtimeBlock.path("properties").fieldNames().asSequence().toSet()
        assertThat(runtimeProps)
            .withFailMessage(
                "FilingRef property drift:\n  canonical: $canonicalProps\n  runtime: $runtimeProps\n" +
                    "  missing in runtime: ${canonicalProps - runtimeProps}",
            )
            .containsAll(canonicalProps)
    }

    // ---- Response codes documented in spec (TSK-121) ----

    @Test
    @DisplayName("Canonical OpenAPI documents all 4 response codes (200/404/422/503) for /deep endpoint")
    fun `canonical spec documents all response codes for deep endpoint`() {
        val canonicalDoc = YAML.readTree(Path.of(canonicalOpenApiPath).toFile())
        val deepResponses = canonicalDoc.path("paths")
            .path("/api/analysis/{ticker}/deep")
            .path("get")
            .path("responses")

        assertThat(deepResponses.has("200"))
            .withFailMessage("Canonical spec missing 200 response for /deep")
            .isTrue()
        assertThat(deepResponses.has("404"))
            .withFailMessage("Canonical spec missing 404 response for /deep")
            .isTrue()
        assertThat(deepResponses.has("422"))
            .withFailMessage("Canonical spec missing 422 response for /deep")
            .isTrue()
        assertThat(deepResponses.has("503"))
            .withFailMessage("Canonical spec missing 503 response for /deep")
            .isTrue()
    }

    // ---- Additional top-level fields (TSK-121) ----

    @Test
    @DisplayName("Response contains llmCalls (integer) and totalDurationMs (integer) per schema")
    fun `response has llmCalls and totalDurationMs fields`() {
        val body = callDeepAndParse("AAPL")

        assertThat(body.has("llmCalls")).isTrue()
        assertThat(body.get("llmCalls").isInt).isTrue()
        assertThat(body.has("totalDurationMs")).isTrue()
        assertThat(body.get("totalDurationMs").isNumber).isTrue()
    }

    // ---- NewsSentimentBlock RUNTIME assertion (TSK-308 AC1 / F1) ----

    @Test
    @DisplayName("invoke_llm=true: newsSentiment.items is a non-empty NewsItem array at runtime")
    fun `invoke_llm true returns newsSentiment items array with NewsItem shape`() {
        // Stub collaboratori LLM per non sollevare FilingsNotIndexedException
        // né chiamare LLM/FMP reali: il chunkCount è già stubbato a 1L in
        // stubDeepAnalysisDeps, e qui forniamo Munger + NewsSentiment con
        // payload deterministico.
        stubMungerAndNewsSentiment("AAPL")

        val result = mockMvc.get("/api/analysis/AAPL/deep?invoke_llm=true") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()
        val body = JSON.readTree(result.response.contentAsString)

        // (a) newsSentiment è un oggetto (non null) sul ramo LLM.
        val news = body.get("newsSentiment")
        assertThat(news).isNotNull
        assertThat(news.isObject).isTrue()
        assertThat(news.isNull).isFalse()

        // (b) items è un array non vuoto.
        val items = news.get("items")
        assertThat(items.isArray)
            .withFailMessage("newsSentiment.items must be array, got: $items")
            .isTrue()
        assertThat(items.size())
            .withFailMessage("newsSentiment.items expected non-empty, got size=${items.size()}")
            .isGreaterThan(0)

        // (c) ogni item ha la shape NewsItem (headline, textExcerpt,
        //     sentimentClass, motivazione, url) con i campi richiesti non-null.
        items.forEach { item ->
            assertThat(item.has("headline")).isTrue()
            assertThat(item.has("textExcerpt")).isTrue()
            assertThat(item.has("sentimentClass")).isTrue()
            assertThat(item.has("motivazione")).isTrue()
            assertThat(item.has("url")).isTrue()
            // textExcerpt + sentimentClass devono essere valorizzati (atteso
            // dalla US-091): testi non-blank, classe enum valida.
            assertThat(item.get("textExcerpt").isTextual).isTrue()
            assertThat(item.get("textExcerpt").asText()).isNotBlank()
            assertThat(item.get("sentimentClass").isTextual).isTrue()
            assertThat(item.get("sentimentClass").asText())
                .isIn("TEMPORARY_PANIC", "STRUCTURAL_DAMAGE", "NEUTRAL")
            // motivazione + url possono in teoria essere null per certi
            // payload futuri ma nel nostro stub li popoliamo entrambi.
            assertThat(item.get("motivazione").asText()).isNotBlank()
            assertThat(item.get("url").asText()).isNotBlank()
        }
    }

    private fun stubMungerAndNewsSentiment(ticker: String) {
        val t = ticker.uppercase()
        every {
            mungerInversionAnalyzer.analyze(any(), any(), any())
        } returns MungerInversionReport(
            ticker = t,
            livelloRischio = LivelloRischio.RISCHIO_MODERATO,
            rischiPrincipali = emptyList(),
            puntiDiForza = emptyList(),
            segnaliRecenti10Q = emptyList(),
            filingComboHash = "test-hash",
            llmCallsCount = 1,
            sintesi = "test synthesis",
        )
        every { newsSentimentService.classify(t) } returns NewsSentimentResult(
            ticker = t,
            total = 2,
            panicCount = 1,
            structuralCount = 0,
            neutralCount = 1,
            dominantClass = SentimentClass.TEMPORARY_PANIC,
            classifications = listOf(
                NewsClassificationSummary(
                    newsId = "n1",
                    headline = "Stock dips on macro fears",
                    sentimentClass = SentimentClass.TEMPORARY_PANIC,
                    textExcerpt = "Market sells off on Fed comments; no fundamental impact reported.",
                    motivazione = "Reazione emotiva senza danno strutturale",
                    url = "https://example.com/news/1",
                ),
                NewsClassificationSummary(
                    newsId = "n2",
                    headline = "Routine quarterly update",
                    sentimentClass = SentimentClass.NEUTRAL,
                    textExcerpt = "Company reaffirms guidance with no notable changes.",
                    motivazione = "Aggiornamento routinario",
                    url = "https://example.com/news/2",
                ),
            ),
        )
    }

    private fun callDeepAndParse(ticker: String): JsonNode {
        val result = mockMvc.get("/api/analysis/$ticker/deep") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()
        return JSON.readTree(result.response.contentAsString)
    }

    private fun stubDeepAnalysisDeps(ticker: String) {
        val t = ticker.uppercase()
        val blob = FilingBlobEntity(
            id = 1L,
            ticker = t,
            cik = "0000320193",
            formType = "10-K",
            accessionNumber = "0000320193-24-000081",
            filingDate = LocalDate.of(2024, 11, 1),
        )
        // Post V028 split: analyze legge la cache filing dal repo (non più
        // via fetchAndCache); il contract test assertea filingsUsed non-vuoto.
        every { filingBlobRepository.findByTickerOrderByFilingDateDesc(t) } returns listOf(blob)
        every { filingChunkRepository.countByTicker(t) } returns 1L
        every { filing10KQDownloaderService.fetchAndCache(t) } returns listOf(blob)
        every { filingRagService.indexFiling(any(), any()) } returns IndexResult(0, true)
        every { priceActionAnalyzer.analyze(t) } returns PriceActionSnapshot(
            ticker = t,
            priceNow = 175.0,
            max52w = 200.0,
            min52w = 130.0,
            drawdownPct = -12.5,
            trend3mPct = 5.0,
            ma50 = 170.0,
            ma200 = 160.0,
            panicDiscount = false,
            deteriorationWarning = false,
            seriesDays = 252,
            note = null,
        )
    }
}
