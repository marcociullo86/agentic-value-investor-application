package com.valueinvesting.webapp.contract

// Contract test: OpenAPI drift — 13 ruleId typed sub-schemas vs real payload from
// GET /api/analysis/AAPL on deterministic fixture (TSK-315 / US-094 / EP-021).
//
// Scope:
//   1. For every signal in signals[]: ruleId is one of the 13 canonical typed ruleId.
//   2. For each typed ruleId: the required typed fields declared by its OpenAPI sub-schema
//      are present (non-missing, non-null where required is true per ADR-028 §3) in the JSON node.
//   3. Top-level RuleEngineResult fields: ticker, evaluatedAt, grahamNumber, dcfIntrinsicValue,
//      dcfMethod, mosSignal, currentPriceAtEval, dataSnapshotAt, isStale — validated as
//      the existing OpenApiContractIT contract already required.
//
// Strategy: parse the HTTP response body as Jackson JsonNode; verify field presence per
// ruleId via explicit assertions (no external schema validator dep — jackson-dataformat-yaml
// already on testImplementation classpath; everit/networknt JSON Schema not needed).
// This avoids adding a new test dependency and reuses the idioms of RuleSignalEnumContractTest
// and GrahamRulesIntegrationTest (AssertJ assertAll + MockkBean stub).
//
// Build gate: @Tag("contract") → the Gradle task `contractCheck` includes this test in CI.
//
// [^src: management/kanban/EP-021-rulesignal-payload-refactor/US-094-rulesignal-client-regen-contract/TSK-315.md]
// [^src: design_&_architecture/decisions/ADR-028-rulesignal-typed-oneof-discriminator.md §3,§5]
// [^src: management/kanban/EP-021-rulesignal-payload-refactor/US-094-rulesignal-client-regen-contract/US-094.md §AC]

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.GrahamFixtureLoader
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
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

