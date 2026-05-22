package com.valueinvesting.webapp.ruleengine.feasibility

import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.FmpCacheService
import com.valueinvesting.webapp.fmp.FmpFixtureLoader
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.ruleengine.calculators.FcfFallbackEstimator
import com.valueinvesting.webapp.ruleengine.calculators.GreenwaldMaintenanceCapexEstimator
import com.valueinvesting.webapp.service.FinancialDataService
import com.valueinvesting.webapp.service.FinancialDataset
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DcfFeasibilityCheckTest {

    private val financialDataService = mockk<FinancialDataService>()
    private val fmpCacheService = mockk<FmpCacheService>(relaxed = true)
    private val fmpAdapter = mockk<FmpAdapter>(relaxed = true)
    private val greenwald = GreenwaldMaintenanceCapexEstimator()
    private val fcf = FcfFallbackEstimator()
    private val check = DcfFeasibilityCheck(
        financialDataService,
        fmpCacheService,
        fmpAdapter,
        greenwald,
        fcf,
    )

    @Test
    fun `GREENWALD feasible with full PPE history`() {
        val dataset = datasetFromSymbol("AAPL")
        every { financialDataService.getFinancialDataset("AAPL") } returns dataset

        val result = check.canApply("AAPL", DcfMethod.GREENWALD)

        assertThat(result.feasible).isTrue()
    }

    @Test
    fun `GREENWALD infeasible with short PPE history`() {
        val dataset = datasetFromSymbol("LOWPPE", balance = FmpFixtureLoader.shortBalanceSheets("LOWPPE"))
        every { financialDataService.getFinancialDataset("LOWPPE") } returns dataset

        val result = check.canApply("LOWPPE", DcfMethod.GREENWALD)

        assertThat(result.feasible).isFalse()
        assertThat(result.reason).isEqualTo("PPE_RATIO_HISTORY_INSUFFICIENT")
        assertThat(result.availableYears).isLessThan(5)
        assertThat(result.requiredYears).isEqualTo(5)
    }

    @Test
    fun `FCF_FALLBACK infeasible with no FCF years`() {
        val dataset = datasetFromSymbol(
            "SHORT",
            cashFlow = FmpFixtureLoader.shortCashFlows("SHORT").map {
                it.copy(freeCashFlow = null, operatingCashFlow = null, capitalExpenditure = null)
            },
        )
        every { financialDataService.getFinancialDataset("SHORT") } returns dataset

        val result = check.canApply("SHORT", DcfMethod.FCF_FALLBACK)

        assertThat(result.feasible).isFalse()
        assertThat(result.reason).isEqualTo("FCF_HISTORY_INSUFFICIENT")
    }

    @Test
    fun `FCF_FALLBACK feasible with at least one FCF year`() {
        val dataset = datasetFromSymbol("AAPL")
        every { financialDataService.getFinancialDataset("AAPL") } returns dataset

        val result = check.canApply("AAPL", DcfMethod.FCF_FALLBACK)

        assertThat(result.feasible).isTrue()
    }

    private fun datasetFromSymbol(
        symbol: String,
        balance: List<BalanceSheetDto> = FmpFixtureLoader.tenYearBalanceSheets(symbol),
        cashFlow: List<CashFlowDto> = FmpFixtureLoader.tenYearCashFlows(symbol),
    ): FinancialDataset {
        val now = Instant.parse("2024-06-01T10:00:00Z")
        return FinancialDataset(
            ticker = symbol,
            income = FmpFixtureLoader.tenYearIncomeStatements(symbol),
            balance = balance,
            cashFlow = cashFlow,
            keyMetrics = FmpFixtureLoader.tenYearKeyMetrics(symbol),
            dataSnapshotAt = now,
            isStale = false,
        )
    }
}
