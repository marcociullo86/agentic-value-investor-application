package com.valueinvesting.webapp.contract

// Contract test: OpenAPI RuleSignal.ruleId enum vs runtime RuleEngineService — TSK-089 / US-032 / EP-010.
//
// Three test surfaces:
//   1. Enum completeness      — canonical openapi.yaml declares exactly 13 ruleId values.
//   2. x-extension tags       — x-buffett-quality (7) and x-graham-defensive (6) partition the enum
//                               cleanly with no overlap.
//   3. Spring runtime alignment — RuleEngineService.evaluateAll(emptyDataset) returns exactly 13
//                                 RuleSignal instances covering all 13 ruleId values.
//
// OpenAPI file access:
//   Gradle passes the absolute path via system property `contract.openapi.canonical`
//   (see build.gradle.kts `tasks.withType<Test> { systemProperty("contract.openapi.canonical", ...) }`).
//   This avoids fragile relative-path assumptions and keeps the test runnable both locally
//   and inside the Podman build container where cwd may differ.
//
// Parsing strategy:
//   jackson-dataformat-yaml is already a testImplementation dep (build.gradle.kts line 97).
//   We reuse OpenApiContractSupport.loadCanonicalOpenApi() which wraps ObjectMapper(YAMLFactory())
//   — zero new deps.
//
// [^src: management/kanban/EP-010-graham-defensive-completeness/TSK-089.md §Acceptance Criteria]
// [^src: design_&_architecture/api/openapi.yaml §components.schemas.RuleSignal]

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.RuleEngineService
import com.valueinvesting.webapp.ruleengine.rules.CapexIntensityRule
import com.valueinvesting.webapp.ruleengine.rules.CurrentRatioRule
import com.valueinvesting.webapp.ruleengine.rules.DebtToIncomeRule
import com.valueinvesting.webapp.ruleengine.rules.DividendContinuityRule
import com.valueinvesting.webapp.ruleengine.rules.EarningsStabilityRule
import com.valueinvesting.webapp.ruleengine.rules.EpsGrowthRule
import com.valueinvesting.webapp.ruleengine.rules.GrossMarginRule
import com.valueinvesting.webapp.ruleengine.rules.NetMarginRule
import com.valueinvesting.webapp.ruleengine.rules.PbLatestRule
import com.valueinvesting.webapp.ruleengine.rules.Pe3yAvgRule
import com.valueinvesting.webapp.ruleengine.rules.RoeRule
import com.valueinvesting.webapp.ruleengine.rules.RoicRule
import com.valueinvesting.webapp.ruleengine.rules.SizeRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import java.time.Instant

