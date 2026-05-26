package com.valueinvesting.webapp.contextflags

import com.valueinvesting.webapp.fmp.FmpAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

// Evaluator per il context flag long-term trend (SMA200) — EP-013 / US-057.
//
// Iniettato in AnalyzeTickerService. Il `currentPrice` è passato esplicitamente
// dal caller (tipicamente `profile.value.price`) per mantenere l'evaluator
// disaccoppiato dal Rule Engine e dal FinancialDataset.
//
// Failure tolerance:
//   - currentPrice null o <= 0 → INDETERMINATE senza chiamata FMP.
//   - Qualsiasi eccezione FmpAdapter → INDETERMINATE.
//   - SMA null o <= 0 → INDETERMINATE.
//   - Ticker IPO < 200 giorni (FMP empty list) → INDETERMINATE.
//
// Soglie asimmetriche by design (i mercati salgono over time):
//   - priceVsSmaPct < -5%   → BELOW_TREND
//   - priceVsSmaPct > +20%  → ABOVE_TREND
//   - else                  → NEAR_TREND
//
// [^src: management/kanban/EP-013-mr-market-context-flags/US-057-sma200-trend-context-flag/TSK-166.md]
@Component
class LongTermTrendEvaluator(
    private val fmpAdapter: FmpAdapter,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun evaluate(ticker: String, currentPrice: Double?): LongTermTrendFlag {
        if (currentPrice == null || currentPrice <= 0.0) {
            return indeterminate(currentPrice = currentPrice)
        }

        val records = runCatching {
            fmpAdapter.getTechnicalIndicator(
                ticker = ticker,
                indicator = INDICATOR_SMA,
                periodLength = PERIOD_LENGTH,
                timeframe = TIMEFRAME,
            )
        }.getOrElse { ex ->
            log.warn(
                "Long-term trend (SMA200) fetch failed for {} — degrading to INDETERMINATE: {}",
                ticker, ex.message,
            )
            return indeterminate(currentPrice = currentPrice)
        }

        // Lex ordering yyyy-MM-dd HH:mm:ss == cronologico → maxByOrNull == latest.
        val latest = records.maxByOrNull { it.date ?: "" }
            ?: return indeterminate(currentPrice = currentPrice)
        val sma = latest.value
        if (sma == null || sma <= 0.0) {
            return indeterminate(currentPrice = currentPrice, timestamp = latest.date)
        }

        val pct = (currentPrice - sma) / sma
        val signal = when {
            pct < BELOW_TREND_THRESHOLD -> LongTermTrendSignal.BELOW_TREND
            pct > ABOVE_TREND_THRESHOLD -> LongTermTrendSignal.ABOVE_TREND
            else -> LongTermTrendSignal.NEAR_TREND
        }
        return LongTermTrendFlag(
            flag = signal,
            sma200Latest = sma,
            currentPrice = currentPrice,
            priceVsSmaPct = pct,
            smaTimestamp = latest.date,
            periodLength = PERIOD_LENGTH,
            timeframe = TIMEFRAME,
        )
    }

    private fun indeterminate(
        currentPrice: Double? = null,
        timestamp: String? = null,
    ) = LongTermTrendFlag(
        flag = LongTermTrendSignal.INDETERMINATE,
        sma200Latest = null,
        currentPrice = currentPrice,
        priceVsSmaPct = null,
        smaTimestamp = timestamp,
        periodLength = PERIOD_LENGTH,
        timeframe = TIMEFRAME,
    )

    private companion object {
        const val INDICATOR_SMA = "sma"
        const val PERIOD_LENGTH = 200
        const val TIMEFRAME = "1day"
        const val BELOW_TREND_THRESHOLD = -0.05
        const val ABOVE_TREND_THRESHOLD = 0.20
    }
}
