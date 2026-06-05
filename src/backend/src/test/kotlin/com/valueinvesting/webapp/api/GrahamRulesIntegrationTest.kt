package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.GrahamFixtureLoader
import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * E2E integration tests for the 6 Graham Defensive rules (EP-010, TSK-090).
 *
 * Tests cover:
 *   - All 13 ruleId present in response (7 Buffett + 6 Graham).
 *   - Each Graham ruleId carries the signal expected from its test fixture.
 *   - Zero-regression check: all 7 Buffett ruleId still present on AAPL.
 *   - Edge case: ticker with no dividend history → DIVIDEND_CONTINUITY_20Y = INDETERMINATE.
 *
 * Signal assertions are done via body-text parsing (AssertJ on JsonNode) rather than
 * Spring MockMvc JsonPath filter expressions, which return JSONArray rather than String
 * and require Hamcrest matchers for element-level comparison.
 *
 * Fixture design decisions:
 *   - AAPL: Graham income 10y (2015-2024, EPS growing ~1.8x → EPS_GROWTH_10Y GREEN).
 *     Dividend fixture spans 2005-2024 = 20 consecutive years → DIVIDEND_CONTINUITY_20Y GREEN.
 *     Key-metrics include bookValuePerShare=3.0, giving P/B≈50 → PB_LATEST RED.
 *     Price=150 from profile, PE_3Y_AVG ≈ 24.4 → RED (>20).
 *   - MSFT: 10y income (strong EPS growth +327%), dividends from 2003 (22 years → GREEN),
 *     bookValuePerShare=36.0 → P/B≈11 → RED. Revenue $245B → SIZE_LATEST GREEN.
 *   - KO: 10y income (EPS stable +19% → EPS_GROWTH YELLOW), dividends from 1993 (32 years → GREEN),
 *     bookValuePerShare=2.08 → P/B≈29 → RED. Revenue $47B → SIZE_LATEST GREEN.
 *   - GOOGL: full income data but getDividendHistory() returns emptyList() →
 *     DIVIDEND_CONTINUITY_20Y INDETERMINATE.
 *
 * [^src: management/kanban/EP-010-graham-defensive-completeness/TSK-090.md]
 * [^src: wiki/concepts/seven-criteria-defensive-stock-selection.md §Tabella Sinottica]
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Testcontainers
class GrahamRulesIntegrationTest {

