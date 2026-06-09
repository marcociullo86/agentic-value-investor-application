package com.valueinvesting.webapp.backtest

import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord
import com.valueinvesting.webapp.technicalanalysis.EntryTimingAdvisor
import com.valueinvesting.webapp.technicalanalysis.PriceLevel
import com.valueinvesting.webapp.technicalanalysis.StopPlacementAdvisor
import com.valueinvesting.webapp.technicalanalysis.SupportResistanceFinder
import com.valueinvesting.webapp.technicalanalysis.TrendClassification
import com.valueinvesting.webapp.technicalanalysis.TrendClassifier
import org.springframework.stereotype.Component
import java.time.LocalDate

// AsOfDateTechnicalContext — riusa la pipeline TA (US-098/099/100) in modalita'
// "as-of date" (EP-024 / US-105 / TSK-346).
//
// Dati storici scaricati una sola volta (la finestra massima copre tutti i `t`
// del campionamento). Per ogni `t`, questa classe filtra in-memory tutte le
// serie a `date ≤ t` PRIMA di calcolare gli indicatori — garantendo che il
// verdetto TA a `t` non veda mai EOD futuri.
//
// Pure-function: niente I/O, niente persistenza, niente LLM. Riusa
// EntryTimingAdvisor, StopPlacementAdvisor, TrendClassifier, SupportResistanceFinder
// di EP-024 Fase 1.
//
// Differenza dalla pipeline live (TechnicalAnalysisService.analyze):
//   - I dati FMP NON sono scaricati qui: vengono passati una sola volta dal
//     BacktestEngine e poi sliced per ogni `t` (efficienza: 1 fetch full series
//     vs N fetch puntuali, e anti look-ahead per costruzione).
//   - Il `currentPrice` e' il close EOD a `t` (lex-ordered lookup), NON il
//     profile FMP live.
//   - Niente AnalyzeTickerService — il backtest assembla direttamente il
//     contesto VI separatamente (point-in-time fundamentals).
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-346.md §"Indicatori TA as-of-date"]
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md]
@Component
class AsOfDateTechnicalContext(
    private val entryTimingAdvisor: EntryTimingAdvisor,
    private val stopPlacementAdvisor: StopPlacementAdvisor,
) {

    /**
     * Snapshot TA a `t` — risultato del calcolo as-of-date. Tutte le quantita'
     * sono nullable per gestire `INSUFFICIENT_HISTORY` parziale (es. SMA200
     * non calcolabile se ci sono < 200 EOD a `t`).
     */
    data class TaSnapshot(
        val asOf: LocalDate,
        val currentPrice: Double?,
        val sma50: Double?,
        val sma200: Double?,
        val rsi14: Double?,
        val macdDaily: Double?,
        val macdWeekly: Double?,
        val atr14: Double?,
        val trend: TrendClassification,
        val nearestSupport: PriceLevel?,
        val entryTimingVerdict: EntryTimingVerdict,
        val stopPrice: Double?,
        val stopDistance: Double?,
        val stopDistancePct: Double?,
        /** Numero di sedute EOD ≤ asOf usate (per la soglia MIN_HISTORY_DAYS). */
        val historyDaysAvailable: Int,
    )

    /**
     * Calcola lo snapshot TA "come sarebbe stato a `asOf`" usando solo dati con
     * `date ≤ asOf`. Tutte le serie devono essere pre-fetchate (finestra
     * massima) e passate al chiamante una sola volta — questo metodo le filtra
     * in-memory a `asOf` ad ogni invocazione.
     *
     * @param asOf data di valutazione (point-in-time cut-off).
     * @param fullEodSeries serie EOD completa (qualsiasi ordine — viene riordinata).
     * @param fullSma50 serie SMA50 completa.
     * @param fullSma200 serie SMA200 completa.
     * @param fullRsi serie RSI14 completa.
     * @param fullMacdDaily serie MACD daily completa.
     * @param fullMacdWeekly serie MACD weekly completa.
     * @param fullAtr serie ATR14 completa.
     */
    fun snapshotAt(
        asOf: LocalDate,
        fullEodSeries: List<EodPriceRecord>,
        fullSma50: List<TechnicalIndicatorRecord>,
        fullSma200: List<TechnicalIndicatorRecord>,
        fullRsi: List<TechnicalIndicatorRecord>,
        fullMacdDaily: List<TechnicalIndicatorRecord>,
        fullMacdWeekly: List<TechnicalIndicatorRecord>,
        fullAtr: List<TechnicalIndicatorRecord>,
    ): TaSnapshot {
        // 1) Filtra EOD ≤ asOf, ordinato cronologicamente asc.
        val eodUpToT = fullEodSeries
            .filter { it.date != null && !it.date.isAfter(asOf) }
            .sortedBy { it.date }

        val historyDays = eodUpToT.size
        val currentPrice = eodUpToT.lastOrNull()?.close

        // 2) Filtra le serie indicator ≤ asOf (lex ordering ISO date == cronologico).
        val asOfStr = asOf.toString()
        val sma50AsOf = filterIndicatorUpTo(fullSma50, asOfStr)
        val sma200AsOf = filterIndicatorUpTo(fullSma200, asOfStr)
        val rsiAsOf = filterIndicatorUpTo(fullRsi, asOfStr)
        val macdDailyAsOf = filterIndicatorUpTo(fullMacdDaily, asOfStr)
        val macdWeeklyAsOf = filterIndicatorUpTo(fullMacdWeekly, asOfStr)
        val atrAsOf = filterIndicatorUpTo(fullAtr, asOfStr)

        val sma50Latest = latestValue(sma50AsOf)
        val sma200Latest = latestValue(sma200AsOf)
        val rsi14 = latestValue(rsiAsOf)
        val macdDaily = latestValue(macdDailyAsOf)
        val macdWeekly = latestValue(macdWeeklyAsOf)
        val atr14 = latestValue(atrAsOf)

        // 3) Trend deterministico riusando TrendClassifier (US-098).
        val sma200Series = sma200AsOf
            .filter { it.value != null && it.date != null }
            .sortedBy { it.date }
            .mapNotNull { it.value }
        val trend = TrendClassifier.classify(
            price = currentPrice,
            sma50 = sma50Latest,
            sma200 = sma200Latest,
            sma200Series = sma200Series,
            historyDays = historyDays,
        )

        // 4) Support/resistance sulla finestra 12m fino a `asOf`.
        val closes12m = eodUpToT
            .mapNotNull { it.close }
            .takeLast(EOD_12M_DAYS)
        val nearestSupport: PriceLevel? = if (currentPrice != null && currentPrice > 0.0) {
            val levels = SupportResistanceFinder.find(currentPrice, closes12m)
            levels.support.firstOrNull()
        } else null

        // 5) EntryTimingAdvisor (US-099) — pure-function.
        val entry = entryTimingAdvisor.advise(
            EntryTimingAdvisor.Input(
                trend = trend,
                rsi14 = rsi14,
                macdDaily = macdDaily,
                macdWeekly = macdWeekly,
                currentPrice = currentPrice,
                nearestSupport = nearestSupport?.price,
            ),
        )

        // 6) StopPlacementAdvisor (US-100) — solo se price > 0 (altrimenti non
        // serve nel backtest, lo simuliamo NOT_CALCULABLE).
        val stop = if (currentPrice != null && currentPrice > 0.0) {
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

        return TaSnapshot(
            asOf = asOf,
            currentPrice = currentPrice,
            sma50 = sma50Latest,
            sma200 = sma200Latest,
            rsi14 = rsi14,
            macdDaily = macdDaily,
            macdWeekly = macdWeekly,
            atr14 = atr14,
            trend = trend,
            nearestSupport = nearestSupport,
            entryTimingVerdict = entry.verdict,
            stopPrice = stop?.stopPrice,
            stopDistance = stop?.stopDistance,
            stopDistancePct = stop?.stopDistancePct,
            historyDaysAvailable = historyDays,
        )
    }

    /**
     * Lookup deterministico del close EOD a una data specifica (o al primo EOD
     * trading day ≥ requested se la richiesta cade in weekend/holiday).
     *
     * @return coppia (data effettiva, close) oppure null se la data richiesta
     *         supera la fine del periodo coperto.
     */
    fun closeOnOrAfter(
        fullEodSeries: List<EodPriceRecord>,
        target: LocalDate,
    ): Pair<LocalDate, Double>? {
        return fullEodSeries
            .asSequence()
            .filter { it.date != null && it.close != null && !it.date.isBefore(target) }
            .sortedBy { it.date }
            .firstOrNull()
            ?.let { it.date!! to it.close!! }
    }

    /**
     * Lookup deterministico del close EOD a una data specifica (o al primo
     * trading day ≤ requested). Usato per la chiusura forzata al `HORIZON`:
     * vogliamo il close a (o appena prima di) un orizzonte fisso.
     */
    fun closeOnOrBefore(
        fullEodSeries: List<EodPriceRecord>,
        target: LocalDate,
    ): Pair<LocalDate, Double>? {
        return fullEodSeries
            .asSequence()
            .filter { it.date != null && it.close != null && !it.date.isAfter(target) }
            .sortedByDescending { it.date }
            .firstOrNull()
            ?.let { it.date!! to it.close!! }
    }

    private fun filterIndicatorUpTo(
        records: List<TechnicalIndicatorRecord>,
        asOfPrefix: String,
    ): List<TechnicalIndicatorRecord> =
        records.filter {
            val d = it.date
            // `date` puo' essere `yyyy-MM-dd` (FMP RSI/SMA) o `yyyy-MM-dd HH:mm:ss`
            // (FMP MACD intraday): in entrambi i casi prefix-compare lex con
            // `yyyy-MM-dd` di `asOf` mantiene l'ordering cronologico.
            d != null && d.length >= 10 && d.substring(0, 10) <= asOfPrefix
        }

    private fun latestValue(records: List<TechnicalIndicatorRecord>): Double? =
        records.maxByOrNull { it.date ?: "" }?.value

    companion object {
        // Coerente con TechnicalAnalysisService.EOD_12M_DAYS.
        const val EOD_12M_DAYS: Int = 252
    }
}
