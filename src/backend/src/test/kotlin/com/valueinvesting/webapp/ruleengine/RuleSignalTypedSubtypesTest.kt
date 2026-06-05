@file:Suppress("DEPRECATION")
// @file-level suppress mirrors RuleSignal.kt: the 13 data-class subtypes declare
// the 3 legacy fields (observedValue / rationale / threshold) as constructor params,
// so Kotlin emits deprecation warnings on every access in test assertions.
// Suppressed here because (a) we WANT to verify legacy-field values to satisfy
// TSK-313 retrocompat AC and (b) the suppress is explicitly approved by ADR-028 §8
// (removal window R+3). Remove suppress together with the legacy fields at R+3.
package com.valueinvesting.webapp.ruleengine

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.ruleengine.rules.CapexIntensityRule
import com.valueinvesting.webapp.ruleengine.rules.CurrentRatioRule
import com.valueinvesting.webapp.ruleengine.rules.DebtToIncomeRule
import com.valueinvesting.webapp.ruleengine.rules.DividendContinuityRule
import com.valueinvesting.webapp.ruleengine.rules.EarningsStabilityRule
import com.valueinvesting.webapp.ruleengine.rules.EpsGrowthRule
import com.valueinvesting.webapp.ruleengine.rules.GrossMarginRule
import com.valueinvesting.webapp.ruleengine.rules.NetMarginRule
import com.valueinvesting.webapp.ruleengine.rules.Pe3yAvgRule
import com.valueinvesting.webapp.ruleengine.rules.PbLatestRule
import com.valueinvesting.webapp.ruleengine.rules.RoeRule
import com.valueinvesting.webapp.ruleengine.rules.RoicRule
import com.valueinvesting.webapp.ruleengine.rules.SizeRule
import com.valueinvesting.webapp.service.FinancialDataset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

// TSK-313 — QA Test unitari 13 sotto-tipi RuleSignal
//
// Verifica per ognuna delle 13 strategie ValuationRule (EP-021 / US-093):
//   1. Il DTO di output è il sotto-tipo concreto atteso.
//   2. I campi tipati specifici contengono i valori corretti dato un input deterministico.
//   3. La logica del segnale GREEN/YELLOW/RED/INDETERMINATE/NOT_CALCULABLE è invariata
//      rispetto ai test pre-refactor esistenti in rules/*.
//   4. Retrocompatibilità JSONB: deserializzazione di un payload legacy (senza campi tipati)
//      non solleva eccezioni e produce campi tipati null + campi legacy valorizzati.
//
// Deviazioni note documentate (TSK-312 handoff):
//   - Pe3yAvg / PbLatest: le soglie sono thresholdGreen / thresholdYellow (non un campo singolo
//     `threshold: Double`) perché entrambe le rule hanno DUE soglie semantiche, e il nome
//     `threshold` conflicterebbe con il campo legacy. Allineato a Roe10yAvg / Roic10yAvg.
//   - ROE / ROIC / GrossMargin / NetMargin / CapexIntensity: `averagePercent` è in form
//     percentuale (es. 15.0 per il 15%) mentre `observedValue` legacy resta in forma fraction
//     (es. 0.15). Verificato nei singoli test.
//   - EpsGrowth: `cagrPercent` è growth * 100 (es. 40.0), `observedValue` è growth ratio (0.40).
//
// Struttura:
//   - Un inner object per ogni ruleId.
//   - Almeno GREEN / RED (o YELLOW se disponibile) / INDETERMINATE (o NOT_CALCULABLE) per rule.
//   - Jackson retrocompat test in fondo (usa ObjectMapper con KotlinModule per coerenza con
//     Spring Boot autoconfigure che usa jackson-module-kotlin).
//
// Idiomi: JUnit 5 + AssertJ assertAll (stessa libreria delle rule test esistenti).
// Nessuna dipendenza Spring/WireMock/Mockito — pure unit test.
//
// [^src: management/kanban/EP-021-rulesignal-payload-refactor/US-093-rulesignal-schema-typed-be/TSK-313.md]
// [^src: design_&_architecture/decisions/ADR-028-rulesignal-typed-oneof-discriminator.md §3,§4,§8]
// [^src: management/kanban/EP-021-rulesignal-payload-refactor/US-093-rulesignal-schema-typed-be/US-093.md §AC]
class RuleSignalTypedSubtypesTest {

    // =========================================================================
    // 1. SIZE_LATEST — RuleSignal.Size
    // =========================================================================

    @Suppress("DEPRECATION")
    private val sizeRule = SizeRule()

