package com.valueinvesting.webapp.backtest

import com.github.benmanes.caffeine.cache.Caffeine
import com.valueinvesting.webapp.api.model.BacktestStatus
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.ProfileDto
import com.valueinvesting.webapp.fmp.dto.ScreenedStockDto
import com.valueinvesting.webapp.fmp.dto.SearchHitDto
import com.valueinvesting.webapp.fmp.dto.SecFilingFmpDto
import com.valueinvesting.webapp.fmp.dto.StockNewsItem
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord
import com.valueinvesting.webapp.ruleengine.RuleEngineService
import com.valueinvesting.webapp.ruleengine.ValuationRule
import com.valueinvesting.webapp.ruleengine.calculators.DcfCalculator
import com.valueinvesting.webapp.ruleengine.calculators.FcfFallbackEstimator
import com.valueinvesting.webapp.ruleengine.calculators.GreenwaldMaintenanceCapexEstimator
import com.valueinvesting.webapp.ruleengine.calculators.MarginOfSafetyEvaluator
import com.valueinvesting.webapp.summary.SummaryVerdictAggregator
import com.valueinvesting.webapp.summary.ViVerdictAggregator
import com.valueinvesting.webapp.technicalanalysis.EntryTimingAdvisor
import com.valueinvesting.webapp.technicalanalysis.StopPlacementAdvisor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Tests TSK-348 / TSK-349: cache hit/miss, INSUFFICIENT_HISTORY, idempotenza,
// equity mai persistita (la chiave di cache non la include).
//
// Uso un fake FmpAdapter in-memory + RuleEngineService con rules=emptyList()
// (in questo unit-test ci interessa il path orchestrazione, NON la copertura
// dei 13 ruleId reali — quelli sono testati da GrahamRulesIntegrationTest).
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-348.md]
// [^src: management/kanban/EP-024-.../US-105-.../TSK-349.md §"Test idempotenza", §"Test INSUFFICIENT_HISTORY"]
class BacktestServiceTest {

