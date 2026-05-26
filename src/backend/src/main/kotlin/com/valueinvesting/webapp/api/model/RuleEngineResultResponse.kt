package com.valueinvesting.webapp.api.model

import io.swagger.v3.oas.annotations.media.Schema
import com.valueinvesting.webapp.contextflags.ContextFlags
import com.valueinvesting.webapp.ruleengine.RuleSignal
import com.valueinvesting.webapp.ruleengine.Signal
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import java.time.Instant

// EP-013 (US-056 + US-057): `contextFlags` è una sezione opzionale di advisory
// Mr. Market signals, DISTINTA dai 13 `signals` del Rule Engine. Aggiunta in
// fondo per non rompere la deserializzazione di client esistenti (additive
// non-breaking change). Default null mantiene la backward-compatibility per
// risposte cached/persistite prima di EP-013.
@Schema(name = "RuleEngineResult")
data class RuleEngineResultResponse(
    val ticker: String,
    val evaluatedAt: Instant,
    val signals: List<RuleSignal>,
    val grahamNumber: Double?,
    val dcfIntrinsicValue: Double?,
    val dcfMethod: DcfMethod?,
    val dcfMethodSource: DcfMethodSource,
    val mosSignal: Signal,
    val currentPriceAtEval: Double?,
    val dataSnapshotAt: Instant,
    val isStale: Boolean,
    val contextFlags: ContextFlags? = null,
)
