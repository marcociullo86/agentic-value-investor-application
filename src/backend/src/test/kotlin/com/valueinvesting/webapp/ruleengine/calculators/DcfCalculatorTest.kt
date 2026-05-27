package com.valueinvesting.webapp.ruleengine.calculators

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DcfCalculatorTest {

    private val calculator = DcfCalculator(
        GreenwaldMaintenanceCapexEstimator(),
        FcfFallbackEstimator(),
    )

    @Test
    fun `positive ten year FCF history yields per-share intrinsic value`() {
        val dataset = syntheticDataset(years = 10, baseFcf = 100.0, growth = 0.06)
        val result = calculator.calculate(dataset)

        assertThat(result.method).isIn(DcfMethod.GREENWALD, DcfMethod.FCF_FALLBACK)
        assertThat(result.intrinsicValue).isNotNull().isPositive()
        // US-052: result is now per-share, not aggregate enterprise value.
        assertThat(result.intrinsicValueTotal).isNotNull().isPositive()
        assertThat(result.sharesUsed).isNotNull().isPositive()
        assertThat(result.intrinsicValue!!)
            .isCloseTo(result.intrinsicValueTotal!! / result.sharesUsed!!, org.assertj.core.api.Assertions.within(1e-6))
    }

    @Test
    fun `forced FCF_FALLBACK uses fallback even when greenwald data exists`() {
        val dataset = syntheticDataset(years = 10, baseFcf = 80.0, growth = 0.05)
        val result = calculator.calculate(dataset, forcedMethod = DcfMethod.FCF_FALLBACK)

        assertThat(result.method).isEqualTo(DcfMethod.FCF_FALLBACK)
        assertThat(result.intrinsicValue).isNotNull()
    }

    @Test
    fun `less than five years returns NOT_APPLICABLE`() {
        val dataset = syntheticDataset(years = 3, baseFcf = 50.0, growth = 0.05)
        val result = calculator.calculate(dataset)

        assertThat(result.method).isEqualTo(DcfMethod.NOT_APPLICABLE)
        assertThat(result.intrinsicValue).isNull()
    }

    @Test
    fun `missing shares outstanding yields per-share null but keeps total`() {
        val dataset = syntheticDataset(years = 10, baseFcf = 100.0, growth = 0.06, shares = null)
        val result = calculator.calculate(dataset)

        // US-052 regression: when shares are unavailable, per-share value is
        // null (MoS will short-circuit NOT_CALCULABLE) but the audit field
        // `intrinsicValueTotal` still exposes the enterprise-level DCF.
        assertThat(result.intrinsicValue).isNull()
        assertThat(result.intrinsicValueTotal).isNotNull().isPositive()
        assertThat(result.sharesUsed).isNull()
    }

    @Test
    fun `falls back to basic shares when diluted is missing`() {
        val dataset = syntheticDataset(
            years = 10,
            baseFcf = 100.0,
            growth = 0.06,
            shares = null,
            basicShares = 50_000_000.0,
        )
        val result = calculator.calculate(dataset)

        assertThat(result.intrinsicValue).isNotNull().isPositive()
        assertThat(result.sharesUsed).isEqualTo(50_000_000.0)
    }

    // TSK-254 / TSK-018-iter-1 — strict forced-method honor. When the user
    // forces GREENWALD via DcfMethodOverride but the dataset does not support
    // it (no PP&E history → Greenwald.usable=false), the calculator MUST NOT
    // silently fall back to FCF_FALLBACK. AnalyzeTickerService exposes
    // `dcfMethod=forcedMethod` for USER_OVERRIDE, so a silent swap would
    // publish dcfMethod=GREENWALD over an FCF-computed value. Expected
    // contract: method=NOT_APPLICABLE, intrinsicValue=null, rationale cites
    // the forced method and explicitly disclaims fallback.
    @Test
    fun `forced GREENWALD with no PPE returns NOT_APPLICABLE not silent FCF fallback`() {
        val dataset = syntheticDataset(
            years = 10,
            baseFcf = 100.0,
            growth = 0.06,
            includePpe = false,
        )

        val result = calculator.calculate(dataset, forcedMethod = DcfMethod.GREENWALD)

        assertThat(result.method).isEqualTo(DcfMethod.NOT_APPLICABLE)
        assertThat(result.intrinsicValue).isNull()
        assertThat(result.intrinsicValueTotal).isNull()
        assertThat(result.rationale)
            .contains("forcedMethod=GREENWALD")
            .contains("non utilizzabile")
            .contains("Nessun fallback")
    }

    // TSK-254 / TSK-018-iter-1 — symmetric strict-honor for FCF_FALLBACK.
    // When FCF history is insufficient (< MIN_HISTORICAL_YEARS=5) but the
    // user forces FCF_FALLBACK, the calculator must refuse explicitly
    // instead of falling through to the generic NOT_APPLICABLE path, so the
    // rationale tells the consumer that the *forced* method (not the
    // default policy) failed.
    @Test
    fun `forced FCF_FALLBACK with insufficient FCF history returns NOT_APPLICABLE with forced rationale`() {
        val dataset = syntheticDataset(years = 3, baseFcf = 50.0, growth = 0.05)

        val result = calculator.calculate(dataset, forcedMethod = DcfMethod.FCF_FALLBACK)

        assertThat(result.method).isEqualTo(DcfMethod.NOT_APPLICABLE)
        assertThat(result.intrinsicValue).isNull()
        assertThat(result.rationale)
            .contains("forcedMethod=FCF_FALLBACK")
            .contains("non utilizzabile")
    }

    // TSK-254 — control case: when forced GREENWALD IS usable, the
    // calculator honors it and produces a valid result (no spurious refusal
    // introduced by the strict guard).
    @Test
    fun `forced GREENWALD with sufficient data is honored`() {
        val dataset = syntheticDataset(years = 10, baseFcf = 100.0, growth = 0.06)

        val result = calculator.calculate(dataset, forcedMethod = DcfMethod.GREENWALD)

        assertThat(result.method).isEqualTo(DcfMethod.GREENWALD)
        assertThat(result.intrinsicValue).isNotNull().isPositive()
    }

    private fun syntheticDataset(
        years: Int,
        baseFcf: Double,
        growth: Double,
        shares: Double? = 100_000_000.0,
        basicShares: Double? = null,
        includePpe: Boolean = true,
    ): FinancialDataset {
        val income = mutableListOf<IncomeStatementDto>()
        val balance = mutableListOf<BalanceSheetDto>()
        val cashFlow = mutableListOf<CashFlowDto>()
        var revenue = 1_000.0
        var fcf = baseFcf

        for (year in 2024 downTo (2024 - years + 1)) {
            income += IncomeStatementDto(
                calendarYear = year.toString(),
                revenue = revenue,
                netIncome = fcf * 0.8,
                depreciationAndAmortization = fcf * 0.1,
                weightedAverageShsOutDil = shares,
                weightedAverageShsOut = basicShares,
            )
            balance += BalanceSheetDto(
                calendarYear = year.toString(),
                grossPpe = if (includePpe) revenue * 0.4 else null,
                propertyPlantEquipmentNet = if (includePpe) revenue * 0.35 else null,
            )
            cashFlow += CashFlowDto(
                calendarYear = year.toString(),
                freeCashFlow = fcf,
                operatingCashFlow = fcf + 10,
                capitalExpenditure = -20.0,
                depreciationAndAmortization = fcf * 0.1,
            )
            revenue *= (1.0 + growth)
            fcf *= (1.0 + growth)
        }

        return FinancialDataset(
            ticker = "TEST",
            income = income,
            balance = balance,
            cashFlow = cashFlow,
            keyMetrics = emptyList(),
            dataSnapshotAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
    }
}
