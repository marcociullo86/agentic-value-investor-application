package com.valueinvesting.webapp.backtest

import com.github.benmanes.caffeine.cache.Cache
import com.valueinvesting.webapp.api.model.BacktestCaveats
import com.valueinvesting.webapp.api.model.BacktestResponse
import com.valueinvesting.webapp.api.model.BacktestStatus
import com.valueinvesting.webapp.api.model.BacktestWindow
import com.valueinvesting.webapp.fmp.FmpAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

// BacktestService — orchestratore del backtest per-ticker (EP-024 / US-105 / TSK-348).
//
// Responsabilita':
//   1) Fetch dati FMP storici sulla finestra di lookback (cache-aside FMP 24h
//      applicata a monte dal ResilientFmpAdapter).
//   2) Caching del risultato per (ticker, years, horizonMonths) — Caffeine,
//      TTL 24h, allineato alla policy ADR-030 §1. `equity` ESCLUSA dalla
//      chiave (non persistita).
//   3) Delega a BacktestEngine (TSK-346) per la ricostruzione point-in-time +
//      a BacktestRoundTripSimulator (TSK-347) per round-trip + metriche.
//   4) Composizione del BacktestResponse + caveats SEMPRE presenti.
//
// `equity` MAI persistita server-side (vincolo US-105). In questo motore non e'
// usata per nessun calcolo decisionale — il round-trip e' percentuale, non
// nominale. Esposta solo come metadato del DTO per il FE che voglia derivare
// il dollar amount lato client.
//
// Niente call LLM. Niente scritture DB. Determinismo: stesso input + stesso
// snapshot FMP → stesso output.
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-348.md §"Controller + DTO", §"Caching"]
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §1]
@Service
class BacktestService(
    private val fmpAdapter: FmpAdapter,
    private val backtestEngine: BacktestEngine,
    private val backtestRoundTripSimulator: BacktestRoundTripSimulator,
    private val backtestCache: Cache<BacktestCacheKey, BacktestResponse>,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Esegue il backtest per il ticker con i parametri richiesti.
     *
     * @param ticker ticker uppercased internamente.
     * @param years finestra di lookback (anni). Default 5; clamped a [1..20].
     * @param horizonMonths orizzonte massimo di holding. Default 6; valori
     *                     ammessi 1/3/6/12.
     */
    fun backtest(
        ticker: String,
        years: Int = DEFAULT_YEARS,
        horizonMonths: Int = DEFAULT_HORIZON_MONTHS,
    ): BacktestResponse {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        require(years in BacktestEngine.MIN_YEARS..BacktestEngine.MAX_YEARS) {
            "years must be in [${BacktestEngine.MIN_YEARS}..${BacktestEngine.MAX_YEARS}], got $years"
        }
        require(horizonMonths in BacktestEngine.ALLOWED_HORIZONS) {
            "horizonMonths must be one of ${BacktestEngine.ALLOWED_HORIZONS}, got $horizonMonths"
        }

        val t = ticker.uppercase()
        val key = BacktestCacheKey(ticker = t, years = years, horizonMonths = horizonMonths)

        backtestCache.getIfPresent(key)?.let { cached ->
            log.debug("Backtest cache HIT key={}", key)
            return cached
        }
        log.debug("Backtest cache MISS key={}", key)

        // -- Fetch finestra storica FMP -----------------------------------------
        val endDate = LocalDate.now(clock)
        val bundle = fetchBundle(t, years, horizonMonths)

        // -- Ricostruzione point-in-time ----------------------------------------
        val reconstruct = backtestEngine.reconstruct(
            bundle = bundle,
            years = years,
            horizonMonths = horizonMonths,
            endDate = endDate,
        )

        if (reconstruct.insufficientHistory) {
            val response = BacktestResponse(
                ticker = t,
                evaluatedAt = clock.instant(),
                status = BacktestStatus.INSUFFICIENT_HISTORY,
                insufficientHistoryReason = reconstruct.insufficientHistoryReason
                    ?: "Storico insufficiente per il backtest richiesto.",
                window = null,
                strategies = null,
                timingEdge = null,
                trades = null,
                caveats = BacktestCaveats(),
            )
            backtestCache.put(key, response)
            return response
        }

        // -- Simulazione round-trip + metriche ----------------------------------
        val sim = backtestRoundTripSimulator.simulate(
            snapshots = reconstruct.snapshots,
            fullEodSeries = bundle.eodPrices,
            horizonMonths = horizonMonths,
            effectiveFrom = reconstruct.effectiveFrom,
            effectiveTo = reconstruct.effectiveTo,
        )

        val response = BacktestResponse(
            ticker = t,
            evaluatedAt = clock.instant(),
            status = BacktestStatus.OK,
            insufficientHistoryReason = null,
            window = BacktestWindow(
                fromDate = reconstruct.effectiveFrom,
                toDate = reconstruct.effectiveTo,
                years = years,
                horizonMonths = horizonMonths,
            ),
            strategies = sim.strategies,
            timingEdge = sim.timingEdge,
            trades = sim.trades,
            caveats = BacktestCaveats(),
        )
        backtestCache.put(key, response)
        return response
    }

    /**
     * Fetch della finestra storica FMP per il ticker. Tutte le chiamate sono
     * cache-aside FMP 24h (`FmpCacheService` upstream + Resilience4j tramite
     * ResilientFmpAdapter). Errori downstream degradano a lista vuota (stile
     * TechnicalAnalysisService.fetchOrEmpty): la pipeline produce comunque un
     * `INSUFFICIENT_HISTORY` controllato anziche' 500.
     *
     * `limit` per i 4 financial statements: `years * 4` quarterlies + buffer.
     * FMP `/stable` ritorna annuali di default; per il backtest ci basta che
     * la lista copra interamente la finestra. limit = max(20, years*4) e' un
     * cap ragionevole — gli statement non usati vengono filtrati a `t`.
     */
    private fun fetchBundle(ticker: String, years: Int, horizonMonths: Int): BacktestEngine.FmpHistoricalBundle {
        val statementLimit = maxOf(20, years * 4 + 4)
        val eodDays = (years + 1) * 365 + horizonMonths * 31 + 200 // +200 = buffer SMA200 prima dello start
        val income = fetchOrEmpty(ticker, "income") { fmpAdapter.getIncomeStatement(ticker, statementLimit) }
        val balance = fetchOrEmpty(ticker, "balance") { fmpAdapter.getBalanceSheet(ticker, statementLimit) }
        val cashFlow = fetchOrEmpty(ticker, "cashflow") { fmpAdapter.getCashFlow(ticker, statementLimit) }
        val keyMetrics = fetchOrEmpty(ticker, "keymetrics") { fmpAdapter.getKeyMetrics(ticker, statementLimit) }
        val dividends = fetchOrEmpty(ticker, "dividends") { fmpAdapter.getDividendHistory(ticker) }
        val eodPrices = fetchOrEmpty(ticker, "eod") { fmpAdapter.getHistoricalEodPrices(ticker, days = eodDays) }
        val sma50 = fetchOrEmpty(ticker, "sma50") { fmpAdapter.getTechnicalIndicator(ticker, "sma", periodLength = 50) }
        val sma200 = fetchOrEmpty(ticker, "sma200") { fmpAdapter.getTechnicalIndicator(ticker, "sma", periodLength = 200) }
        val rsi = fetchOrEmpty(ticker, "rsi") { fmpAdapter.getTechnicalIndicator(ticker, "rsi", periodLength = 14) }
        val macdDaily = fetchOrEmpty(ticker, "macd-daily") { fmpAdapter.getMacd(ticker, timeframe = "1day") }
        val macdWeekly = fetchOrEmpty(ticker, "macd-weekly") { fmpAdapter.getMacd(ticker, timeframe = "1week") }
        val atr = fetchOrEmpty(ticker, "atr") { fmpAdapter.getAtr(ticker, periodLength = 14) }

        return BacktestEngine.FmpHistoricalBundle(
            ticker = ticker,
            income = income,
            balance = balance,
            cashFlow = cashFlow,
            keyMetrics = keyMetrics,
            dividends = dividends,
            eodPrices = eodPrices,
            sma50 = sma50,
            sma200 = sma200,
            rsi = rsi,
            macdDaily = macdDaily,
            macdWeekly = macdWeekly,
            atr = atr,
        )
    }

    private fun <T> fetchOrEmpty(ticker: String, label: String, block: () -> List<T>): List<T> =
        runCatching(block).getOrElse { ex ->
            log.warn(
                "Backtest pipeline: {} fetch failed for ticker={} — degrading to empty: {}",
                label, ticker, ex.message,
            )
            emptyList()
        }

    companion object {
        const val DEFAULT_YEARS: Int = 5
        const val DEFAULT_HORIZON_MONTHS: Int = 6
    }
}
