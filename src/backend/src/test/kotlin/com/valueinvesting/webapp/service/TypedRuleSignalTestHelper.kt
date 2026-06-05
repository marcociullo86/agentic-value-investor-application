@file:Suppress("DEPRECATION")

package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal

// Test helper (TSK-312 EP-021): dispatcher tipato per costruire un sotto-tipo
// concreto di `RuleSignal` a partire da (ruleId, signal). Sostituisce la
// factory shim top-level `fun RuleSignal(ruleId, signal, ...)` rimossa al
// completamento di TSK-312.
//
// Uso: i test che verificano solo segnali aggregati (Munger decision /
// verdict cascade) non leggono i campi tipati specifici, quindi i campi
// tipati sono valorizzati a null/default. Per fixture che leggono i campi
// tipati (es. snapshot test del payload) usare il costruttore tipato diretto.
//
// [^src: management/kanban/EP-021-rulesignal-payload-refactor/US-093-rulesignal-schema-typed-be/TSK-312.md §Obiettivo]
// [^src: design_&_architecture/decisions/ADR-028-rulesignal-typed-oneof-discriminator.md §1, §3]
internal fun typedRuleSignal(id: String, signal: Signal): RuleSignal = when (id) {
    "SIZE_LATEST" -> RuleSignal.Size(
        signal = signal,
        revenueLatest = null,
        thresholdUsd = 0L,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "EARNINGS_STABILITY_10Y" -> RuleSignal.EarningsStability10y(
        signal = signal,
        yearsPositive = 0,
        yearsAvailable = 0,
        lossYears = emptyList(),
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "EPS_GROWTH_10Y" -> RuleSignal.EpsGrowth10y(
        signal = signal,
        cagrPercent = null,
        thresholdPercent = 0.0,
        epsStart = null,
        epsEnd = null,
        yearStart = null,
        yearEnd = null,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "PE_3Y_AVG" -> RuleSignal.Pe3yAvg(
        signal = signal,
        pe3yAvg = null,
        thresholdGreen = 0.0,
        thresholdYellow = 0.0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "PB_LATEST" -> RuleSignal.PbLatest(
        signal = signal,
        pbLatest = null,
        thresholdGreen = 0.0,
        thresholdYellow = 0.0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "DIVIDEND_CONTINUITY_20Y" -> RuleSignal.DividendContinuity20y(
        signal = signal,
        consecutiveYears = null,
        thresholdYears = 0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "ROE_10Y_AVG" -> RuleSignal.Roe10yAvg(
        signal = signal,
        averagePercent = null,
        yearsAvailable = 0,
        thresholdGreenPercent = 0.0,
        thresholdYellowPercent = 0.0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "ROIC_10Y_AVG" -> RuleSignal.Roic10yAvg(
        signal = signal,
        averagePercent = null,
        yearsAvailable = 0,
        thresholdGreenPercent = 0.0,
        thresholdYellowPercent = 0.0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "GROSS_MARGIN_10Y_AVG" -> RuleSignal.GrossMargin10yAvg(
        signal = signal,
        averagePercent = null,
        thresholdGreenPercent = 0.0,
        thresholdYellowPercent = 0.0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "NET_MARGIN_10Y_AVG" -> RuleSignal.NetMargin10yAvg(
        signal = signal,
        averagePercent = null,
        thresholdGreenPercent = 0.0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "CURRENT_RATIO_LATEST" -> RuleSignal.CurrentRatioLatest(
        signal = signal,
        ratioLatest = null,
        thresholdGreen = 0.0,
        thresholdYellow = 0.0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "DEBT_TO_INCOME_LATEST" -> RuleSignal.DebtToIncomeLatest(
        signal = signal,
        ratioLatest = null,
        thresholdGreen = 0.0,
        thresholdYellow = 0.0,
        netIncomePositive = true,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    "CAPEX_INTENSITY_10Y_AVG" -> RuleSignal.CapexIntensity10yAvg(
        signal = signal,
        averagePercent = null,
        thresholdGreenPercent = 0.0,
        thresholdYellowPercent = 0.0,
        observedValue = null,
        rationale = "",
        threshold = "",
    )
    else -> error("Unknown ruleId '$id' in typedRuleSignal test helper.")
}