    companion object {
        /** Total rules: 7 Buffett (ROE, ROIC, GROSS_MARGIN, NET_MARGIN, CURRENT_RATIO,
         *  DEBT_TO_INCOME, CAPEX_INTENSITY) + 6 Graham = 13.
         *  All @Component : ValuationRule beans are auto-collected by RuleEngineService. */
        // EP-023: 7 Buffett + 6 Graham-defensive + 2 Graham-enterprising (NCAV_LATEST, NET_NET_RATIO) = 15.
        private const val TOTAL_RULES = 15

        private val GRAHAM_RULE_IDS = setOf(
            "SIZE_LATEST",
            "EARNINGS_STABILITY_10Y",
            "EPS_GROWTH_10Y",
            "PE_3Y_AVG",
            "PB_LATEST",
            "DIVIDEND_CONTINUITY_20Y",
        )

        private val BUFFETT_RULE_IDS = setOf(
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
            .withDatabaseName("value_investing_graham_test")
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
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper: stub FmpAdapter for the full Graham-fixture pipeline
    // ─────────────────────────────────────────────────────────────────────────────

    private fun stubAapl() {
        every { fmpAdapter.getProfile("AAPL") } returns GrahamFixtureLoader.aaplProfile()
        every { fmpAdapter.getIncomeStatement("AAPL", any()) } returns GrahamFixtureLoader.aaplIncome()
        every { fmpAdapter.getBalanceSheet("AAPL", any()) } returns GrahamFixtureLoader.aaplBalance()
        every { fmpAdapter.getCashFlow("AAPL", any()) } returns GrahamFixtureLoader.aaplCashFlow()
        every { fmpAdapter.getKeyMetrics("AAPL", any()) } returns GrahamFixtureLoader.aaplKeyMetrics()
        every { fmpAdapter.getDividendHistory("AAPL") } returns GrahamFixtureLoader.aaplDividends()
    }

    private fun stubMsft() {
        every { fmpAdapter.getProfile("MSFT") } returns GrahamFixtureLoader.msftProfile()
        every { fmpAdapter.getIncomeStatement("MSFT", any()) } returns GrahamFixtureLoader.msftIncome()
        every { fmpAdapter.getBalanceSheet("MSFT", any()) } returns GrahamFixtureLoader.msftBalance()
        every { fmpAdapter.getCashFlow("MSFT", any()) } returns GrahamFixtureLoader.msftCashFlow()
        every { fmpAdapter.getKeyMetrics("MSFT", any()) } returns GrahamFixtureLoader.msftKeyMetrics()
        every { fmpAdapter.getDividendHistory("MSFT") } returns GrahamFixtureLoader.msftDividends()
    }

    private fun stubKo() {
        every { fmpAdapter.getProfile("KO") } returns GrahamFixtureLoader.koProfile()
        every { fmpAdapter.getIncomeStatement("KO", any()) } returns GrahamFixtureLoader.koIncome()
        every { fmpAdapter.getBalanceSheet("KO", any()) } returns GrahamFixtureLoader.koBalance()
        every { fmpAdapter.getCashFlow("KO", any()) } returns GrahamFixtureLoader.koCashFlow()
        every { fmpAdapter.getKeyMetrics("KO", any()) } returns GrahamFixtureLoader.koKeyMetrics()
        every { fmpAdapter.getDividendHistory("KO") } returns GrahamFixtureLoader.koDividends()
    }

    private fun stubGoogl() {
        every { fmpAdapter.getProfile("GOOGL") } returns GrahamFixtureLoader.googlProfile()
        every { fmpAdapter.getIncomeStatement("GOOGL", any()) } returns GrahamFixtureLoader.googlIncome()
        every { fmpAdapter.getBalanceSheet("GOOGL", any()) } returns GrahamFixtureLoader.googlBalance()
        every { fmpAdapter.getCashFlow("GOOGL", any()) } returns GrahamFixtureLoader.googlCashFlow()
        every { fmpAdapter.getKeyMetrics("GOOGL", any()) } returns GrahamFixtureLoader.googlKeyMetrics()
        // No dividends — fetchDividendsWithFallback returns emptyList(), rule → INDETERMINATE
        every { fmpAdapter.getDividendHistory("GOOGL") } returns emptyList()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper: parse signal map from response body { ruleId -> signal }
    // ─────────────────────────────────────────────────────────────────────────────

    private fun parseSignals(result: MvcResult): Map<String, String> {
        val root: JsonNode = JSON.readTree(result.response.contentAsString)
        val signals = root.get("signals") ?: return emptyMap()
        return buildMap {
            signals.forEach { node ->
                val ruleId = node.get("ruleId")?.asText() ?: return@forEach
                val signal = node.get("signal")?.asText() ?: return@forEach
                put(ruleId, signal)
            }
        }
    }

    /** Parses the HTTP response body and returns a map of { ruleId -> observedValue }.
     *  Signals with null `observedValue` in JSON (INDETERMINATE / NOT_CALCULABLE) are excluded. */
    private fun parseObservedValues(result: MvcResult): Map<String, Double> {
        val root: JsonNode = JSON.readTree(result.response.contentAsString)
        val signals = root.get("signals") ?: return emptyMap()
        return buildMap {
            signals.forEach { node ->
                val ruleId = node.get("ruleId")?.asText() ?: return@forEach
                val observedValueNode = node.get("observedValue") ?: return@forEach
                if (!observedValueNode.isNull) {
                    put(ruleId, observedValueNode.asDouble())
                }
            }
        }
    }

    private fun analyze(ticker: String): MvcResult =
        mockMvc.get("/api/analysis/$ticker") {
            accept(MediaType.APPLICATION_JSON)
        }.andExpect {
            status { isOk() }
        }.andReturn()

    private fun analyzeAndGetSignals(ticker: String): Map<String, String> =
        parseSignals(analyze(ticker))

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 1: AAPL response has all 13 ruleId in signals
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: response contains exactly 15 signals (7 Buffett + 6 Graham defensive + 2 Graham enterprising)")
    fun `AAPL response has all 15 ruleId in signals`() {
        stubAapl()

        val signals = analyzeAndGetSignals("AAPL")

        assertThat(signals)
            .hasSize(TOTAL_RULES)

        val allRuleIds = GRAHAM_RULE_IDS + BUFFETT_RULE_IDS
        assertThat(signals.keys)
            .containsAll(allRuleIds)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 2: AAPL SIZE_LATEST GREEN (revenue ~$391B >> $100M threshold)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: SIZE_LATEST GREEN (revenue ~\$391B >> \$100M threshold)")
    fun `AAPL SIZE_LATEST is GREEN`() {
        stubAapl()

        val signals = analyzeAndGetSignals("AAPL")

        assertThat(signals["SIZE_LATEST"])
            .isEqualTo("GREEN")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 3: AAPL EARNINGS_STABILITY_10Y GREEN (10/10 years positive netIncome)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: EARNINGS_STABILITY_10Y GREEN (10/10 years positive netIncome)")
    fun `AAPL EARNINGS_STABILITY_10Y is GREEN with observedValue 10`() {
        stubAapl()

        val signals = analyzeAndGetSignals("AAPL")

        assertThat(signals["EARNINGS_STABILITY_10Y"])
            .isEqualTo("GREEN")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 4: AAPL DIVIDEND_CONTINUITY_20Y GREEN (fixture spans 2005-2024 = 20 years)
    //
    // Design note: The existing dividends-aapl-20y.json fixture spans 2005-2024,
    // which equals exactly 20 consecutive years. Since totalSpanYears=20 >= 20
    // AND consecutiveYears=20 >= 20, the rule returns GREEN (not INDETERMINATE).
    // This aligns with AC 1: "AAPL fixture: 6 ruleId Graham presenti con segnali
    // coerenti con la fixture". The TSK description's alternative "only 12y →
    // INDETERMINATE" referred to a shorter hypothetical fixture; our fixture is
    // the actual dividends-aapl-20y.json whose oldest entry is 2005-02-15.
    //
    // TSK-288 (US-037 F-288-01): DoD requires consecutiveYears in response body.
    // DividendContinuityRule.evaluate sets observedValue = consecutiveYears.toDouble().
    // Fixture years 2005-2024 all have entries → consecutiveYears = 20, observedValue = 20.0.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: DIVIDEND_CONTINUITY_20Y GREEN (fixture 2005-2024 = 20 consecutive years)")
    fun `AAPL DIVIDEND_CONTINUITY_20Y is GREEN twenty consecutive years`() {
        stubAapl()

        val result = analyze("AAPL")
        val signals = parseSignals(result)
        val observedValues = parseObservedValues(result)

        assertThat(signals["DIVIDEND_CONTINUITY_20Y"])
            .isEqualTo("GREEN")
        assertThat(observedValues["DIVIDEND_CONTINUITY_20Y"])
            .isNotNull()
            .isCloseTo(20.0, within(0.5))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 5: MSFT DIVIDEND_CONTINUITY_20Y GREEN (22 years, from 2003)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MSFT: DIVIDEND_CONTINUITY_20Y GREEN (dividends 2003-2024 = 22 consecutive years)")
    fun `MSFT DIVIDEND_CONTINUITY_20Y is GREEN`() {
        stubMsft()

        val signals = analyzeAndGetSignals("MSFT")

        assertThat(signals["DIVIDEND_CONTINUITY_20Y"])
            .isEqualTo("GREEN")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 6: KO DIVIDEND_CONTINUITY_20Y GREEN (32 years, from 1993)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("KO: DIVIDEND_CONTINUITY_20Y GREEN (dividends 1993-2024 = 32 consecutive years — dividend aristocrat)")
    fun `KO DIVIDEND_CONTINUITY_20Y is GREEN`() {
        stubKo()

        val signals = analyzeAndGetSignals("KO")

        assertThat(signals["DIVIDEND_CONTINUITY_20Y"])
            .isEqualTo("GREEN")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 7: GOOGL — DIVIDEND_CONTINUITY_20Y INDETERMINATE (no dividend history)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GOOGL: DIVIDEND_CONTINUITY_20Y INDETERMINATE (getDividendHistory returns emptyList)")
    fun `GOOGL DIVIDEND_CONTINUITY_20Y is INDETERMINATE when no dividends`() {
        stubGoogl()

        val signals = analyzeAndGetSignals("GOOGL")

        assertThat(signals["DIVIDEND_CONTINUITY_20Y"])
            .isEqualTo("INDETERMINATE")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 8: AAPL EPS_GROWTH_10Y GREEN (EPS grew ~+180% from avg 2.19 to avg 6.14)
    //
    // Design note: Graham income fixture has EPS 2015→2024.
    // ASC window: oldest 3-year avg = (2.20+2.08+2.30)/3 = 2.193 (2015,2016,2017);
    // newest 3-year avg = (6.15+6.16+6.11)/3 = 6.14 (2022,2023,2024).
    // Growth = (6.14-2.193)/2.193 ≈ +1.80 = +180% >> 33% threshold → GREEN.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: EPS_GROWTH_10Y GREEN (10y EPS triennial avg grew ~+180%)")
    fun `AAPL EPS_GROWTH_10Y is GREEN`() {
        stubAapl()

        val signals = analyzeAndGetSignals("AAPL")

        assertThat(signals["EPS_GROWTH_10Y"])
            .isEqualTo("GREEN")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 9: AAPL PE_3Y_AVG RED (price=150, avg_eps_3y≈6.14, PE≈24.4 > 20)
    //
    // TSK-282 (US-035 F-01): DoD requires pe3yAvg in response body.
    // Fixture: income 2022-2024 eps = [6.15, 6.16, 6.11], avg = 6.1400;
    // pe3yAvg = 150 / 6.14 ≈ 24.43. observedValue tolerance ±0.5 covers
    // floating-point variance in the EPS average.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: PE_3Y_AVG RED (price=150, avg EPS≈6.14, PE≈24.4 > 20 threshold)")
    fun `AAPL PE_3Y_AVG is RED`() {
        stubAapl()

        val result = analyze("AAPL")
        val signals = parseSignals(result)
        val observedValues = parseObservedValues(result)

        assertThat(signals["PE_3Y_AVG"])
            .isEqualTo("RED")
        assertThat(observedValues["PE_3Y_AVG"])
            .isNotNull()
            .isCloseTo(24.4, within(0.5))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 10: AAPL PB_LATEST RED (price=150, bookValuePerShare=3.0, P/B=50 > 3)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: PB_LATEST RED (price=150, bookValuePerShare=3.0, P/B=50.0 > 3.0 threshold)")
    fun `AAPL PB_LATEST is RED`() {
        stubAapl()

        val signals = analyzeAndGetSignals("AAPL")

        assertThat(signals["PB_LATEST"])
            .isEqualTo("RED")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 11: MSFT SIZE_LATEST GREEN (revenue ~$245B >> $100M)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MSFT: SIZE_LATEST GREEN (revenue ~\$245B >> \$100M threshold)")
    fun `MSFT SIZE_LATEST is GREEN`() {
        stubMsft()

        val signals = analyzeAndGetSignals("MSFT")

        assertThat(signals["SIZE_LATEST"])
            .isEqualTo("GREEN")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 12: KO EPS_GROWTH_10Y YELLOW (eps stable, +19% growth between endpoints)
    //
    // Design note: KO income fixture has EPS stabilized ~2.00 in 2015-2017.
    // oldest 3-year avg = (2.00+2.00+2.00)/3 = 2.00 (2015,2016,2017);
    // newest 3-year avg = (2.19+2.47+2.47)/3 = 2.377 (2022,2023,2024).
    // Growth = (2.377-2.00)/2.00 = +18.9%, between 0% and 33% → YELLOW.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("KO: EPS_GROWTH_10Y YELLOW (triennial avg +18.9%: 2.00→2.38, between 0% and 33%)")
    fun `KO EPS_GROWTH_10Y is YELLOW`() {
        stubKo()

        val signals = analyzeAndGetSignals("KO")

        assertThat(signals["EPS_GROWTH_10Y"])
            .isEqualTo("YELLOW")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 13: Buffett rules zero-regression — all 7 Buffett ruleId present in AAPL
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AAPL: all 7 Buffett ruleId still present — zero regression check")
    fun `Buffett rules zero regression on AAPL`() {
        stubAapl()

        val signals = analyzeAndGetSignals("AAPL")

        assertThat(signals.keys)
            .containsAll(BUFFETT_RULE_IDS)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 14: MSFT EPS_GROWTH_10Y GREEN (EPS grew ~+327% from avg 2.43 to avg 10.39)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MSFT: EPS_GROWTH_10Y GREEN (EPS triennial avg grew +327%: 2.43→10.39)")
    fun `MSFT EPS_GROWTH_10Y is GREEN`() {
        stubMsft()

        val signals = analyzeAndGetSignals("MSFT")

        assertThat(signals["EPS_GROWTH_10Y"])
            .isEqualTo("GREEN")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 15 (F-001): KO PE_3Y_AVG RED (premium-valued — price=60, avg EPS 3y≈2.38, PE≈25.25)
    //
    // KO is a premium-priced consumer staples franchise — PE well above Graham's
    // moderate-P/E threshold.  DoD TSK-292 originally stated GREEN (PE≤15) which
    // was erroneous: the fixture yields PE≈25.25 → RED (>20 threshold in Pe3yAvgRule).
    // Fixture: income 2022-2024 eps = [2.19, 2.47, 2.47], avg = 2.3767;
    // price = 60.0 (graham-profile-ko.json); pe3yAvg = 60 / 2.3767 ≈ 25.25.
    // observedValue tolerance ±0.5 covers floating-point EPS-average variance.
    //
    // [^src: management/kanban/EP-010-graham-defensive-completeness/TSK-292.md §Spec correction]
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("KO: PE_3Y_AVG RED (price=60, avg EPS 3y≈2.38, PE≈25.25 > 20 — premium-valued franchise)")
    fun `KO PE_3Y_AVG is RED with observedValue approx 25 point 25`() {
        stubKo()

        val result = analyze("KO")
        val signals = parseSignals(result)
        val observedValues = parseObservedValues(result)

        assertThat(signals["PE_3Y_AVG"])
            .isEqualTo("RED")
        assertThat(observedValues["PE_3Y_AVG"])
            .isNotNull()
            .isCloseTo(25.25, within(0.5))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 16 (F-001): KO PB_LATEST RED (price=60, bookValuePerShare=2.08, P/B≈28.85)
    //
    // KO trades at a massive premium to book value due to intangible brand capital
    // not reflected in GAAP book value (decades of share buybacks and goodwill).
    // Fixture: keymetrics latest (2024-12-31) bookValuePerShare=2.08;
    // price=60.0; pbLatest = 60 / 2.08 ≈ 28.846 → RED (>3.0 threshold in PbLatestRule).
    // DoD TSK-292 originally stated YELLOW — corrected per iter-1 code-review finding F-001.
    // observedValue tolerance ±0.5 covers floating-point variance.
    //
    // [^src: management/kanban/EP-010-graham-defensive-completeness/TSK-292.md §Spec correction]
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("KO: PB_LATEST RED (price=60, bookValuePerShare=2.08, P/B≈28.85 > 3.0 — premium-to-book)")
    fun `KO PB_LATEST is RED with observedValue approx 28 point 85`() {
        stubKo()

        val result = analyze("KO")
        val signals = parseSignals(result)
        val observedValues = parseObservedValues(result)

        assertThat(signals["PB_LATEST"])
            .isEqualTo("RED")
        assertThat(observedValues["PB_LATEST"])
            .isNotNull()
            .isCloseTo(28.85, within(0.5))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 17 (F-002): MSFT EARNINGS_STABILITY_10Y GREEN (10/10 years positive netIncome)
    //
    // MSFT income fixture 2015-2024: all 10 years have positive netIncome.
    // Fixture data confirms netIncome > 0 every year → rule returns GREEN.
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MSFT: EARNINGS_STABILITY_10Y GREEN (10/10 years positive netIncome)")
    fun `MSFT EARNINGS_STABILITY_10Y is GREEN`() {
        stubMsft()

        val signals = analyzeAndGetSignals("MSFT")

        assertThat(signals["EARNINGS_STABILITY_10Y"])
            .isEqualTo("GREEN")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test 18 (F-002): KO EARNINGS_STABILITY_10Y GREEN (10/10 years positive netIncome)
    //
    // KO income fixture 2015-2024: all 10 years have positive netIncome.
    // Minimum netIncome in fixture is 6,000,000,000 (2017) → well above 0.
    // Rule returns GREEN (deterministic, no threshold ambiguity).
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("KO: EARNINGS_STABILITY_10Y GREEN (10/10 years positive netIncome)")
    fun `KO EARNINGS_STABILITY_10Y is GREEN`() {
        stubKo()

        val signals = analyzeAndGetSignals("KO")

        assertThat(signals["EARNINGS_STABILITY_10Y"])
            .isEqualTo("GREEN")
    }
}