    private val fixedClock = Clock.fixed(
        Instant.parse("2026-06-09T12:00:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun `INSUFFICIENT_HISTORY returned when FMP series is too short for horizon`() {
        val emptyFmp = FakeFmpAdapter(
            eodPrices = listOf(
                EodPriceRecord(date = LocalDate.of(2026, 5, 1), close = 100.0),
                EodPriceRecord(date = LocalDate.of(2026, 5, 31), close = 105.0),
            ),
        )
        val service = buildService(emptyFmp)

        val response = service.backtest("TEST", years = 5, horizonMonths = 6)

        assertThat(response.status).isEqualTo(BacktestStatus.INSUFFICIENT_HISTORY)
        assertThat(response.insufficientHistoryReason).isNotBlank
        assertThat(response.window).isNull()
        assertThat(response.strategies).isNull()
        assertThat(response.timingEdge).isNull()
        assertThat(response.trades).isNull()
        // caveats sempre presente.
        assertThat(response.caveats.lookAheadResidual).isTrue
        assertThat(response.caveats.singleTicker).isTrue
        assertThat(response.caveats.notPortfolioPerformance).isTrue
    }

    @Test
    fun `INSUFFICIENT_HISTORY returned when no EOD data available at all`() {
        val service = buildService(FakeFmpAdapter())

        val response = service.backtest("TEST", years = 5, horizonMonths = 6)

        assertThat(response.status).isEqualTo(BacktestStatus.INSUFFICIENT_HISTORY)
        assertThat(response.insufficientHistoryReason).contains("EOD")
    }

    @Test
    fun `idempotenza — same input produces same output`() {
        val fmp = FakeFmpAdapter(
            eodPrices = generateEodSeries(LocalDate.of(2020, 1, 1), days = 365 * 7),
        )
        val service = buildService(fmp)

        val first = service.backtest("AAPL", years = 5, horizonMonths = 6)
        val second = service.backtest("AAPL", years = 5, horizonMonths = 6)

        // Stesso input → stesso output (cache OK + deterministic anche senza cache).
        assertThat(second.status).isEqualTo(first.status)
        assertThat(second.window).isEqualTo(first.window)
        assertThat(second.strategies?.map { it.strategy to it.trades })
            .isEqualTo(first.strategies?.map { it.strategy to it.trades })
        assertThat(second.timingEdge).isEqualTo(first.timingEdge)
    }

    @Test
    fun `cache key excludes equity — different equity hits same cache entry`() {
        // Costruiamo manualmente la chiave per verificare l'invariante.
        val keyA = BacktestCacheKey("AAPL", years = 5, horizonMonths = 6)
        val keyB = BacktestCacheKey("AAPL", years = 5, horizonMonths = 6)
        assertThat(keyA).isEqualTo(keyB)
        assertThat(keyA.hashCode()).isEqualTo(keyB.hashCode())
        // Le 3 field della key sono ticker/years/horizonMonths — verifica
        // via reflection: equity NON presente nella chiave.
        val fields = BacktestCacheKey::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()
        assertThat(fields).containsExactlyInAnyOrder("ticker", "years", "horizonMonths")
        assertThat(fields).doesNotContain("equity")
    }

    @Test
    fun `horizonMonths outside allowed values rejected via require`() {
        val service = buildService(FakeFmpAdapter())
        org.assertj.core.api.Assertions.assertThatThrownBy {
            service.backtest("TEST", years = 5, horizonMonths = 2)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildService(fmp: FmpAdapter): BacktestService {
        val filter = PointInTimeFinancialFilter()
        val ctx = AsOfDateTechnicalContext(EntryTimingAdvisor(), StopPlacementAdvisor())
        val ruleEngine = RuleEngineService(rules = emptyList<ValuationRule>())
        val dcf = DcfCalculator(
            greenwaldEstimator = GreenwaldMaintenanceCapexEstimator(),
            fcfFallbackEstimator = FcfFallbackEstimator(),
        )
        val mos = MarginOfSafetyEvaluator()
        val engine = BacktestEngine(
            pointInTimeFilter = filter,
            asOfDateTechnicalContext = ctx,
            ruleEngineService = ruleEngine,
            dcfCalculator = dcf,
            marginOfSafetyEvaluator = mos,
            viVerdictAggregator = ViVerdictAggregator(),
            summaryVerdictAggregator = SummaryVerdictAggregator(),
        )
        val cache = Caffeine.newBuilder()
            .maximumSize(100)
            .build<BacktestCacheKey, com.valueinvesting.webapp.api.model.BacktestResponse>()
        return BacktestService(
            fmpAdapter = fmp,
            backtestEngine = engine,
            backtestRoundTripSimulator = BacktestRoundTripSimulator(),
            backtestCache = cache,
            clock = fixedClock,
        )
    }

    private fun generateEodSeries(from: LocalDate, days: Int): List<EodPriceRecord> =
        (0 until days).map {
            EodPriceRecord(
                date = from.plusDays(it.toLong()),
                close = 100.0 + (it % 30),
                open = 100.0,
                high = 100.0,
                low = 100.0,
                volume = 1_000_000L,
            )
        }
}

/**
 * In-memory FmpAdapter per i test BacktestService. Tutti i metodi che il
 * BacktestService consuma ritornano le liste pre-configurate; gli altri
 * ritornano default vuoti senza eccezioni.
 */
private class FakeFmpAdapter(
    private val income: List<IncomeStatementDto> = emptyList(),
    private val balance: List<BalanceSheetDto> = emptyList(),
    private val cashFlow: List<CashFlowDto> = emptyList(),
    private val keyMetrics: List<KeyMetricsDto> = emptyList(),
    private val dividends: List<DividendRecord> = emptyList(),
    private val eodPrices: List<EodPriceRecord> = emptyList(),
    private val indicator: List<TechnicalIndicatorRecord> = emptyList(),
) : FmpAdapter {
    override fun getIncomeStatement(ticker: String, limit: Int): List<IncomeStatementDto> = income
    override fun getBalanceSheet(ticker: String, limit: Int): List<BalanceSheetDto> = balance
    override fun getCashFlow(ticker: String, limit: Int): List<CashFlowDto> = cashFlow
    override fun getKeyMetrics(ticker: String, limit: Int): List<KeyMetricsDto> = keyMetrics
    override fun getProfile(ticker: String): ProfileDto = ProfileDto(symbol = ticker, price = 100.0)
    override fun screen(
        marketCapMoreThan: Long?,
        marketCapLowerThan: Long?,
        sector: String?,
        exchange: String?,
        country: String?,
        limit: Int,
    ): List<ScreenedStockDto> = emptyList()
    override fun searchSymbol(query: String, limit: Int): List<SearchHitDto> = emptyList()
    override fun getDividendHistory(ticker: String): List<DividendRecord> = dividends
    override fun getStockNews(ticker: String, days: Int): List<StockNewsItem> = emptyList()
    override fun getHistoricalEodPrices(ticker: String, days: Int): List<EodPriceRecord> = eodPrices
    override fun getSecFilings(
        ticker: String,
        formTypes: List<String>,
        limit: Int,
        lookbackMonths: Long,
    ): List<SecFilingFmpDto> = emptyList()
    override fun searchCusip(cusip: String): String? = null
    override fun getTechnicalIndicator(
        ticker: String,
        indicator: String,
        periodLength: Int,
        timeframe: String,
    ): List<TechnicalIndicatorRecord> = this.indicator
}