/**
 * Contract test: verifies that the real payload of GET /api/analysis/AAPL (on deterministic
 * AAPL fixture) satisfies the OpenAPI typed sub-schemas for all 13 ruleId (EP-021, ADR-028 §3).
 *
 * Verification per ruleId:
 *   - ruleId value matches one of the 13 canonical typed ruleId.
 *   - Required typed fields (per OpenAPI sub-schema) are present and non-null in the JSON node.
 *   - For threshold-type fields that are always populated by the rule implementation (thresholdUsd,
 *     thresholdYears, thresholdGreen, thresholdYellow, thresholdGreenPercent, thresholdYellowPercent,
 *     thresholdPercent, thresholdRatio, netIncomePositive): asserted as non-null/non-missing.
 *   - For observation-value fields nullable by spec (revenueLatest, cagrPercent, pe3yAvg, etc.):
 *     only field presence (non-MissingNode) is asserted; value may be null for INDETERMINATE signals.
 *
 * Zero-regression:
 *   - Top-level RuleEngineResult fields are validated: ticker, evaluatedAt, grahamNumber,
 *     dcfIntrinsicValue, dcfMethod, mosSignal, currentPriceAtEval, dataSnapshotAt, isStale.
 *
 * TSK-315 / US-094 AC:
 *   - All 13 ruleId present in signals[] → test on signal count.
 *   - Typed fields present and correct per sub-schema → field-presence assertions below.
 *   - Test fails with per-ruleId message if a typed field is missing → descriptive failure messages.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
@Tag("contract")
class RuleSignalTypedPayloadContractTest {

    companion object {
        /** The 13 canonical typed ruleId (EP-021 / ADR-028 §3). EP-023 NCAV / NET_NET are NOT in scope. */
        private val TYPED_RULE_IDS: Set<String> = setOf(
            "SIZE_LATEST",
            "EARNINGS_STABILITY_10Y",
            "EPS_GROWTH_10Y",
            "PE_3Y_AVG",
            "PB_LATEST",
            "DIVIDEND_CONTINUITY_20Y",
            "ROE_10Y_AVG",
            "ROIC_10Y_AVG",
            "GROSS_MARGIN_10Y_AVG",
            "NET_MARGIN_10Y_AVG",
            "CURRENT_RATIO_LATEST",
            "DEBT_TO_INCOME_LATEST",
            "CAPEX_INTENSITY_10Y_AVG",
        )

        private val JSON: ObjectMapper = jacksonObjectMapper()

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_contract_typed_test")
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

    @BeforeEach
    fun resetMocks() {
        clearMocks(fmpAdapter, answers = false, recordedCalls = true)
        stubAapl()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fixture setup (mirrors GrahamRulesIntegrationTest.stubAapl)
    // ─────────────────────────────────────────────────────────────────────────

    private fun stubAapl() {
        every { fmpAdapter.getProfile("AAPL") } returns GrahamFixtureLoader.aaplProfile()
        every { fmpAdapter.getIncomeStatement("AAPL", any()) } returns GrahamFixtureLoader.aaplIncome()
        every { fmpAdapter.getBalanceSheet("AAPL", any()) } returns GrahamFixtureLoader.aaplBalance()
        every { fmpAdapter.getCashFlow("AAPL", any()) } returns GrahamFixtureLoader.aaplCashFlow()
        every { fmpAdapter.getKeyMetrics("AAPL", any()) } returns GrahamFixtureLoader.aaplKeyMetrics()
        every { fmpAdapter.getDividendHistory("AAPL") } returns GrahamFixtureLoader.aaplDividends()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: call /api/analysis/AAPL and return parsed root JSON node
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchAaplPayload(): JsonNode {
        val result = mockMvc.get("/api/analysis/AAPL") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()
        return JSON.readTree(result.response.contentAsString)
    }

    /** Returns the signals[] array from the response root. */
    private fun signalsArray(root: JsonNode): List<JsonNode> {
        val arr = root.get("signals")
        assertThat(arr).withFailMessage("Response body missing 'signals' array").isNotNull
        return arr.toList()
    }

    /** Returns a map of { ruleId -> JsonNode } from the signals array. */
    private fun signalsByRuleId(root: JsonNode): Map<String, JsonNode> =
        buildMap {
            signalsArray(root).forEach { node ->
                val id = node.get("ruleId")?.asText() ?: return@forEach
                put(id, node)
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper: field-presence assertion utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Asserts that [fieldName] is present and non-null in [node].
     * Used for required non-nullable fields per OpenAPI sub-schema.
     */
    private fun assertRequiredFieldPresent(node: JsonNode, fieldName: String, ruleId: String) {
        val field = node.get(fieldName)
        assertThat(field)
            .withFailMessage(
                "ruleId=$ruleId: required field '$fieldName' is missing from payload. " +
                    "OpenAPI sub-schema RuleSignal${ruleId.toPascalCase()} requires it. " +
                    "Payload: ${node.toPrettyString().take(600)}",
            )
            .isNotNull
        assertThat(field!!.isNull)
            .withFailMessage(
                "ruleId=$ruleId: required field '$fieldName' is null in payload. " +
                    "OpenAPI sub-schema declares it non-nullable. " +
                    "Payload: ${node.toPrettyString().take(600)}",
            )
            .isFalse()
    }

    /**
     * Asserts that [fieldName] is present (may be null) in [node].
     * Used for nullable optional fields per OpenAPI sub-schema (e.g. observation-value fields).
     */
    private fun assertNullableFieldPresent(node: JsonNode, fieldName: String, ruleId: String) {
        assertThat(node.has(fieldName))
            .withFailMessage(
                "ruleId=$ruleId: nullable field '$fieldName' is completely absent from payload " +
                    "(expected present but may be null). OpenAPI sub-schema requires it exists (nullable: true). " +
                    "Payload: ${node.toPrettyString().take(600)}",
            )
            .isTrue()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 1 — Top-level RuleEngineResult fields zero-regression (US-094 AC last bullet)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: top-level RuleEngineResult fields present — zero-regression on existing contract")
    fun `top-level RuleEngineResult fields are present and correctly typed`() {
        val root = fetchAaplPayload()

        assertAll(
            { assertThat(root.get("ticker")?.asText()).isEqualTo("AAPL") },
            { assertThat(root.has("evaluatedAt")).isTrue() },
            { assertThat(root.has("grahamNumber")).isTrue() },
            { assertThat(root.has("dcfIntrinsicValue")).isTrue() },
            {
                val dcfMethod = root.get("dcfMethod")
                assertThat(dcfMethod).withFailMessage("dcfMethod missing from payload").isNotNull
                assertThat(dcfMethod!!.asText()).isIn("GREENWALD", "FCF_FALLBACK", "NOT_APPLICABLE")
            },
            {
                val mosSignal = root.get("mosSignal")
                assertThat(mosSignal).withFailMessage("mosSignal missing from payload").isNotNull
                assertThat(mosSignal!!.asText())
                    .isIn("GREEN", "YELLOW", "RED", "INDETERMINATE", "NOT_CALCULABLE")
            },
            { assertThat(root.has("currentPriceAtEval")).isTrue() },
            {
                assertThat(root.has("dataSnapshotAt"))
                    .withFailMessage("dataSnapshotAt missing from payload").isTrue()
            },
            {
                val isStale = root.get("isStale")
                assertThat(isStale).withFailMessage("isStale missing from payload").isNotNull
                assertThat(isStale!!.isBoolean).isTrue()
            },
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 2 — signals[] contains exactly 13 typed ruleId (EP-021 scope)
    //
    // Note: the actual response may contain 13 or 15 signals (13 + EP-023 NCAV/NET_NET
    // if those rules are active on the fixture stub). The AC for TSK-315 is that
    // all 13 typed ruleId are present and have their typed fields.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: all 13 typed ruleId present in signals[]")
    fun `signals array contains all 13 typed ruleId`() {
        val root = fetchAaplPayload()
        val presentIds = signalsByRuleId(root).keys

        val missingTypedIds = TYPED_RULE_IDS - presentIds

        assertThat(missingTypedIds)
            .withFailMessage(
                buildString {
                    appendLine("signals[] is missing typed ruleId (EP-021 scope).")
                    appendLine("Missing: $missingTypedIds")
                    appendLine("Present in payload: ${presentIds.sorted()}")
                },
            )
            .isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 3 — All 13 ruleId have ruleId field matching their sub-type
    //          (discriminator symmetry: the field that drives oneOf resolution is present)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: every signal node contains a non-null ruleId matching its sub-type (discriminator)")
    fun `every signal node has ruleId matching its discriminator`() {
        val root = fetchAaplPayload()
        val signals = signalsArray(root)

        val violations = signals.mapNotNull { node ->
            val ruleId = node.get("ruleId")?.takeIf { !it.isNull }?.asText()
            if (ruleId == null) {
                "signal node missing ruleId: ${node.toPrettyString().take(200)}"
            } else if (ruleId !in TYPED_RULE_IDS && ruleId !in setOf("NCAV_LATEST", "NET_NET_RATIO")) {
                "unknown ruleId '$ruleId' not declared in OpenAPI discriminator.mapping"
            } else {
                null
            }
        }

        assertThat(violations)
            .withFailMessage(
                buildString {
                    appendLine("Discriminator symmetry violations found:")
                    violations.forEach { appendLine("  - $it") }
                },
            )
            .isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests 4–16 — Per-ruleId typed field assertions (ADR-028 §3)
    // One test per ruleId; fail message identifies the offending ruleId and field.
    // ─────────────────────────────────────────────────────────────────────────

    // ── 4. SIZE_LATEST ────────────────────────────────────────────────────────
    // OpenAPI RuleSignalSize required: thresholdUsd; nullable: revenueLatest
    // ADR-028 §3: revenueLatest: Double?, thresholdUsd: Long (non-nullable)

    @Test
    @DisplayName("SIZE_LATEST: typed fields thresholdUsd (required) and revenueLatest (nullable) present in payload")
    fun `SIZE_LATEST typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("SIZE_LATEST")

        assertAll(
            // thresholdUsd: required non-nullable (Long)
            { assertRequiredFieldPresent(node, "thresholdUsd", "SIZE_LATEST") },
            // revenueLatest: nullable field — must be present (may be null for no-revenue cases)
            { assertNullableFieldPresent(node, "revenueLatest", "SIZE_LATEST") },
        )
    }

    // ── 5. EARNINGS_STABILITY_10Y ──────────────────────────────────────────────
    // OpenAPI RuleSignalEarningsStability10y required: yearsPositive, yearsAvailable, lossYears

    @Test
    @DisplayName("EARNINGS_STABILITY_10Y: typed fields yearsPositive, yearsAvailable, lossYears present in payload")
    fun `EARNINGS_STABILITY_10Y typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("EARNINGS_STABILITY_10Y")

        assertAll(
            { assertRequiredFieldPresent(node, "yearsPositive", "EARNINGS_STABILITY_10Y") },
            { assertRequiredFieldPresent(node, "yearsAvailable", "EARNINGS_STABILITY_10Y") },
            {
                val lossYears = node.get("lossYears")
                assertThat(lossYears)
                    .withFailMessage("EARNINGS_STABILITY_10Y: required field 'lossYears' missing from payload")
                    .isNotNull
                assertThat(lossYears!!.isArray)
                    .withFailMessage("EARNINGS_STABILITY_10Y: 'lossYears' must be an array, got: $lossYears")
                    .isTrue()
            },
        )
    }

    // ── 6. EPS_GROWTH_10Y ─────────────────────────────────────────────────────
    // OpenAPI RuleSignalEpsGrowth10y required: thresholdPercent; nullable: cagrPercent, epsStart, epsEnd, yearStart, yearEnd

    @Test
    @DisplayName("EPS_GROWTH_10Y: typed fields thresholdPercent (required) + nullable fields present in payload")
    fun `EPS_GROWTH_10Y typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("EPS_GROWTH_10Y")

        assertAll(
            // thresholdPercent: required non-nullable
            { assertRequiredFieldPresent(node, "thresholdPercent", "EPS_GROWTH_10Y") },
            // nullable fields must be present (value may be null)
            { assertNullableFieldPresent(node, "cagrPercent", "EPS_GROWTH_10Y") },
            { assertNullableFieldPresent(node, "epsStart", "EPS_GROWTH_10Y") },
            { assertNullableFieldPresent(node, "epsEnd", "EPS_GROWTH_10Y") },
            { assertNullableFieldPresent(node, "yearStart", "EPS_GROWTH_10Y") },
            { assertNullableFieldPresent(node, "yearEnd", "EPS_GROWTH_10Y") },
        )
    }

    // ── 7. PE_3Y_AVG ─────────────────────────────────────────────────────────
    // OpenAPI RuleSignalPe3yAvg required: thresholdGreen, thresholdYellow; nullable: pe3yAvg
    // Deviazione ADR-028 §3: two threshold fields instead of single `threshold`.

    @Test
    @DisplayName("PE_3Y_AVG: typed fields thresholdGreen, thresholdYellow (required) + pe3yAvg (nullable) present")
    fun `PE_3Y_AVG typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("PE_3Y_AVG")

        assertAll(
            { assertRequiredFieldPresent(node, "thresholdGreen", "PE_3Y_AVG") },
            { assertRequiredFieldPresent(node, "thresholdYellow", "PE_3Y_AVG") },
            { assertNullableFieldPresent(node, "pe3yAvg", "PE_3Y_AVG") },
        )
    }

    // ── 8. PB_LATEST ─────────────────────────────────────────────────────────
    // OpenAPI RuleSignalPbLatest required: thresholdGreen, thresholdYellow; nullable: pbLatest
    // Deviazione ADR-028 §3: analogous to Pe3yAvg.

    @Test
    @DisplayName("PB_LATEST: typed fields thresholdGreen, thresholdYellow (required) + pbLatest (nullable) present")
    fun `PB_LATEST typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("PB_LATEST")

        assertAll(
            { assertRequiredFieldPresent(node, "thresholdGreen", "PB_LATEST") },
            { assertRequiredFieldPresent(node, "thresholdYellow", "PB_LATEST") },
            { assertNullableFieldPresent(node, "pbLatest", "PB_LATEST") },
        )
    }

    // ── 9. DIVIDEND_CONTINUITY_20Y ────────────────────────────────────────────
    // OpenAPI RuleSignalDividendContinuity20y required: thresholdYears; nullable: consecutiveYears

    @Test
    @DisplayName("DIVIDEND_CONTINUITY_20Y: thresholdYears (required) + consecutiveYears (nullable) present")
    fun `DIVIDEND_CONTINUITY_20Y typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("DIVIDEND_CONTINUITY_20Y")

        assertAll(
            { assertRequiredFieldPresent(node, "thresholdYears", "DIVIDEND_CONTINUITY_20Y") },
            { assertNullableFieldPresent(node, "consecutiveYears", "DIVIDEND_CONTINUITY_20Y") },
        )
    }

    // ── 10. ROE_10Y_AVG ───────────────────────────────────────────────────────
    // OpenAPI RuleSignalRoe10yAvg required: yearsAvailable, thresholdGreenPercent, thresholdYellowPercent; nullable: averagePercent

    @Test
    @DisplayName("ROE_10Y_AVG: yearsAvailable, thresholdGreenPercent, thresholdYellowPercent (required) + averagePercent (nullable)")
    fun `ROE_10Y_AVG typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("ROE_10Y_AVG")

        assertAll(
            { assertRequiredFieldPresent(node, "yearsAvailable", "ROE_10Y_AVG") },
            { assertRequiredFieldPresent(node, "thresholdGreenPercent", "ROE_10Y_AVG") },
            { assertRequiredFieldPresent(node, "thresholdYellowPercent", "ROE_10Y_AVG") },
            { assertNullableFieldPresent(node, "averagePercent", "ROE_10Y_AVG") },
        )
    }

    // ── 11. ROIC_10Y_AVG ──────────────────────────────────────────────────────
    // OpenAPI RuleSignalRoic10yAvg: schema identical to ROE_10Y_AVG

    @Test
    @DisplayName("ROIC_10Y_AVG: yearsAvailable, thresholdGreenPercent, thresholdYellowPercent (required) + averagePercent (nullable)")
    fun `ROIC_10Y_AVG typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("ROIC_10Y_AVG")

        assertAll(
            { assertRequiredFieldPresent(node, "yearsAvailable", "ROIC_10Y_AVG") },
            { assertRequiredFieldPresent(node, "thresholdGreenPercent", "ROIC_10Y_AVG") },
            { assertRequiredFieldPresent(node, "thresholdYellowPercent", "ROIC_10Y_AVG") },
            { assertNullableFieldPresent(node, "averagePercent", "ROIC_10Y_AVG") },
        )
    }

    // ── 12. GROSS_MARGIN_10Y_AVG ──────────────────────────────────────────────
    // OpenAPI RuleSignalGrossMargin10yAvg required: thresholdGreenPercent, thresholdYellowPercent; nullable: averagePercent

    @Test
    @DisplayName("GROSS_MARGIN_10Y_AVG: thresholdGreenPercent, thresholdYellowPercent (required) + averagePercent (nullable)")
    fun `GROSS_MARGIN_10Y_AVG typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("GROSS_MARGIN_10Y_AVG")

        assertAll(
            { assertRequiredFieldPresent(node, "thresholdGreenPercent", "GROSS_MARGIN_10Y_AVG") },
            { assertRequiredFieldPresent(node, "thresholdYellowPercent", "GROSS_MARGIN_10Y_AVG") },
            { assertNullableFieldPresent(node, "averagePercent", "GROSS_MARGIN_10Y_AVG") },
        )
    }

    // ── 13. NET_MARGIN_10Y_AVG ────────────────────────────────────────────────
    // OpenAPI RuleSignalNetMargin10yAvg required: thresholdGreenPercent; nullable: averagePercent
    // Note: binario (GREEN/RED), no thresholdYellow.

    @Test
    @DisplayName("NET_MARGIN_10Y_AVG: thresholdGreenPercent (required) + averagePercent (nullable)")
    fun `NET_MARGIN_10Y_AVG typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("NET_MARGIN_10Y_AVG")

        assertAll(
            { assertRequiredFieldPresent(node, "thresholdGreenPercent", "NET_MARGIN_10Y_AVG") },
            { assertNullableFieldPresent(node, "averagePercent", "NET_MARGIN_10Y_AVG") },
        )
    }

    // ── 14. CURRENT_RATIO_LATEST ──────────────────────────────────────────────
    // OpenAPI RuleSignalCurrentRatioLatest required: thresholdGreen, thresholdYellow; nullable: ratioLatest

    @Test
    @DisplayName("CURRENT_RATIO_LATEST: thresholdGreen, thresholdYellow (required) + ratioLatest (nullable)")
    fun `CURRENT_RATIO_LATEST typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("CURRENT_RATIO_LATEST")

        assertAll(
            { assertRequiredFieldPresent(node, "thresholdGreen", "CURRENT_RATIO_LATEST") },
            { assertRequiredFieldPresent(node, "thresholdYellow", "CURRENT_RATIO_LATEST") },
            { assertNullableFieldPresent(node, "ratioLatest", "CURRENT_RATIO_LATEST") },
        )
    }

    // ── 15. DEBT_TO_INCOME_LATEST ─────────────────────────────────────────────
    // OpenAPI RuleSignalDebtToIncomeLatest required: thresholdGreen, thresholdYellow, netIncomePositive;
    //   nullable: ratioLatest

    @Test
    @DisplayName("DEBT_TO_INCOME_LATEST: thresholdGreen, thresholdYellow, netIncomePositive (required) + ratioLatest (nullable)")
    fun `DEBT_TO_INCOME_LATEST typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("DEBT_TO_INCOME_LATEST")

        assertAll(
            { assertRequiredFieldPresent(node, "thresholdGreen", "DEBT_TO_INCOME_LATEST") },
            { assertRequiredFieldPresent(node, "thresholdYellow", "DEBT_TO_INCOME_LATEST") },
            {
                // netIncomePositive: required boolean
                val field = node.get("netIncomePositive")
                assertThat(field)
                    .withFailMessage(
                        "DEBT_TO_INCOME_LATEST: required field 'netIncomePositive' missing from payload",
                    )
                    .isNotNull
                assertThat(field!!.isBoolean)
                    .withFailMessage(
                        "DEBT_TO_INCOME_LATEST: 'netIncomePositive' must be a boolean, got: $field",
                    )
                    .isTrue()
            },
            { assertNullableFieldPresent(node, "ratioLatest", "DEBT_TO_INCOME_LATEST") },
        )
    }

    // ── 16. CAPEX_INTENSITY_10Y_AVG ───────────────────────────────────────────
    // OpenAPI RuleSignalCapexIntensity10yAvg required: thresholdGreenPercent, thresholdYellowPercent;
    //   nullable: averagePercent

    @Test
    @DisplayName("CAPEX_INTENSITY_10Y_AVG: thresholdGreenPercent, thresholdYellowPercent (required) + averagePercent (nullable)")
    fun `CAPEX_INTENSITY_10Y_AVG typed fields satisfy OpenAPI sub-schema`() {
        val node = requireSignalNode("CAPEX_INTENSITY_10Y_AVG")

        assertAll(
            { assertRequiredFieldPresent(node, "thresholdGreenPercent", "CAPEX_INTENSITY_10Y_AVG") },
            { assertRequiredFieldPresent(node, "thresholdYellowPercent", "CAPEX_INTENSITY_10Y_AVG") },
            { assertNullableFieldPresent(node, "averagePercent", "CAPEX_INTENSITY_10Y_AVG") },
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 17 — Aggregate: all 13 typed ruleId have the base fields (ruleId + signal)
    //           that are declared as `required` in RuleSignalBase (OpenAPI).
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All 13 typed ruleId: base fields ruleId and signal are present and non-null")
    fun `all 13 typed ruleId have base fields ruleId and signal present`() {
        val root = fetchAaplPayload()
        val signalMap = signalsByRuleId(root)

        val violations = mutableListOf<String>()

        TYPED_RULE_IDS.forEach { id ->
            val node = signalMap[id]
            if (node == null) {
                violations += "ruleId=$id absent from signals[]"
                return@forEach
            }
            val ruleIdField = node.get("ruleId")
            if (ruleIdField == null || ruleIdField.isNull || ruleIdField.asText() != id) {
                violations += "ruleId=$id: 'ruleId' field missing or mismatched (got: $ruleIdField)"
            }
            val signalField = node.get("signal")
            if (signalField == null || signalField.isNull) {
                violations += "ruleId=$id: 'signal' field missing or null"
            } else {
                val signalValue = signalField.asText()
                if (signalValue !in setOf("GREEN", "YELLOW", "RED", "INDETERMINATE", "NOT_CALCULABLE")) {
                    violations += "ruleId=$id: 'signal' value '$signalValue' is not a valid Signal enum"
                }
            }
        }

        assertThat(violations)
            .withFailMessage(
                buildString {
                    appendLine("Base-field (ruleId + signal) contract violations found:")
                    violations.forEach { appendLine("  - $it") }
                },
            )
            .isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test 18 — Legacy deprecated fields present during transition window (ADR-028 §8 R+1/R+2)
    //           observedValue (nullable), threshold (string), rationale (string) must be
    //           in the payload for the transition window.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("All 13 typed ruleId: deprecated legacy fields observedValue, threshold, rationale present in payload (ADR-028 §8 transition R+1/R+2)")
    fun `all 13 typed ruleId have deprecated legacy fields present for transition window`() {
        val root = fetchAaplPayload()
        val signalMap = signalsByRuleId(root)

        val violations = mutableListOf<String>()

        TYPED_RULE_IDS.forEach { id ->
            val node = signalMap[id] ?: return@forEach // already caught by earlier test
            // observedValue: nullable — must be present (may be null or a number)
            if (!node.has("observedValue")) {
                violations += "ruleId=$id: deprecated field 'observedValue' absent from payload (expected present until R+3)"
            }
            // threshold: string — must be present
            if (!node.has("threshold")) {
                violations += "ruleId=$id: deprecated field 'threshold' absent from payload (expected present until R+3)"
            }
            // rationale: string — must be present
            if (!node.has("rationale")) {
                violations += "ruleId=$id: deprecated field 'rationale' absent from payload (expected present until R+3)"
            }
        }

        assertThat(violations)
            .withFailMessage(
                buildString {
                    appendLine("Deprecated legacy field contract violations (ADR-028 §8 — must stay until R+3):")
                    violations.forEach { appendLine("  - $it") }
                },
            )
            .isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helper: get signal node for a specific ruleId or fail fast
    // ─────────────────────────────────────────────────────────────────────────

    private fun requireSignalNode(ruleId: String): JsonNode {
        val root = fetchAaplPayload()
        val signalMap = signalsByRuleId(root)
        return signalMap[ruleId]
            ?: throw AssertionError(
                "ruleId=$ruleId not found in signals[] of GET /api/analysis/AAPL. " +
                    "Present ruleIds: ${signalMap.keys.sorted()}",
            )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extension: ruleId string → PascalCase for error messages
    // ─────────────────────────────────────────────────────────────────────────

    private fun String.toPascalCase(): String =
        split("_").joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
}
