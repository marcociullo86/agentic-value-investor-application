package com.valueinvesting.webapp.api.model

import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import java.time.Instant

data class RuleEngineResultResponse(
    val ticker: String,
    val evaluatedAt: Instant,
    val signals: List<RuleSignal>,
    val grahamNumber: Double?,
    val dcfIntrinsicValue: Double?,
    val dcfMethod: DcfMethod?,
    val mosSignal: Signal,
    val currentPriceAtEval: Double?,
    val dataSnapshotAt: Instant,
    val isStale: Boolean,
)
