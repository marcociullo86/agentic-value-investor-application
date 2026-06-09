package com.valueinvesting.webapp.technicalanalysis

import com.valueinvesting.webapp.api.model.LevelsBlock
import com.valueinvesting.webapp.api.model.MomentumBlock
import com.valueinvesting.webapp.api.model.PriceContextBlock
import com.valueinvesting.webapp.api.model.PriceLevelDto
import com.valueinvesting.webapp.api.model.TechnicalAnalysisResponse
import com.valueinvesting.webapp.api.model.TrendBlock
import com.valueinvesting.webapp.api.model.VolatilityBlock
import com.valueinvesting.webapp.api.model.VolumeBlock
import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord
import com.valueinvesting.webapp.service.AnalyzeTickerService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

// TechnicalAnalysisService — orchestratore del payload Technical Analysis
// (EP-024, US-098..US-100, TSK-325/-326/-328/-330).
//
// Responsabilita':
//   - Fetch indicatori (SMA50/200, RSI14, MACD daily+weekly, ATR14, OBV) +
//     historical EOD via FmpAdapter (cache-aside FMP 24h applicata a monte
//     dalla chain Resilience4j del ResilientFmpAdapter; nessuna nuova tabella).
//   - Calcolo deterministico in pure-function (TrendClassifier, PriceContextBuilder,
//     SupportResistanceFinder).
//   - Composizione dei 6 blocchi indicatori + 3 advisor (EntryTimingAdvisor +
//     StopPlacementAdvisor) nel `TechnicalAnalysisResponse`.
//
// Niente LLM (deterministico). Niente regressione su /api/analysis/{ticker}
// (zero overlap: le nuove chiamate FMP sono invocate solo da questo servizio,
// che e' raggiungibile esclusivamente via TechnicalAnalysisController su
// `/technical`).
//
// Kdoc cita [[ta-entry-timing-stock-detail]] e [[ta-stop-placement-position-sizing]].
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/TSK-325.md]
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/TSK-326.md]
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md]
// [^src: wiki/syntheses/ta-stop-placement-position-sizing.md]
@Service
class TechnicalAnalysisService(
    private val fmpAdapter: FmpAdapter,
    private val entryTimingAdvisor: EntryTimingAdvisor,
    private val stopPlacementAdvisor: StopPlacementAdvisor,
    private val analyzeTickerService: AnalyzeTickerService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Calcola il payload TA completo per il ticker.
     *
     * @param ticker  ticker uppercased internamente.
     * @param equity  capitale per il position sizing 2%/6% Rule (US-100).
     *                Default DEFAULT_EQUITY_USD (50_000). Mai persistito.
     */
    fun analyze(ticker: String, equity: Double = DEFAULT_EQUITY_USD): TechnicalAnalysisResponse {
        require(ticker.isNotBlank()) { "ticker must not be blank" }
        require(equity > 0.0) { "equity must be > 0" }
        val t = ticker.uppercase()

        // --- Fetch dati FMP --------------------------------------------------
        val eodPrices = fetchOrEmpty(t, "historical-price-eod") {
            fmpAdapter.getHistoricalEodPrices(t, days = EOD_LOOKBACK_CALENDAR_DAYS)
        }
        val sma50Records = fetchOrEmpty(t, "sma50") {
            fmpAdapter.getTechnicalIndicator(t, "sma", periodLength = 50)
        }
        val sma200Records = fetchOrEmpty(t, "sma200") {
            fmpAdapter.getTechnicalIndicator(t, "sma", periodLength = 200)
        }
        val rsiRecords = fetchOrEmpty(t, "rsi") {
            fmpAdapter.getTechnicalIndicator(t, "rsi", periodLength = 14)
        }
        val macdDailyRecords = fetchOrEmpty(t, "macd-daily") {
            fmpAdapter.getMacd(t, timeframe = "1day")
        }
        val macdWeeklyRecords = fetchOrEmpty(t, "macd-weekly") {
            fmpAdapter.getMacd(t, timeframe = "1week")
        }
        val atrRecords = fetchOrEmpty(t, "atr14") {
            fmpAdapter.getAtr(t, periodLength = 14)
        }
        val obvRecords = fetchOrEmpty(t, "obv") {
            fmpAdapter.getObv(t)
        }

        // --- Prezzo corrente: dall'AnalyzeTickerService (riusa la pipeline VI -
        // include profile + DCF). Cosi' il payload TA "vede" lo stesso prezzo
        // del tab Analisi Base e abbiamo gia' il dcfIntrinsicValue per il
        // rewardRiskRatio (US-100). ----------------------------------------
        val ruleResult = runCatching { analyzeTickerService.analyze(t) }
            .onFailure { log.warn("AnalyzeTickerService.analyze({}) failed in TA pipeline: {}", t, it.message) }
            .getOrNull()
        val currentPrice = ruleResult?.currentPriceAtEval
        val dcfIntrinsicValue = ruleResult?.dcfIntrinsicValue

        // --- Calcoli ---------------------------------------------------------
        val sma200Latest = latestValue(sma200Records)
        val sma50Latest = latestValue(sma50Records)
        val sma200Series = sma200Records
            .filter { it.value != null && it.date != null }
            .sortedBy { it.date }
            .mapNotNull { it.value }

        // historyDays = sedute EOD effettive disponibili.
        val historyDays = eodPrices.count { it.close != null }
        val trendClass = TrendClassifier.classify(
            price = currentPrice,
            sma50 = sma50Latest,
            sma200 = sma200Latest,
            sma200Series = sma200Series,
            historyDays = historyDays,
        )
        val slope = TrendClassifier.linearRegressionSlope(
            sma200Series.takeLast(TrendClassifier.SLOPE_WINDOW),
        )

        val rsi14 = latestValue(rsiRecords)
        val macdDaily = latestValue(macdDailyRecords)
        val macdWeekly = latestValue(macdWeeklyRecords)
        val atr14 = latestValue(atrRecords)
        val obv = latestValue(obvRecords)

        val priceContextRaw = PriceContextBuilder.build(currentPrice, eodPrices)
        val avgVolume20d = averageVolume(eodPrices, 20)

        val sortedCloses = eodPrices
            .filter { it.close != null && it.date != null }
            .sortedBy { it.date }
            .mapNotNull { it.close }
        val window12m = sortedCloses.takeLast(EOD_12M_DAYS)
        val srLevels = if (currentPrice != null && currentPrice > 0.0) {
            SupportResistanceFinder.find(currentPrice, window12m)
        } else {
            SupportResistanceLevels(emptyList(), emptyList())
        }

        val nearestSupport = srLevels.support.firstOrNull()

        // --- Advisor (US-099 + US-100) --------------------------------------
        val entryAdvisor = entryTimingAdvisor.advise(
            EntryTimingAdvisor.Input(
                trend = trendClass,
                rsi14 = rsi14,
                macdDaily = macdDaily,
                macdWeekly = macdWeekly,
                currentPrice = currentPrice,
                nearestSupport = nearestSupport?.price,
            ),
        )

        val stopSuggestion = if (currentPrice != null && currentPrice > 0.0) {
            stopPlacementAdvisor.suggestStop(
                StopPlacementAdvisor.StopInput(
                    currentPrice = currentPrice,
                    nearestSupport = nearestSupport?.price,
                    nearestSupportLabel = nearestSupport?.type?.name,
                    sma200 = sma200Latest,
                    atr14 = atr14,
                ),
            )
        } else null

        val positionSizing = if (currentPrice != null && currentPrice > 0.0 && stopSuggestion != null) {
            stopPlacementAdvisor.computePositionSizing(equity, currentPrice, stopSuggestion)
        } else null

        val rewardRisk = if (currentPrice != null && currentPrice > 0.0 && stopSuggestion != null) {
            stopPlacementAdvisor.computeRewardRisk(currentPrice, dcfIntrinsicValue, stopSuggestion)
        } else null

        // --- Composizione response ------------------------------------------
        val trendReduced = trendClass == TrendClassification.INDETERMINATE
        val priceContextBlock = PriceContextBlock(
            currentPrice = priceContextRaw.currentPrice,
            high52w = priceContextRaw.high52w,
            low52w = priceContextRaw.low52w,
            drawdownFrom52wHigh = priceContextRaw.drawdownFrom52wHigh,
            confidenceReduced = priceContextRaw.confidenceReduced,
        )

        return TechnicalAnalysisResponse(
            ticker = t,
            evaluatedAt = Instant.now(),
            trend = TrendBlock(
                sma50 = sma50Latest,
                sma200 = sma200Latest,
                classification = trendClass,
                sma200SlopePerDay = slope,
                confidenceReduced = trendReduced,
            ),
            momentum = MomentumBlock(
                rsi14 = rsi14,
                macdDaily = macdDaily,
                macdWeekly = macdWeekly,
                confidenceReduced = trendReduced,
            ),
            volatility = VolatilityBlock(
                atr14 = atr14,
                confidenceReduced = atr14 == null,
            ),
            volume = VolumeBlock(
                obv = obv,
                avgVolume20d = avgVolume20d,
                confidenceReduced = obv == null || avgVolume20d == null,
            ),
            levels = LevelsBlock(
                support = srLevels.support.map { it.toDto() },
                resistance = srLevels.resistance.map { it.toDto() },
                confidenceReduced = trendReduced || window12m.size < EOD_12M_DAYS,
            ),
            priceContext = priceContextBlock,
            entryTimingAdvisor = entryAdvisor,
            stopSuggestion = stopSuggestion,
            positionSizing = positionSizing,
            rewardRiskRatio = rewardRisk,
        )
    }

    private fun PriceLevel.toDto(): PriceLevelDto =
        PriceLevelDto(price = price, type = type, confidence = confidence)

    /**
     * Ritorna il `value` del record FMP piu' recente (lex ordering ISO date ==
     * cronologico). Null se la lista e' vuota o il record latest non ha `value`.
     */
    private fun latestValue(records: List<TechnicalIndicatorRecord>): Double? =
        records.maxByOrNull { it.date ?: "" }?.value

    private fun averageVolume(records: List<EodPriceRecord>, days: Int): Double? {
        val volumes = records
            .filter { it.volume != null && it.date != null }
            .sortedBy { it.date }
            .takeLast(days)
            .mapNotNull { it.volume }
        if (volumes.isEmpty()) return null
        return volumes.average()
    }

    /**
     * Esegue la fetch e degrada a lista vuota su qualsiasi eccezione downstream
     * (FmpUnavailableException da Resilience4j circuit open / 5xx / 429, ecc.).
     * Pattern identico a LongTermTrendEvaluator/RsiContextEvaluator (EP-013).
     * Il caller resta robusto: i blocchi degradati portano `confidenceReduced`.
     */
    private fun <T> fetchOrEmpty(ticker: String, label: String, block: () -> List<T>): List<T> =
        runCatching(block).getOrElse { ex ->
            log.warn("TA pipeline: {} fetch failed for ticker={} — degrading to empty: {}", label, ticker, ex.message)
            emptyList()
        }

    companion object {
        // Equity di default per il position sizing 2%/6% se l'utente non passa
        // ?equity=... (US-100 §"Input equity"). NON persistito server-side.
        const val DEFAULT_EQUITY_USD: Double = 50_000.0

        // Finestra EOD per il download historical. Coerente con PriceActionAnalyzer.
        const val EOD_LOOKBACK_CALENDAR_DAYS: Int = 400

        // Window 12m usato per la detection swing + retracement.
        const val EOD_12M_DAYS: Int = 252
    }
}