    @Test
    fun `SIZE_LATEST GREEN — sotto-tipo Size con revenueLatest e thresholdUsd valorizzati`() {
        val dataset = incomeDataset(revenue = 500_000_000.0)

        val result = sizeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Size::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.Size
                assertThat(typed.revenueLatest).isEqualTo(500_000_000.0)
                assertThat(typed.thresholdUsd).isEqualTo(100_000_000L)
            },
            // Legacy invariata
            { assertThat(result.observedValue).isEqualTo(500_000_000.0) },
        )
    }

    @Test
    fun `SIZE_LATEST RED — revenueLatest sotto soglia`() {
        val dataset = incomeDataset(revenue = 50_000_000.0)

        val result = sizeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Size::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.Size
                assertThat(typed.revenueLatest).isEqualTo(50_000_000.0)
                assertThat(typed.thresholdUsd).isEqualTo(100_000_000L)
            },
        )
    }

    @Test
    fun `SIZE_LATEST INDETERMINATE — revenue null, revenueLatest null, thresholdUsd presente`() {
        val dataset = incomeDataset(revenue = null)

        val result = sizeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Size::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.Size
                assertThat(typed.revenueLatest).isNull()
                assertThat(typed.thresholdUsd).isEqualTo(100_000_000L)
            },
        )
    }

    // =========================================================================
    // 2. EARNINGS_STABILITY_10Y — RuleSignal.EarningsStability10y
    // =========================================================================

    private val earningsStabilityRule = EarningsStabilityRule()

    @Test
    fun `EARNINGS_STABILITY_10Y GREEN — yearsPositive 10, lossYears vuota`() {
        val dataset = tenYearIncomeDataset(lossYears = emptyList())

        val result = earningsStabilityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EarningsStability10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.EarningsStability10y
                assertThat(typed.yearsPositive).isEqualTo(10)
                assertThat(typed.yearsAvailable).isEqualTo(10)
                assertThat(typed.lossYears).isEmpty()
            },
            { assertThat(result.observedValue).isEqualTo(10.0) },
        )
    }

    @Test
    fun `EARNINGS_STABILITY_10Y YELLOW — yearsPositive 9, lossYears contiene anno di perdita`() {
        val dataset = tenYearIncomeDataset(lossYears = listOf(2020))

        val result = earningsStabilityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EarningsStability10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.EarningsStability10y
                assertThat(typed.yearsPositive).isEqualTo(9)
                assertThat(typed.yearsAvailable).isEqualTo(10)
                assertThat(typed.lossYears).contains(2020)
            },
        )
    }

    @Test
    fun `EARNINGS_STABILITY_10Y RED — yearsPositive 8, lossYears contiene 2 anni`() {
        val dataset = tenYearIncomeDataset(lossYears = listOf(2019, 2020))

        val result = earningsStabilityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EarningsStability10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.EarningsStability10y
                assertThat(typed.yearsPositive).isEqualTo(8)
                assertThat(typed.lossYears).containsExactlyInAnyOrder(2019, 2020)
            },
        )
    }

    @Test
    fun `EARNINGS_STABILITY_10Y NOT_CALCULABLE — income vuota, yearsPositive 0`() {
        val dataset = emptyIncomeDataset()

        val result = earningsStabilityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EarningsStability10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.EarningsStability10y
                assertThat(typed.yearsPositive).isEqualTo(0)
                assertThat(typed.yearsAvailable).isEqualTo(0)
                assertThat(typed.lossYears).isEmpty()
            },
        )
    }

    // =========================================================================
    // 3. EPS_GROWTH_10Y — RuleSignal.EpsGrowth10y
    // =========================================================================

    private val epsGrowthRule = EpsGrowthRule()

    @Test
    fun `EPS_GROWTH_10Y GREEN — cagrPercent 40, epsStart e epsEnd popolati`() {
        // avgInitial=1.0, avgFinal=1.4, growth=0.40 => cagrPercent=40.0
        val dataset = epsGrowthDataset(initialEps = List(3) { 1.0 }, finalEps = List(3) { 1.4 })

        val result = epsGrowthRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EpsGrowth10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.EpsGrowth10y
                assertThat(typed.cagrPercent).isNotNull
                assertThat(typed.cagrPercent!!).isCloseTo(40.0, within(1e-6))
                assertThat(typed.thresholdPercent).isCloseTo(33.0, within(1e-6))
                assertThat(typed.epsStart).isNotNull
                assertThat(typed.epsEnd).isNotNull
                assertThat(typed.yearStart).isEqualTo(2015)
                assertThat(typed.yearEnd).isEqualTo(2024)
            },
            // Legacy: observedValue = growth ratio (fraction), non percentuale
            { assertThat(result.observedValue).isCloseTo(0.40, within(1e-9)) },
        )
    }

    @Test
    fun `EPS_GROWTH_10Y YELLOW — cagrPercent tra 0 e 33`() {
        // avgInitial=1.0, avgFinal=1.2, growth=0.20 => cagrPercent=20.0
        val dataset = epsGrowthDataset(initialEps = List(3) { 1.0 }, finalEps = List(3) { 1.2 })

        val result = epsGrowthRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EpsGrowth10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.EpsGrowth10y
                assertThat(typed.cagrPercent!!).isCloseTo(20.0, within(1e-6))
            },
        )
    }

    @Test
    fun `EPS_GROWTH_10Y RED — cagrPercent negativo`() {
        // avgInitial=1.0, avgFinal=0.9, growth=-0.10 => cagrPercent=-10.0
        val dataset = epsGrowthDataset(initialEps = List(3) { 1.0 }, finalEps = List(3) { 0.9 })

        val result = epsGrowthRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EpsGrowth10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.EpsGrowth10y
                assertThat(typed.cagrPercent!!).isLessThan(0.0)
            },
        )
    }

    @Test
    fun `EPS_GROWTH_10Y INDETERMINATE — avgEpsInitial negativo, cagrPercent null`() {
        val dataset = epsGrowthDataset(initialEps = List(3) { -0.5 }, finalEps = List(3) { 1.0 })

        val result = epsGrowthRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EpsGrowth10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.EpsGrowth10y
                assertThat(typed.cagrPercent).isNull()
                assertThat(typed.epsStart).isNotNull()  // epsStart compilato (avgEpsInitial)
            },
        )
    }

    @Test
    fun `EPS_GROWTH_10Y NOT_CALCULABLE — income vuota, tutti i campi tipati null`() {
        val dataset = emptyIncomeDataset()

        val result = epsGrowthRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EpsGrowth10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.EpsGrowth10y
                assertThat(typed.cagrPercent).isNull()
                assertThat(typed.epsStart).isNull()
                assertThat(typed.epsEnd).isNull()
                assertThat(typed.yearStart).isNull()
                assertThat(typed.yearEnd).isNull()
                assertThat(typed.thresholdPercent).isCloseTo(33.0, within(1e-6))
            },
        )
    }

    // =========================================================================
    // 4. PE_3Y_AVG — RuleSignal.Pe3yAvg
    //    Deviazione ADR-028 §3: thresholdGreen=15.0, thresholdYellow=20.0
    // =========================================================================

    private val pe3yAvgRule = Pe3yAvgRule()

    @Test
    fun `PE_3Y_AVG GREEN — pe3yAvg 10 (price 150, avgEps 15), thresholdGreen 15`() {
        val dataset = pe3yDataset(currentPrice = 150.0, eps = 15.0)

        val result = pe3yAvgRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Pe3yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.Pe3yAvg
                assertThat(typed.pe3yAvg).isNotNull
                assertThat(typed.pe3yAvg!!).isCloseTo(10.0, within(1e-6))
                // Deviazione: thresholdGreen e thresholdYellow (non singola threshold Double)
                assertThat(typed.thresholdGreen).isEqualTo(15.0)
                assertThat(typed.thresholdYellow).isEqualTo(20.0)
            },
            // Legacy observedValue = pe3yAvg
            { assertThat(result.observedValue).isCloseTo(10.0, within(1e-6)) },
        )
    }

    @Test
    fun `PE_3Y_AVG YELLOW — pe3yAvg 16 point 67 (price 150, avgEps 9)`() {
        val dataset = pe3yDataset(currentPrice = 150.0, eps = 9.0)

        val result = pe3yAvgRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Pe3yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.Pe3yAvg
                assertThat(typed.pe3yAvg!!).isCloseTo(16.667, within(0.001))
            },
        )
    }

    @Test
    fun `PE_3Y_AVG RED — pe3yAvg 25 (price 200, avgEps 8)`() {
        val dataset = pe3yDataset(currentPrice = 200.0, eps = 8.0)

        val result = pe3yAvgRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Pe3yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.Pe3yAvg
                assertThat(typed.pe3yAvg!!).isCloseTo(25.0, within(1e-6))
            },
        )
    }

    @Test
    fun `PE_3Y_AVG INDETERMINATE — currentPrice null, pe3yAvg null`() {
        val dataset = pe3yDataset(currentPrice = null, eps = 10.0)

        val result = pe3yAvgRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Pe3yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.Pe3yAvg
                assertThat(typed.pe3yAvg).isNull()
            },
        )
    }

    @Test
    fun `PE_3Y_AVG NOT_CALCULABLE — income vuota`() {
        val dataset = makeDataset(currentPrice = 150.0, income = emptyList())

        val result = pe3yAvgRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Pe3yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
        )
    }

    // =========================================================================
    // 5. PB_LATEST — RuleSignal.PbLatest
    //    Deviazione ADR-028 §3: thresholdGreen=1.5, thresholdYellow=3.0
    // =========================================================================

    private val pbLatestRule = PbLatestRule()

    @Test
    fun `PB_LATEST GREEN — pbLatest 1 (price 100, book 100), thresholdGreen 1 point 5`() {
        val dataset = pbDataset(currentPrice = 100.0, bookValuePerShare = 100.0)

        val result = pbLatestRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.PbLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.PbLatest
                assertThat(typed.pbLatest).isNotNull
                assertThat(typed.pbLatest!!).isCloseTo(1.0, within(1e-6))
                assertThat(typed.thresholdGreen).isEqualTo(1.5)
                assertThat(typed.thresholdYellow).isEqualTo(3.0)
            },
            { assertThat(result.observedValue).isCloseTo(1.0, within(1e-6)) },
        )
    }

    @Test
    fun `PB_LATEST YELLOW — pbLatest 2 (price 200, book 100)`() {
        val dataset = pbDataset(currentPrice = 200.0, bookValuePerShare = 100.0)

        val result = pbLatestRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.PbLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.PbLatest
                assertThat(typed.pbLatest!!).isCloseTo(2.0, within(1e-6))
            },
        )
    }

    @Test
    fun `PB_LATEST RED — pbLatest 4 (price 400, book 100)`() {
        val dataset = pbDataset(currentPrice = 400.0, bookValuePerShare = 100.0)

        val result = pbLatestRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.PbLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.PbLatest
                assertThat(typed.pbLatest!!).isCloseTo(4.0, within(1e-6))
            },
        )
    }

    @Test
    fun `PB_LATEST INDETERMINATE — bookValuePerShare null`() {
        val dataset = pbDataset(currentPrice = 100.0, bookValuePerShare = null)

        val result = pbLatestRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.PbLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.PbLatest
                assertThat(typed.pbLatest).isNull()
            },
        )
    }

    @Test
    fun `PB_LATEST NOT_CALCULABLE — keyMetrics vuota`() {
        val dataset = makeDataset(currentPrice = 100.0)

        val result = pbLatestRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.PbLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
        )
    }

    // =========================================================================
    // 6. DIVIDEND_CONTINUITY_20Y — RuleSignal.DividendContinuity20y
    // =========================================================================

    private val dividendContinuityRule = DividendContinuityRule()

    @Test
    fun `DIVIDEND_CONTINUITY_20Y GREEN — consecutiveYears 20, thresholdYears 20`() {
        val dividends = consecutiveDividendYears(2005, 2024)
        val dataset = makeDataset(dividends = dividends)

        val result = dividendContinuityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DividendContinuity20y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.DividendContinuity20y
                assertThat(typed.consecutiveYears).isEqualTo(20)
                assertThat(typed.thresholdYears).isEqualTo(20)
            },
            { assertThat(result.observedValue).isEqualTo(20.0) },
        )
    }

    @Test
    fun `DIVIDEND_CONTINUITY_20Y YELLOW — streak 17 anni con span 25 anni`() {
        // Span 2000-2024 (25y); streak 2008-2024 = 17 consecutive years (gap 2000-2007)
        val dividends = (2008..2024).map { divRecord("$it-06-15") } +
            listOf(divRecord("2000-06-01"))

        val dataset = makeDataset(dividends = dividends)

        val result = dividendContinuityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DividendContinuity20y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.DividendContinuity20y
                assertThat(typed.consecutiveYears).isEqualTo(17)
                assertThat(typed.thresholdYears).isEqualTo(20)
            },
        )
    }

    @Test
    fun `DIVIDEND_CONTINUITY_20Y RED — streak 12 anni con span 25 anni`() {
        // Span 2000-2024 (25y); streak from 2013 = 12 years; gap at 2012
        val dividends = (2000..2011).map { divRecord("$it-06-15") } +
            (2013..2024).map { divRecord("$it-06-15") }
        val dataset = makeDataset(dividends = dividends)

        val result = dividendContinuityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DividendContinuity20y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.DividendContinuity20y
                assertThat(typed.consecutiveYears).isEqualTo(12)
            },
        )
    }

    @Test
    fun `DIVIDEND_CONTINUITY_20Y INDETERMINATE — lista vuota, consecutiveYears null`() {
        val dataset = makeDataset(dividends = emptyList())

        val result = dividendContinuityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DividendContinuity20y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.DividendContinuity20y
                assertThat(typed.consecutiveYears).isNull()
                assertThat(typed.thresholdYears).isEqualTo(20)
            },
        )
    }

    // =========================================================================
    // 7. ROE_10Y_AVG — RuleSignal.Roe10yAvg
    //    Deviazione: averagePercent in form percentuale (es. 18.0), observedValue in fraction (0.18)
    // =========================================================================

    private val roeRule = RoeRule()

    @Test
    fun `ROE_10Y_AVG GREEN — averagePercent in forma percentuale, non fraction`() {
        // ROE fractions all 0.20 => average fraction = 0.20, averagePercent = 20.0
        val dataset = keyMetricsDataset(roe = List(10) { 0.20 })

        val result = roeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roe10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.Roe10yAvg
                // averagePercent deve essere 20.0 (non 0.20)
                assertThat(typed.averagePercent).isNotNull
                assertThat(typed.averagePercent!!).isCloseTo(20.0, within(1e-6))
                assertThat(typed.yearsAvailable).isEqualTo(10)
                assertThat(typed.thresholdGreenPercent).isCloseTo(15.0, within(1e-6))
                assertThat(typed.thresholdYellowPercent).isCloseTo(10.0, within(1e-6))
            },
            // Legacy: observedValue = fraction 0.20 (invariato)
            { assertThat(result.observedValue!!).isCloseTo(0.20, within(1e-9)) },
        )
    }

    @Test
    fun `ROE_10Y_AVG YELLOW — average 12 percent`() {
        val dataset = keyMetricsDataset(roe = List(10) { 0.12 })

        val result = roeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roe10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.Roe10yAvg
                assertThat(typed.averagePercent!!).isCloseTo(12.0, within(1e-6))
            },
        )
    }

    @Test
    fun `ROE_10Y_AVG RED — average 7 percent`() {
        val dataset = keyMetricsDataset(roe = List(10) { 0.07 })

        val result = roeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roe10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
        )
    }

    @Test
    fun `ROE_10Y_AVG INDETERMINATE — solo 4 anni validi`() {
        val dataset = keyMetricsDataset(roe = listOf(0.05, 0.06, 0.07, 0.04))

        val result = roeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roe10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.Roe10yAvg
                assertThat(typed.yearsAvailable).isEqualTo(4)
                assertThat(typed.averagePercent).isNotNull()
            },
        )
    }

    @Test
    fun `ROE_10Y_AVG NOT_CALCULABLE — keyMetrics vuota`() {
        val dataset = makeDataset()

        val result = roeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roe10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.Roe10yAvg
                assertThat(typed.averagePercent).isNull()
            },
        )
    }

    // =========================================================================
    // 8. ROIC_10Y_AVG — RuleSignal.Roic10yAvg
    //    Struttura identica a Roe10yAvg (stessa nota su averagePercent vs fraction)
    // =========================================================================

    private val roicRule = RoicRule()

    @Test
    fun `ROIC_10Y_AVG GREEN — averagePercent in forma percentuale`() {
        // ROIC fractions all 0.15 => fraction average = 0.15, averagePercent = 15.0
        val dataset = keyMetricsDataset(roic = List(10) { 0.15 })

        val result = roicRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roic10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.Roic10yAvg
                assertThat(typed.averagePercent!!).isCloseTo(15.0, within(1e-6))
                assertThat(typed.thresholdGreenPercent).isCloseTo(12.0, within(1e-6))
                assertThat(typed.thresholdYellowPercent).isCloseTo(8.0, within(1e-6))
            },
            { assertThat(result.observedValue!!).isCloseTo(0.15, within(1e-9)) },
        )
    }

    @Test
    fun `ROIC_10Y_AVG YELLOW — average 10 percent`() {
        val dataset = keyMetricsDataset(roic = List(10) { 0.10 })

        val result = roicRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roic10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.Roic10yAvg
                assertThat(typed.averagePercent!!).isCloseTo(10.0, within(1e-6))
            },
        )
    }

    @Test
    fun `ROIC_10Y_AVG RED — average 5 percent`() {
        val dataset = keyMetricsDataset(roic = List(10) { 0.05 })

        val result = roicRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roic10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
        )
    }

    @Test
    fun `ROIC_10Y_AVG NOT_CALCULABLE — keyMetrics vuota`() {
        val dataset = makeDataset()

        val result = roicRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roic10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.Roic10yAvg
                assertThat(typed.averagePercent).isNull()
            },
        )
    }

    // =========================================================================
    // 9. GROSS_MARGIN_10Y_AVG — RuleSignal.GrossMargin10yAvg
    //    averagePercent in forma percentuale; observedValue legacy in fraction
    // =========================================================================

    private val grossMarginRule = GrossMarginRule()

    @Test
    fun `GROSS_MARGIN_10Y_AVG GREEN — averagePercent 45 (grossProfitRatio 0 point 45)`() {
        // grossProfitRatio = 0.45 => GREEN (> 40%)
        val income = List(10) { i ->
            IncomeStatementDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                grossProfitRatio = 0.45,
            )
        }
        val dataset = makeDataset(income = income)

        val result = grossMarginRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.GrossMargin10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.GrossMargin10yAvg
                assertThat(typed.averagePercent!!).isCloseTo(45.0, within(1e-6))
                assertThat(typed.thresholdGreenPercent).isCloseTo(40.0, within(1e-6))
                assertThat(typed.thresholdYellowPercent).isCloseTo(30.0, within(1e-6))
            },
            { assertThat(result.observedValue!!).isCloseTo(0.45, within(1e-9)) },
        )
    }

    @Test
    fun `GROSS_MARGIN_10Y_AVG YELLOW — averagePercent 35`() {
        val income = List(10) { i ->
            IncomeStatementDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                grossProfitRatio = 0.35,
            )
        }
        val dataset = makeDataset(income = income)

        val result = grossMarginRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.GrossMargin10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.GrossMargin10yAvg
                assertThat(typed.averagePercent!!).isCloseTo(35.0, within(1e-6))
            },
        )
    }

    @Test
    fun `GROSS_MARGIN_10Y_AVG RED — averagePercent 25`() {
        val income = List(10) { i ->
            IncomeStatementDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                grossProfitRatio = 0.25,
            )
        }
        val dataset = makeDataset(income = income)

        val result = grossMarginRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.GrossMargin10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
        )
    }

    @Test
    fun `GROSS_MARGIN_10Y_AVG NOT_CALCULABLE — income vuota`() {
        val dataset = emptyIncomeDataset()

        val result = grossMarginRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.GrossMargin10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.GrossMargin10yAvg
                assertThat(typed.averagePercent).isNull()
            },
        )
    }

    // =========================================================================
    // 10. NET_MARGIN_10Y_AVG — RuleSignal.NetMargin10yAvg
    //     Binario: GREEN se avg > 10%, altrimenti RED. Nessun YELLOW band.
    //     averagePercent in forma percentuale; observedValue legacy in fraction.
    // =========================================================================

    private val netMarginRule = NetMarginRule()

    @Test
    fun `NET_MARGIN_10Y_AVG GREEN — averagePercent 15`() {
        val income = List(10) { i ->
            IncomeStatementDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                netIncomeRatio = 0.15,
            )
        }
        val dataset = makeDataset(income = income)

        val result = netMarginRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.NetMargin10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.NetMargin10yAvg
                assertThat(typed.averagePercent!!).isCloseTo(15.0, within(1e-6))
                assertThat(typed.thresholdGreenPercent).isCloseTo(10.0, within(1e-6))
            },
            { assertThat(result.observedValue!!).isCloseTo(0.15, within(1e-9)) },
        )
    }

    @Test
    fun `NET_MARGIN_10Y_AVG RED — averagePercent 8 (classificazione binaria senza YELLOW)`() {
        val income = List(10) { i ->
            IncomeStatementDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                netIncomeRatio = 0.08,
            )
        }
        val dataset = makeDataset(income = income)

        val result = netMarginRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.NetMargin10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
            // Nessun YELLOW band su NetMargin: le due soglie sono identiche (threshold unica)
            { assertThat(result.signal).isNotEqualTo(Signal.YELLOW) },
        )
    }

    @Test
    fun `NET_MARGIN_10Y_AVG NOT_CALCULABLE — income vuota`() {
        val dataset = emptyIncomeDataset()

        val result = netMarginRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.NetMargin10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.NetMargin10yAvg
                assertThat(typed.averagePercent).isNull()
            },
        )
    }

    // =========================================================================
    // 11. CURRENT_RATIO_LATEST — RuleSignal.CurrentRatioLatest
    // =========================================================================

    private val currentRatioRule = CurrentRatioRule()

    @Test
    fun `CURRENT_RATIO_LATEST GREEN — ratioLatest 2 point 5, thresholdGreen 2`() {
        val dataset = balanceDataset(currentAssets = 250.0, currentLiabilities = 100.0)

        val result = currentRatioRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CurrentRatioLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.CurrentRatioLatest
                assertThat(typed.ratioLatest!!).isCloseTo(2.5, within(1e-6))
                assertThat(typed.thresholdGreen).isEqualTo(2.0)
                assertThat(typed.thresholdYellow).isEqualTo(1.5)
            },
            { assertThat(result.observedValue!!).isCloseTo(2.5, within(1e-6)) },
        )
    }

    @Test
    fun `CURRENT_RATIO_LATEST YELLOW — ratioLatest 1 point 7`() {
        val dataset = balanceDataset(currentAssets = 170.0, currentLiabilities = 100.0)

        val result = currentRatioRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CurrentRatioLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.CurrentRatioLatest
                assertThat(typed.ratioLatest!!).isCloseTo(1.7, within(1e-6))
            },
        )
    }

    @Test
    fun `CURRENT_RATIO_LATEST RED — ratioLatest 1 point 2`() {
        val dataset = balanceDataset(currentAssets = 120.0, currentLiabilities = 100.0)

        val result = currentRatioRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CurrentRatioLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
        )
    }

    @Test
    fun `CURRENT_RATIO_LATEST INDETERMINATE — currentAssets null`() {
        val dataset = balanceDataset(currentAssets = null, currentLiabilities = 100.0)

        val result = currentRatioRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CurrentRatioLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            {
                val typed = result as RuleSignal.CurrentRatioLatest
                assertThat(typed.ratioLatest).isNull()
            },
        )
    }

    @Test
    fun `CURRENT_RATIO_LATEST NOT_CALCULABLE — balance vuota`() {
        val dataset = makeDataset()

        val result = currentRatioRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CurrentRatioLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
        )
    }

    // =========================================================================
    // 12. DEBT_TO_INCOME_LATEST — RuleSignal.DebtToIncomeLatest
    //     netIncomePositive: Boolean — campo tipato aggiuntivo
    // =========================================================================

    private val debtToIncomeRule = DebtToIncomeRule()

    @Test
    fun `DEBT_TO_INCOME_LATEST GREEN — ratio 2, netIncomePositive true`() {
        // longTermDebt=200, netIncome=100 => ratio=2.0 < 4 => GREEN
        val dataset = debtToIncomeDataset(longTermDebt = 200.0, netIncome = 100.0)

        val result = debtToIncomeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DebtToIncomeLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.DebtToIncomeLatest
                assertThat(typed.ratioLatest!!).isCloseTo(2.0, within(1e-6))
                assertThat(typed.thresholdGreen).isEqualTo(4.0)
                assertThat(typed.thresholdYellow).isEqualTo(5.0)
                assertThat(typed.netIncomePositive).isTrue()
            },
            { assertThat(result.observedValue!!).isCloseTo(2.0, within(1e-6)) },
        )
    }

    @Test
    fun `DEBT_TO_INCOME_LATEST YELLOW — ratio 4 point 5`() {
        // longTermDebt=450, netIncome=100 => ratio=4.5 in [4,5] => YELLOW
        val dataset = debtToIncomeDataset(longTermDebt = 450.0, netIncome = 100.0)

        val result = debtToIncomeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DebtToIncomeLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.DebtToIncomeLatest
                assertThat(typed.ratioLatest!!).isCloseTo(4.5, within(1e-6))
                assertThat(typed.netIncomePositive).isTrue()
            },
        )
    }

    @Test
    fun `DEBT_TO_INCOME_LATEST RED — ratio 6`() {
        // longTermDebt=600, netIncome=100 => ratio=6.0 > 5 => RED
        val dataset = debtToIncomeDataset(longTermDebt = 600.0, netIncome = 100.0)

        val result = debtToIncomeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DebtToIncomeLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
        )
    }

    @Test
    fun `DEBT_TO_INCOME_LATEST INDETERMINATE — netIncome negativo, netIncomePositive false`() {
        val dataset = debtToIncomeDataset(longTermDebt = 200.0, netIncome = -50.0)

        val result = debtToIncomeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DebtToIncomeLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            // US-009 AC verbatim: NEVER RED when netIncome <= 0
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
            {
                val typed = result as RuleSignal.DebtToIncomeLatest
                assertThat(typed.netIncomePositive).isFalse()
                assertThat(typed.ratioLatest).isNull()
            },
        )
    }

    @Test
    fun `DEBT_TO_INCOME_LATEST NOT_CALCULABLE — balance e income vuote`() {
        val dataset = makeDataset()

        val result = debtToIncomeRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.DebtToIncomeLatest::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.DebtToIncomeLatest
                assertThat(typed.netIncomePositive).isFalse()
            },
        )
    }

    // =========================================================================
    // 13. CAPEX_INTENSITY_10Y_AVG — RuleSignal.CapexIntensity10yAvg
    //     averagePercent in forma percentuale; observedValue legacy in fraction
    // =========================================================================

    private val capexIntensityRule = CapexIntensityRule()

    @Test
    fun `CAPEX_INTENSITY_10Y_AVG GREEN — averagePercent 20 (capex 20 percent di netIncome)`() {
        // capex/netIncome = 200/1000 = 0.20 = 20% => GREEN (< 25%)
        val income = List(10) { i ->
            IncomeStatementDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                netIncome = 1_000.0,
            )
        }
        val cashFlow = List(10) { i ->
            CashFlowDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                capitalExpenditure = -200.0,  // FMP convention: negativo
            )
        }
        val dataset = makeDataset(income = income, cashFlow = cashFlow)

        val result = capexIntensityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CapexIntensity10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.CapexIntensity10yAvg
                assertThat(typed.averagePercent!!).isCloseTo(20.0, within(1e-6))
                assertThat(typed.thresholdGreenPercent).isCloseTo(25.0, within(1e-6))
                assertThat(typed.thresholdYellowPercent).isCloseTo(30.0, within(1e-6))
            },
            // Legacy: observedValue = fraction 0.20
            { assertThat(result.observedValue!!).isCloseTo(0.20, within(1e-9)) },
        )
    }

    @Test
    fun `CAPEX_INTENSITY_10Y_AVG YELLOW — averagePercent 27 point 5`() {
        // capex/netIncome = 275/1000 = 0.275 = 27.5% => YELLOW (25%-30%)
        val income = List(10) { i ->
            IncomeStatementDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                netIncome = 1_000.0,
            )
        }
        val cashFlow = List(10) { i ->
            CashFlowDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                capitalExpenditure = -275.0,
            )
        }
        val dataset = makeDataset(income = income, cashFlow = cashFlow)

        val result = capexIntensityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CapexIntensity10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.YELLOW) },
            {
                val typed = result as RuleSignal.CapexIntensity10yAvg
                assertThat(typed.averagePercent!!).isCloseTo(27.5, within(1e-6))
            },
        )
    }

    @Test
    fun `CAPEX_INTENSITY_10Y_AVG RED — averagePercent 40`() {
        // capex/netIncome = 400/1000 = 0.40 = 40% => RED (> 30%)
        val income = List(10) { i ->
            IncomeStatementDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                netIncome = 1_000.0,
            )
        }
        val cashFlow = List(10) { i ->
            CashFlowDto(
                date = "${2024 - i}-12-31",
                calendarYear = (2024 - i).toString(),
                capitalExpenditure = -400.0,
            )
        }
        val dataset = makeDataset(income = income, cashFlow = cashFlow)

        val result = capexIntensityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CapexIntensity10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.RED) },
        )
    }

    @Test
    fun `CAPEX_INTENSITY_10Y_AVG NOT_CALCULABLE — cashFlow vuoto`() {
        val dataset = makeDataset(
            income = listOf(IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", netIncome = 1_000.0)),
            cashFlow = emptyList(),
        )

        val result = capexIntensityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CapexIntensity10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.NOT_CALCULABLE) },
            {
                val typed = result as RuleSignal.CapexIntensity10yAvg
                assertThat(typed.averagePercent).isNull()
            },
        )
    }

    @Test
    fun `CAPEX_INTENSITY_10Y_AVG INDETERMINATE — netIncome negativo nel latest year`() {
        // Un anno solo con netIncome negativo -> latest-year branch -> INDETERMINATE
        val income = listOf(
            IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", netIncome = -500.0),
        )
        val cashFlow = listOf(
            CashFlowDto(date = "2024-12-31", calendarYear = "2024", capitalExpenditure = -200.0),
        )
        val dataset = makeDataset(income = income, cashFlow = cashFlow)

        val result = capexIntensityRule.evaluate(dataset)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.CapexIntensity10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.INDETERMINATE) },
            { assertThat(result.signal).isNotEqualTo(Signal.RED) },
        )
    }

    // =========================================================================
    // 14. Test retrocompatibilità JSONB (ADR-028 §4)
    //     Deserializzazione payload legacy (senza campi tipati) via ObjectMapper
    //     Jackson polimorfico. Campi tipati attesi = null; campi legacy valorizzati.
    // =========================================================================

    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .also { mapper ->
            // Registra il modulo per subtypes polimorfici — identica config usata da
            // Spring Boot (jackson-module-kotlin + ObjectMapper Spring autoconfigure).
            // findAndRegisterModules() aggiunge i moduli presenti nel classpath.
            mapper.findAndRegisterModules()
        }

    @Test
    fun `Jackson retrocompat SIZE_LATEST — payload senza campi tipati, revenueLatest null`() {
        // ADR-028 §4: record JSONB pre-EP-021 senza campi tipati. Jackson deve
        // (a) non sollevare eccezioni, (b) produrre RuleSignal.Size con revenueLatest=null,
        // (c) preservare observedValue e rationale legacy.
        val legacyJson = """
            {
                "ruleId":       "SIZE_LATEST",
                "signal":       "GREEN",
                "observedValue": 500000000.0,
                "threshold":    ">= ${'$'}100M (GREEN), < ${'$'}100M (RED)",
                "rationale":    "Revenue 2023: ${'$'}500M."
            }
        """.trimIndent()

        val result: RuleSignal = objectMapper.readValue(legacyJson, RuleSignal::class.java)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Size::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            {
                val typed = result as RuleSignal.Size
                // Campi tipati assenti nel payload -> null (ADR-028 §4)
                assertThat(typed.revenueLatest).isNull()
                // thresholdUsd: campo non-nullable Long; Jackson assegna default 0L se assente
                // (data class senza default — comportamento Jackson su campi required mancanti)
            },
            // Legacy campi preservati
            { assertThat(result.observedValue).isEqualTo(500_000_000.0) },
            { assertThat(result.rationale).contains("500M") },
        )
    }

    @Test
    fun `Jackson retrocompat EARNINGS_STABILITY_10Y — campi tipati assenti producono valori di default`() {
        val legacyJson = """
            {
                "ruleId":       "EARNINGS_STABILITY_10Y",
                "signal":       "GREEN",
                "observedValue": 10.0,
                "threshold":    "10/10 (GREEN)",
                "rationale":    "10/10 esercizi positivi."
            }
        """.trimIndent()

        val result: RuleSignal = objectMapper.readValue(legacyJson, RuleSignal::class.java)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.EarningsStability10y::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(10.0) },
        )
    }

    @Test
    fun `Jackson retrocompat ROE_10Y_AVG — payload legacy, tipo corretto`() {
        val legacyJson = """
            {
                "ruleId":       "ROE_10Y_AVG",
                "signal":       "GREEN",
                "observedValue": 0.18,
                "threshold":    "> 15% (GREEN)",
                "rationale":    "Media ROE su 10 esercizi: 18.00%."
            }
        """.trimIndent()

        val result: RuleSignal = objectMapper.readValue(legacyJson, RuleSignal::class.java)

        assertAll(
            { assertThat(result).isInstanceOf(RuleSignal.Roe10yAvg::class.java) },
            { assertThat(result.signal).isEqualTo(Signal.GREEN) },
            { assertThat(result.observedValue).isEqualTo(0.18) },
        )
    }

    @Test
    fun `Jackson discriminator funziona per tutti i 13 ruleId senza eccezioni`() {
        // Smoke-test: verifica che Jackson possa deserializzare un payload minimo per
        // ognuno dei 13 ruleId senza sollevare UnrecognizedPropertyException o
        // InvalidTypeIdException. Solo il tipo di output viene asserito (isInstanceOf).
        val ruleIdToClass: Map<String, Class<out RuleSignal>> = mapOf(
            "SIZE_LATEST" to RuleSignal.Size::class.java,
            "EARNINGS_STABILITY_10Y" to RuleSignal.EarningsStability10y::class.java,
            "EPS_GROWTH_10Y" to RuleSignal.EpsGrowth10y::class.java,
            "PE_3Y_AVG" to RuleSignal.Pe3yAvg::class.java,
            "PB_LATEST" to RuleSignal.PbLatest::class.java,
            "DIVIDEND_CONTINUITY_20Y" to RuleSignal.DividendContinuity20y::class.java,
            "ROE_10Y_AVG" to RuleSignal.Roe10yAvg::class.java,
            "ROIC_10Y_AVG" to RuleSignal.Roic10yAvg::class.java,
            "GROSS_MARGIN_10Y_AVG" to RuleSignal.GrossMargin10yAvg::class.java,
            "NET_MARGIN_10Y_AVG" to RuleSignal.NetMargin10yAvg::class.java,
            "CURRENT_RATIO_LATEST" to RuleSignal.CurrentRatioLatest::class.java,
            "DEBT_TO_INCOME_LATEST" to RuleSignal.DebtToIncomeLatest::class.java,
            "CAPEX_INTENSITY_10Y_AVG" to RuleSignal.CapexIntensity10yAvg::class.java,
        )

        ruleIdToClass.forEach { (ruleId, expectedClass) ->
            val json = """{"ruleId":"$ruleId","signal":"INDETERMINATE","observedValue":null,"threshold":"n/a","rationale":"legacy"}"""
            val result = objectMapper.readValue(json, RuleSignal::class.java)
            assertThat(result)
                .describedAs("ruleId=$ruleId deve deserializzare in ${expectedClass.simpleName}")
                .isInstanceOf(expectedClass)
        }
    }

    // =========================================================================
    // Helpers privati
    // =========================================================================

    private val SNAPSHOT: Instant = Instant.parse("2026-05-24T00:00:00Z")

    /** Dataset con una sola riga income (ultimo anno) con il revenue indicato. */
    private fun incomeDataset(revenue: Double?): FinancialDataset =
        makeDataset(income = listOf(IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", revenue = revenue)))

    /** Dataset income vuoto (NOT_CALCULABLE per molte rule). */
    private fun emptyIncomeDataset(): FinancialDataset = makeDataset(income = emptyList())

    /** Dataset con 10 anni di income (2015-2024); gli anni in [lossYears] hanno netIncome negativo. */
    private fun tenYearIncomeDataset(lossYears: List<Int>): FinancialDataset {
        val income = (2015..2024).map { year ->
            IncomeStatementDto(
                date = "$year-12-31",
                calendarYear = year.toString(),
                netIncome = if (year in lossYears) -1_000_000.0 else 1_000_000.0,
            )
        }
        return makeDataset(income = income)
    }

    /**
     * Dataset a 10 anni (2015-2024) con EPS specifici per EpsGrowthRule.
     * [initialEps] = anni 2015-2017 (baseline), [finalEps] = anni 2022-2024 (finale).
     * Anni medi 2018-2021 = 2.0 (neutri).
     */
    private fun epsGrowthDataset(initialEps: List<Double?>, finalEps: List<Double?>): FinancialDataset {
        val midEps = listOf(2.0, 2.0, 2.0, 2.0)
        val allEps = initialEps + midEps + finalEps
        val years = (2015..2024).toList()
        val income = years.zip(allEps).map { (year, eps) ->
            IncomeStatementDto(date = "$year-12-31", calendarYear = year.toString(), eps = eps)
        }
        return makeDataset(income = income)
    }

    /** Dataset con 3 anni di income (2022-2024), stesso eps per tutti, e currentPrice impostato. */
    private fun pe3yDataset(currentPrice: Double?, eps: Double): FinancialDataset =
        makeDataset(
            currentPrice = currentPrice,
            income = listOf(
                IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", eps = eps),
                IncomeStatementDto(date = "2023-12-31", calendarYear = "2023", eps = eps),
                IncomeStatementDto(date = "2022-12-31", calendarYear = "2022", eps = eps),
            ),
        )

    /** Dataset con una riga keyMetrics contenente bookValuePerShare. */
    private fun pbDataset(currentPrice: Double?, bookValuePerShare: Double?): FinancialDataset =
        makeDataset(
            currentPrice = currentPrice,
            keyMetrics = listOf(
                KeyMetricsDto(
                    date = "2024-12-31",
                    calendarYear = "2024",
                    bookValuePerShare = bookValuePerShare,
                )
            ),
        )

    /** Dataset con 10 anni di keyMetrics con il campo ROE impostato. */
    private fun keyMetricsDataset(
        roe: List<Double?> = emptyList(),
        roic: List<Double?> = emptyList(),
    ): FinancialDataset {
        val metrics = when {
            roe.isNotEmpty() -> roe.mapIndexed { i, v ->
                KeyMetricsDto(symbol = "TEST", calendarYear = (2024 - i).toString(), roe = v)
            }
            roic.isNotEmpty() -> roic.mapIndexed { i, v ->
                KeyMetricsDto(symbol = "TEST", calendarYear = (2024 - i).toString(), roic = v)
            }
            else -> emptyList()
        }
        return makeDataset(keyMetrics = metrics)
    }

    /** Dataset con una riga balance sheet con assets e liabilities. */
    private fun balanceDataset(currentAssets: Double?, currentLiabilities: Double?): FinancialDataset =
        makeDataset(
            balance = listOf(
                BalanceSheetDto(
                    date = "2024-12-31",
                    calendarYear = "2024",
                    totalCurrentAssets = currentAssets,
                    totalCurrentLiabilities = currentLiabilities,
                )
            ),
        )

    /** Dataset con una riga balance (longTermDebt) e una income (netIncome) per DebtToIncomeRule. */
    private fun debtToIncomeDataset(longTermDebt: Double?, netIncome: Double?): FinancialDataset =
        makeDataset(
            income = listOf(IncomeStatementDto(date = "2024-12-31", calendarYear = "2024", netIncome = netIncome)),
            balance = listOf(BalanceSheetDto(date = "2024-12-31", calendarYear = "2024", longTermDebt = longTermDebt)),
        )

    /** Record dividendo minimo valido per DividendContinuityRule. */
    private fun divRecord(date: String): DividendRecord =
        DividendRecord(date = date, dividend = 0.25, adjDividend = 0.25)

    /** Lista di DividendRecord consecutivi da fromYear a toYear (un record per anno). */
    private fun consecutiveDividendYears(fromYear: Int, toYear: Int): List<DividendRecord> =
        (fromYear..toYear).map { year -> divRecord("$year-06-15") }

    /**
     * Factory centrale per FinancialDataset — parametri opzionali con default a empty.
     * Riduce il boilerplate in ogni test.
     */
    private fun makeDataset(
        ticker: String = "TEST",
        income: List<IncomeStatementDto> = emptyList(),
        balance: List<BalanceSheetDto> = emptyList(),
        cashFlow: List<CashFlowDto> = emptyList(),
        keyMetrics: List<KeyMetricsDto> = emptyList(),
        dividends: List<DividendRecord> = emptyList(),
        currentPrice: Double? = null,
    ): FinancialDataset = FinancialDataset(
        ticker = ticker,
        income = income,
        balance = balance,
        cashFlow = cashFlow,
        keyMetrics = keyMetrics,
        dataSnapshotAt = SNAPSHOT,
        currentPrice = currentPrice,
        dividends = dividends,
    )
}
