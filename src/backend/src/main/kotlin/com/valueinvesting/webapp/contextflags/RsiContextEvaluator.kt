package com.valueinvesting.webapp.contextflags

import com.valueinvesting.webapp.fmp.FmpAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// Evaluator per il context flag RSI 14-day (EP-013 / US-056).
//
// Iniettato in AnalyzeTickerService (NON nel RuleEngineService — le rule
// restano pure e stateless). Il caller è responsabile di chiamare evaluate()
// dopo aver costruito la response e di iniettare il MrMarketRsiFlag risultante
// nella sezione `contextFlags`.
//
// Failure tolerance:
//   - Qualsiasi eccezione lanciata dall'FmpAdapter (incluso FmpUnavailableException
//     da Resilience4j circuit open / 5xx / 429) viene catturata e degradata a
//     INDETERMINATE. Il context flag NON deve mai far fallire l'analisi
//     principale (i 13 rule signal + DCF + MoS sono il cuore dell'output).
//   - Lista vuota o `value` null → INDETERMINATE.
//
// Soglie RSI standard di settore (Wilder 1978, replicate da TradingView, FMP,
// Bloomberg):
//   - rsi < 30   → OVERSOLD
//   - rsi > 70   → OVERBOUGHT
//   - else       → NEUTRAL
//
// [^src: wiki/syntheses/graham-investing-philosophy.md §Mr. Market]
// [^src: management/kanban/EP-013-mr-market-context-flags/US-056-rsi-mr-market-context-flag/TSK-165.md]
@Component
class RsiContextEvaluator(
    private val fmpAdapter: FmpAdapter,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun evaluate(ticker: String): MrMarketRsiFlag {
        val records = runCatching {
            fmpAdapter.getTechnicalIndicator(
                ticker = ticker,
                indicator = INDICATOR_RSI,
                periodLength = PERIOD_LENGTH,
                timeframe = TIMEFRAME,
            )
        }.getOrElse { ex ->
            log.warn(
                "RSI context flag fetch failed for {} — degrading to INDETERMINATE: {}",
                ticker, ex.message,
            )
            return indeterminate()
        }

        // Lex ordering yyyy-MM-dd HH:mm:ss == cronologico → maxByOrNull == latest.
        val latest = records.maxByOrNull { it.date ?: "" }
            ?: return indeterminate()
        val value = latest.value
            ?: return indeterminate(timestamp = latest.date)

        val signal = when {
            value < OVERSOLD_THRESHOLD -> MrMarketRsiSignal.OVERSOLD
            value > OVERBOUGHT_THRESHOLD -> MrMarketRsiSignal.OVERBOUGHT
            else -> MrMarketRsiSignal.NEUTRAL
        }
        return MrMarketRsiFlag(
            flag = signal,
            rsiLatest = value,
            rsiTimestamp = latest.date,
            periodLength = PERIOD_LENGTH,
            timeframe = TIMEFRAME,
        )
    }

    private fun indeterminate(timestamp: String? = null) = MrMarketRsiFlag(
        flag = MrMarketRsiSignal.INDETERMINATE,
        rsiLatest = null,
        rsiTimestamp = timestamp,
        periodLength = PERIOD_LENGTH,
        timeframe = TIMEFRAME,
    )

    private companion object {
        const val INDICATOR_RSI = "rsi"
        const val PERIOD_LENGTH = 14
        const val TIMEFRAME = "1day"
        const val OVERSOLD_THRESHOLD = 30.0
        const val OVERBOUGHT_THRESHOLD = 70.0
    }
}