@Tag("contract")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RuleSignalEnumContractTest {

    companion object {
        /**
         * Canonical set of 13 ruleId values as declared by US-032 / EP-010 + pre-existing EP-003.
         * 7 Buffett-quality rules (TSK-012..015) + 6 Graham-defensive rules (TSK-073/075/077/079/081/085).
         * Regression guard: adding or removing a ruleId from this set MUST fail this test,
         * forcing a conscious contract update in openapi.yaml AND here.
         */
        val EXPECTED_RULE_IDS: Set<String> = setOf(
            // 7 Buffett-quality rules (EP-003)
            "ROE_10Y_AVG",
            "ROIC_10Y_AVG",
            "GROSS_MARGIN_10Y_AVG",
            "NET_MARGIN_10Y_AVG",
            "CURRENT_RATIO_LATEST",
            "DEBT_TO_INCOME_LATEST",
            "CAPEX_INTENSITY_10Y_AVG",
            // 6 Graham-defensive rules (EP-010, TSK-073/075/077/079/081/085)
            "SIZE_LATEST",
            "EARNINGS_STABILITY_10Y",
            "EPS_GROWTH_10Y",
            "PE_3Y_AVG",
            "PB_LATEST",
            "DIVIDEND_CONTINUITY_20Y",
        )

        val EXPECTED_BUFFETT_IDS: Set<String> = setOf(
            "ROE_10Y_AVG",
            "ROIC_10Y_AVG",
            "GROSS_MARGIN_10Y_AVG",
            "NET_MARGIN_10Y_AVG",
            "CURRENT_RATIO_LATEST",
            "DEBT_TO_INCOME_LATEST",
            "CAPEX_INTENSITY_10Y_AVG",
        )

        val EXPECTED_GRAHAM_IDS: Set<String> = setOf(
            "SIZE_LATEST",
            "EARNINGS_STABILITY_10Y",
            "EPS_GROWTH_10Y",
            "PE_3Y_AVG",
            "PB_LATEST",
            "DIVIDEND_CONTINUITY_20Y",
        )
    }

    private lateinit var ruleIdNode: com.fasterxml.jackson.databind.JsonNode

    @BeforeAll
    fun loadCanonicalSpec() {
        val canonicalPath = System.getProperty("contract.openapi.canonical")
            ?: error(
                "System property `contract.openapi.canonical` not set. " +
                    "Run via Gradle (build.gradle.kts sets it automatically).",
            )
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(Path.of(canonicalPath))
        ruleIdNode = doc
            .path("components")
            .path("schemas")
            .path("RuleSignal")
            .path("properties")
            .path("ruleId")
    }

    // -------------------------------------------------------------------------
    // Test 1 — Enum completeness
    // -------------------------------------------------------------------------

    @Test
    fun `RuleSignal ruleId enum must contain exactly 13 values (US-032 AC regression guard)`() {
        val enumNode = ruleIdNode.path("enum")
        assertThat(enumNode.isArray)
            .withFailMessage("openapi.yaml RuleSignal.ruleId.enum is missing or not an array")
            .isTrue()

        val actualValues = enumNode.map { it.asText() }.toSet()

        assertThat(actualValues)
            .withFailMessage(
                buildString {
                    appendLine("RuleSignal.ruleId enum size mismatch.")
                    appendLine("Expected (${EXPECTED_RULE_IDS.size}): ${EXPECTED_RULE_IDS.sorted()}")
                    appendLine("Actual   (${actualValues.size}): ${actualValues.sorted()}")
                    val missing = EXPECTED_RULE_IDS - actualValues
                    val extra = actualValues - EXPECTED_RULE_IDS
                    if (missing.isNotEmpty()) appendLine("Missing from spec: $missing")
                    if (extra.isNotEmpty()) appendLine("Extra in spec (undeclared): $extra")
                },
            )
            .isEqualTo(EXPECTED_RULE_IDS)

        // Explicit size check so the failure message is crystal-clear
        assertThat(actualValues)
            .withFailMessage("Expected 13 ruleId enum values, got ${actualValues.size}: $actualValues")
            .hasSize(13)
    }

    @Test
    fun `RuleSignal ruleId enum must include all 6 Graham-defensive ruleId (EP-010 regression guard)`() {
        val enumNode = ruleIdNode.path("enum")
        assertThat(enumNode.isArray).isTrue()
        val actualValues = enumNode.map { it.asText() }.toSet()

        assertThat(actualValues)
            .withFailMessage(
                "One or more Graham-defensive ruleId missing from openapi.yaml enum. " +
                    "Missing: ${EXPECTED_GRAHAM_IDS - actualValues}",
            )
            .containsAll(EXPECTED_GRAHAM_IDS)
    }

    // -------------------------------------------------------------------------
    // Test 2 — x-extension tags
    // -------------------------------------------------------------------------

    @Test
    fun `x-buffett-quality extension must list exactly the 7 Buffett ruleId`() {
        val buffettNode = ruleIdNode.path("x-buffett-quality")
        assertThat(buffettNode.isArray)
            .withFailMessage("openapi.yaml RuleSignal.ruleId x-buffett-quality is missing or not an array")
            .isTrue()

        val actualBuffett = buffettNode.map { it.asText() }.toSet()

        assertThat(actualBuffett)
            .withFailMessage(
                buildString {
                    appendLine("x-buffett-quality mismatch.")
                    appendLine("Expected (${EXPECTED_BUFFETT_IDS.size}): ${EXPECTED_BUFFETT_IDS.sorted()}")
                    appendLine("Actual   (${actualBuffett.size}): ${actualBuffett.sorted()}")
                },
            )
            .isEqualTo(EXPECTED_BUFFETT_IDS)

        assertThat(actualBuffett).hasSize(7)
    }

    @Test
    fun `x-graham-defensive extension must list exactly the 6 Graham ruleId`() {
        val grahamNode = ruleIdNode.path("x-graham-defensive")
        assertThat(grahamNode.isArray)
            .withFailMessage("openapi.yaml RuleSignal.ruleId x-graham-defensive is missing or not an array")
            .isTrue()

        val actualGraham = grahamNode.map { it.asText() }.toSet()

        assertThat(actualGraham)
            .withFailMessage(
                buildString {
                    appendLine("x-graham-defensive mismatch.")
                    appendLine("Expected (${EXPECTED_GRAHAM_IDS.size}): ${EXPECTED_GRAHAM_IDS.sorted()}")
                    appendLine("Actual   (${actualGraham.size}): ${actualGraham.sorted()}")
                },
            )
            .isEqualTo(EXPECTED_GRAHAM_IDS)

        assertThat(actualGraham).hasSize(6)
    }

    @Test
    fun `x-buffett-quality and x-graham-defensive must be disjoint (no ruleId overlap)`() {
        val buffettNode = ruleIdNode.path("x-buffett-quality")
        val grahamNode = ruleIdNode.path("x-graham-defensive")

        assertThat(buffettNode.isArray).isTrue()
        assertThat(grahamNode.isArray).isTrue()

        val buffett = buffettNode.map { it.asText() }.toSet()
        val graham = grahamNode.map { it.asText() }.toSet()
        val overlap = buffett intersect graham

        assertThat(overlap)
            .withFailMessage(
                "ruleId appears in BOTH x-buffett-quality and x-graham-defensive — must be disjoint: $overlap",
            )
            .isEmpty()
    }

    @Test
    fun `x-buffett-quality union x-graham-defensive must equal the full 13-value enum`() {
        val enumNode = ruleIdNode.path("enum")
        val buffettNode = ruleIdNode.path("x-buffett-quality")
        val grahamNode = ruleIdNode.path("x-graham-defensive")

        assertThat(enumNode.isArray).isTrue()
        assertThat(buffettNode.isArray).isTrue()
        assertThat(grahamNode.isArray).isTrue()

        val enumValues = enumNode.map { it.asText() }.toSet()
        val union = buffettNode.map { it.asText() }.toSet() + grahamNode.map { it.asText() }.toSet()

        assertThat(union)
            .withFailMessage(
                "Union of x-buffett-quality + x-graham-defensive does not cover the full enum.\n" +
                    "Enum:  ${enumValues.sorted()}\n" +
                    "Union: ${union.sorted()}",
            )
            .isEqualTo(enumValues)
    }

    // -------------------------------------------------------------------------
    // Test 3 — Spring runtime alignment (unit-level, no container needed)
    // -------------------------------------------------------------------------
    //
    // Wires all 13 ValuationRule implementations directly (mirrors the Spring
    // auto-collection pattern in RuleEngineServiceTest) and calls evaluateAll()
    // with a minimal empty dataset. Empty input guarantees INDETERMINATE /
    // NOT_CALCULABLE signals — the goal here is only to verify ruleId coverage.

    @Test
    fun `RuleEngineService evaluateAll with all 13 rules returns 13 RuleSignal with correct ruleId (US-032 AC)`() {
        val service = RuleEngineService(
            rules = listOf(
                // 7 Buffett-quality rules (EP-003)
                RoeRule(),
                RoicRule(),
                GrossMarginRule(),
                NetMarginRule(),
                CurrentRatioRule(),
                DebtToIncomeRule(),
                CapexIntensityRule(),
                // 6 Graham-defensive rules (EP-010)
                SizeRule(),
                EarningsStabilityRule(),
                EpsGrowthRule(),
                Pe3yAvgRule(),
                PbLatestRule(),
                DividendContinuityRule(),
            ),
        )

        val emptyDataset = FinancialDataset(
            ticker = "TEST-EMPTY",
            income = emptyList(),
            balance = emptyList(),
            cashFlow = emptyList(),
            keyMetrics = emptyList(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
            currentPrice = null,
            dividends = emptyList(),
        )

        val results = service.evaluateAll(emptyDataset)

        // AC: exactly 13 signals returned
        assertThat(results)
            .withFailMessage(
                "Expected 13 RuleSignal from evaluateAll, got ${results.size}. " +
                    "ruleIds: ${results.map { it.ruleId }}",
            )
            .hasSize(13)

        // AC: ruleId set matches the 13 canonical values
        val actualIds = results.map { it.ruleId }.toSet()
        assertThat(actualIds)
            .withFailMessage(
                buildString {
                    appendLine("RuleEngineService runtime ruleId set does not match canonical contract.")
                    appendLine("Expected: ${EXPECTED_RULE_IDS.sorted()}")
                    appendLine("Actual:   ${actualIds.sorted()}")
                    val missing = EXPECTED_RULE_IDS - actualIds
                    val extra = actualIds - EXPECTED_RULE_IDS
                    if (missing.isNotEmpty()) appendLine("Missing rules: $missing")
                    if (extra.isNotEmpty()) appendLine("Undeclared rules: $extra")
                },
            )
            .isEqualTo(EXPECTED_RULE_IDS)

        // AC: no duplicate ruleId
        assertThat(results.map { it.ruleId })
            .withFailMessage("Duplicate ruleId detected in evaluateAll output: ${results.map { it.ruleId }}")
            .doesNotHaveDuplicates()
    }

    @Test
    fun `RuleEngineService evaluateAll returns deterministically sorted ruleId on empty dataset`() {
        val service = RuleEngineService(
            // Intentionally shuffled injection order to verify sort stability
            rules = listOf(
                DividendContinuityRule(),
                PbLatestRule(),
                Pe3yAvgRule(),
                EpsGrowthRule(),
                EarningsStabilityRule(),
                SizeRule(),
                CapexIntensityRule(),
                DebtToIncomeRule(),
                CurrentRatioRule(),
                NetMarginRule(),
                GrossMarginRule(),
                RoicRule(),
                RoeRule(),
            ),
        )

        val emptyDataset = FinancialDataset(
            ticker = "TEST-SORT",
            income = emptyList(),
            balance = emptyList(),
            cashFlow = emptyList(),
            keyMetrics = emptyList(),
            dataSnapshotAt = Instant.parse("2026-05-24T00:00:00Z"),
        )

        val results = service.evaluateAll(emptyDataset)

        // Lexicographic sort (all 13):
        //   CAPEX_ < CURRENT_ < DEBT_ < DIVIDEND_ < EARNINGS_ < EPS_ < GROSS_
        //   < NET_ < PB_ < PE_ < ROE_ < ROIC_ < SIZE_
        assertThat(results.map { it.ruleId })
            .containsExactly(
                "CAPEX_INTENSITY_10Y_AVG",
                "CURRENT_RATIO_LATEST",
                "DEBT_TO_INCOME_LATEST",
                "DIVIDEND_CONTINUITY_20Y",
                "EARNINGS_STABILITY_10Y",
                "EPS_GROWTH_10Y",
                "GROSS_MARGIN_10Y_AVG",
                "NET_MARGIN_10Y_AVG",
                "PB_LATEST",
                "PE_3Y_AVG",
                "ROE_10Y_AVG",
                "ROIC_10Y_AVG",
                "SIZE_LATEST",
            )
    }
}
