package com.valueinvesting.webapp.backtest

import com.valueinvesting.webapp.fmp.dto.BalanceSheetDto
import com.valueinvesting.webapp.fmp.dto.CashFlowDto
import com.valueinvesting.webapp.fmp.dto.DividendRecord
import com.valueinvesting.webapp.fmp.dto.EodPriceRecord
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import com.valueinvesting.webapp.fmp.dto.KeyMetricsDto
import com.valueinvesting.webapp.fmp.dto.TechnicalIndicatorRecord
import com.valueinvesting.webapp.ruleengine.RuleEngineService
import com.valueinvesting.webapp.ruleengine.calculators.DcfCalculator
import com.valueinvesting.webapp.ruleengine.calculators.DcfMethod
import com.valueinvesting.webapp.ruleengine.calculators.MarginOfSafetyEvaluator
import com.valueinvesting.webapp.service.FinancialDataset
import com.valueinvesting.webapp.summary.SummaryVerdict
import com.valueinvesting.webapp.summary.SummaryVerdictAggregator
import com.valueinvesting.webapp.summary.ViVerdict
import com.valueinvesting.webapp.summary.ViVerdictAggregator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

// BacktestEngine — ricostruzione point-in-time del verdetto EP-024 (US-105 / TSK-346).
//
// Responsabilita': per ogni `t` campionato nella finestra di lookback,
// ricostruire il verdetto EP-024 ("ENTER_NOW / WAIT_FOR_SETUP / AVOID")
// **come sarebbe stato a `t`**, usando solo dati noti a `t`:
//
//   1) Fondamentali filtrati per `filingDate`/`acceptedDate` ≤ t
//      (PointInTimeFinancialFilter).
//   2) Indicatori TA calcolati solo su EOD ≤ t (AsOfDateTechnicalContext).
//   3) Verdetto (VI, TA) → SummaryVerdict via SummaryVerdictAggregator (mapping
//      deterministico US-103 / ADR-030, gate VI hardcoded).
//
// Riutilizzo critico (US-105 §"Onesta di scope"):
//   - Rule engine VI (RuleEngineService + DCF + MoS) → invocato direttamente
//     sul dataset filtrato per evitare le side-effect di AnalyzeTickerService
//     (persistenza rule_engine_result, lettura override DCF user-scoped).
//   - EntryTimingAdvisor + StopPlacementAdvisor (US-099/US-100) → riusati in
//     modalita' as-of-date.
//   - SummaryVerdictAggregator + ViVerdictAggregator (US-103) → riusati 1:1.
//
// Determinismo: stesso input → stesso output. Tutti i timestamp sono derivati
// da `asOf` (LocalDate), nessun `Instant.now()` nel calcolo del verdetto.
//
// Kdoc cita esplicitamente [[ta-vs-vi-decision-layer]] e
// [[value-investing-design-lens]].
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-346.md §"Campionamento", §"Gestione INSUFFICIENT_HISTORY"]
// [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"La regola sequenziale"]
// [^src: memory/semantic/value-investing-design-lens.md]
@Service
class BacktestEngine(
    private val pointInTimeFilter: PointInTimeFinancialFilter,
    private val asOfDateTechnicalContext: AsOfDateTechnicalContext,
    private val ruleEngineService: RuleEngineService,
    private val dcfCalculator: DcfCalculator,
    private val marginOfSafetyEvaluator: MarginOfSafetyEvaluator,
    private val viVerdictAggregator: ViVerdictAggregator,
    private val summaryVerdictAggregator: SummaryVerdictAggregator,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Dati FMP pre-fetchati che coprono l'intera finestra di lookback. Sono
     * passati una sola volta al motore e poi filtrati in-memory a ogni `t`.
     */
    data class FmpHistoricalBundle(
        val ticker: String,
        val income: List<IncomeStatementDto>,
        val balance: List<BalanceSheetDto>,
        val cashFlow: List<CashFlowDto>,
        val keyMetrics: List<KeyMetricsDto>,
        val dividends: List<DividendRecord>,
        val eodPrices: List<EodPriceRecord>,
        val sma50: List<TechnicalIndicatorRecord>,
        val sma200: List<TechnicalIndicatorRecord>,
        val rsi: List<TechnicalIndicatorRecord>,
        val macdDaily: List<TechnicalIndicatorRecord>,
        val macdWeekly: List<TechnicalIndicatorRecord>,
        val atr: List<TechnicalIndicatorRecord>,
    )

    /**
     * Snapshot del verdetto a una specifica data `t`. Contiene sia il verdetto
     * Summary EP-024 sia il sub-set di metriche necessario al round-trip
     * simulator (TSK-347): `dcfIntrinsicValue`, `stopPrice`, `entryPrice`.
     */
    data class VerdictSnapshot(
        val asOf: LocalDate,
        val tradingPrice: Double?,
        val summaryVerdict: SummaryVerdict,
        val viVerdict: ViVerdict,
        val viOnlyEnter: Boolean,
        val dcfIntrinsicValue: Double?,
        val stopPrice: Double?,
        val stopDistance: Double?,
        val stopDistancePct: Double?,
        val taSnapshot: AsOfDateTechnicalContext.TaSnapshot,
    )

    /**
     * Risultato della ricostruzione: lista snapshot mensili nella finestra +
     * info diagnostiche.
     */
    data class ReconstructResult(
        val snapshots: List<VerdictSnapshot>,
        val effectiveFrom: LocalDate,
        val effectiveTo: LocalDate,
        val insufficientHistory: Boolean,
        val insufficientHistoryReason: String?,
    )

    /**
     * Ricostruisce il verdetto EP-024 a intervalli mensili sulla finestra
     * `[now - years, now]`. Il primo giorno utile del mese (= primo EOD trading
     * day disponibile) e' usato come `t`.
     *
     * INSUFFICIENT_HISTORY: se lo storico EOD disponibile e' inferiore a
     * `horizonMonths + 1 mesi` di calendario, il motore non puo' chiudere
     * neanche un round-trip → ritorna `insufficientHistory = true` senza
     * snapshot.
     */
    fun reconstruct(
        bundle: FmpHistoricalBundle,
        years: Int,
        horizonMonths: Int,
        endDate: LocalDate,
    ): ReconstructResult {
        require(years in MIN_YEARS..MAX_YEARS) {
            "years must be in [$MIN_YEARS..$MAX_YEARS], got $years"
        }
        require(horizonMonths in ALLOWED_HORIZONS) {
            "horizonMonths must be one of $ALLOWED_HORIZONS, got $horizonMonths"
        }

        val sortedEod = bundle.eodPrices
            .filter { it.date != null && it.close != null }
            .sortedBy { it.date }
        if (sortedEod.isEmpty()) {
            return ReconstructResult(
                snapshots = emptyList(),
                effectiveFrom = endDate,
                effectiveTo = endDate,
                insufficientHistory = true,
                insufficientHistoryReason = "Nessuna serie EOD disponibile da FMP per il ticker.",
            )
        }

        val firstAvailable: LocalDate = sortedEod.first().date!!
        val lastAvailable: LocalDate = sortedEod.last().date!!.coerceAtMost(endDate)

        // Finestra richiesta (clamped al primo EOD effettivamente disponibile).
        val windowStart: LocalDate = endDate.minusYears(years.toLong())
        val effectiveStart: LocalDate = maxOf(windowStart, firstAvailable)
        val effectiveEnd: LocalDate = lastAvailable

        // Se l'effective coverage e' inferiore a `horizonMonths + 1 mesi`,
        // non possiamo simulare neanche un round-trip completo (un trade
        // aperto all'inizio deve poter raggiungere almeno l'orizzonte) →
        // INSUFFICIENT_HISTORY.
        val effectiveMonths = monthsBetween(effectiveStart, effectiveEnd)
        if (effectiveMonths < horizonMonths + 1) {
            return ReconstructResult(
                snapshots = emptyList(),
                effectiveFrom = effectiveStart,
                effectiveTo = effectiveEnd,
                insufficientHistory = true,
                insufficientHistoryReason = "Storico EOD insufficiente: $effectiveMonths mesi disponibili, richiesti almeno ${horizonMonths + 1} mesi (horizonMonths + 1).",
            )
        }

        // Campionamento mensile: il primo trading day del mese (≥ primo del mese).
        // Limitiamo le entrate a effectiveEnd - horizonMonths cosi' OGNI segnale
        // ha la possibilita' di completare un round-trip nella finestra.
        val lastEntryDate: LocalDate = effectiveEnd.minusMonths(horizonMonths.toLong())
        val samples = mutableListOf<LocalDate>()
        var cursor = firstDayOfMonth(effectiveStart)
        while (!cursor.isAfter(lastEntryDate)) {
            samples += cursor
            cursor = cursor.plusMonths(1)
        }

        if (samples.isEmpty()) {
            return ReconstructResult(
                snapshots = emptyList(),
                effectiveFrom = effectiveStart,
                effectiveTo = effectiveEnd,
                insufficientHistory = true,
                insufficientHistoryReason = "Nessun punto di campionamento valido nella finestra (windowStart=$effectiveStart, lastEntryDate=$lastEntryDate).",
            )
        }

        // Dataset "raw" — currentPrice + dataSnapshotAt verranno overridden a
        // ogni filter() in funzione di `t`.
        val baseDataset = FinancialDataset(
            ticker = bundle.ticker.uppercase(),
            income = bundle.income,
            balance = bundle.balance,
            cashFlow = bundle.cashFlow,
            keyMetrics = bundle.keyMetrics,
            dataSnapshotAt = PointInTimeFinancialFilter.endOfDayInstant(effectiveEnd),
            isStale = false,
            staleReason = null,
            currentPrice = null,
            dividends = bundle.dividends,
        )

        val snapshots = samples.mapNotNull { rawSample ->
            // Mappa il punto-di-campionamento al primo trading day disponibile
            // (FMP EOD non ha sabato/domenica/holiday).
            val (tradingDate, tradingPrice) =
                asOfDateTechnicalContext.closeOnOrAfter(sortedEod, rawSample)
                    ?: return@mapNotNull null

            val filtered = pointInTimeFilter.filter(baseDataset, tradingDate, tradingPrice)
            val taSnap = asOfDateTechnicalContext.snapshotAt(
                asOf = tradingDate,
                fullEodSeries = sortedEod,
                fullSma50 = bundle.sma50,
                fullSma200 = bundle.sma200,
                fullRsi = bundle.rsi,
                fullMacdDaily = bundle.macdDaily,
                fullMacdWeekly = bundle.macdWeekly,
                fullAtr = bundle.atr,
            )

            buildSnapshot(tradingDate, tradingPrice, filtered, taSnap)
        }

        return ReconstructResult(
            snapshots = snapshots,
            effectiveFrom = effectiveStart,
            effectiveTo = effectiveEnd,
            insufficientHistory = false,
            insufficientHistoryReason = null,
        )
    }

    /**
     * Costruisce lo snapshot del verdetto a `t`. Tutto qui dentro e' pure-function
     * deterministica: stesso `filtered` + `taSnap` → stesso verdetto.
     */
    private fun buildSnapshot(
        asOf: LocalDate,
        tradingPrice: Double,
        filtered: FinancialDataset,
        taSnap: AsOfDateTechnicalContext.TaSnapshot,
    ): VerdictSnapshot {
        // ---- Rule engine VI a t (no DCF user-override, no DB write) -------
        // Riusiamo direttamente RuleEngineService + DcfCalculator (no
        // AnalyzeTickerService): l'unico effetto collaterale che vogliamo
        // evitare e' la persistenza rule_engine_result, perche' un backtest
        // genererebbe ~60 record per chiamata (1 per mese * 5 anni). Inoltre
        // l'override DCF user-scoped introdurrebbe non-determinismo nel
        // backtest (stesso ticker, esiti diversi per user).
        val signals = runCatching { ruleEngineService.evaluateAll(filtered) }
            .onFailure { log.warn("RuleEngine failure at asOf={} ticker={}: {}", asOf, filtered.ticker, it.message) }
            .getOrDefault(emptyList())

        val dcf = runCatching { dcfCalculator.calculate(filtered, forcedMethod = null) }
            .getOrNull()
        val dcfIntrinsic = dcf?.intrinsicValue?.takeIf { dcf.method != DcfMethod.NOT_APPLICABLE }
        val mos = marginOfSafetyEvaluator.evaluate(tradingPrice, dcf ?: emptyDcfResult())

        // ---- Aggregazione VI ----------------------------------------------
        val viAgg = viVerdictAggregator.aggregate(signals)

        // ---- TA verdict ----------------------------------------------------
        val taVerdict = taSnap.entryTimingVerdict

        // ---- Summary verdict (gate VI hardcoded) --------------------------
        // Deep e' NULL nel backtest: la Deep Analysis (Munger) richiede
        // l'ingest dei 10-K e una run sincrona — non e' ricostruibile
        // point-in-time per definizione (i 10-K storici NON sono ri-ingestabili
        // al volo per ogni `t`). Limite documentato nel kdoc del controller
        // (TSK-348) e nei caveats: il Riepilogo del backtest applica VI + TA
        // senza Munger. Coerente con la lente di valore — il gate VI primario
        // resta hardcoded, la Deep aggiungerebbe solo override su un sottoinsieme.
        val summary = summaryVerdictAggregator.aggregate(
            SummaryVerdictAggregator.Input(
                viVerdict = viAgg.verdict,
                mosSignal = mos.signal,
                deepVerdict = null,
                taVerdict = taVerdict,
            ),
        )

        // VI-only signal: GREEN_DOMINANT + MoS GREEN. Replica la baseline B1 di
        // US-105 senza filtro TA — il VI puro entra ovunque ci sia gate
        // fondamentale positivo + MoS adeguato.
        val viOnlyEnter = viAgg.verdict == ViVerdict.GREEN_DOMINANT &&
            mos.signal == com.valueinvesting.webapp.ruleengine.Signal.GREEN

        return VerdictSnapshot(
            asOf = asOf,
            tradingPrice = tradingPrice,
            summaryVerdict = summary,
            viVerdict = viAgg.verdict,
            viOnlyEnter = viOnlyEnter,
            dcfIntrinsicValue = dcfIntrinsic,
            stopPrice = taSnap.stopPrice,
            stopDistance = taSnap.stopDistance,
            stopDistancePct = taSnap.stopDistancePct,
            taSnapshot = taSnap,
        )
    }

    /**
     * DcfResult "vuoto" usato quando il calcolo DCF lancia eccezione (es. dataset
     * filtrato troppo corto). Restituisce NOT_APPLICABLE → MoS NOT_CALCULABLE.
     */
    private fun emptyDcfResult(): com.valueinvesting.webapp.ruleengine.calculators.DcfResult =
        com.valueinvesting.webapp.ruleengine.calculators.DcfResult(
            intrinsicValue = null,
            method = DcfMethod.NOT_APPLICABLE,
            rationale = "DCF non calcolabile sul dataset filtrato a `t`.",
        )

    /** Numero di mesi calendariali (approssimato) tra due date. */
    internal fun monthsBetween(from: LocalDate, to: LocalDate): Int {
        if (to.isBefore(from)) return 0
        return java.time.Period.between(from, to).let { it.years * 12 + it.months }
    }

    private fun firstDayOfMonth(date: LocalDate): LocalDate =
        LocalDate.of(date.year, date.month, 1).let {
            if (it.isBefore(date)) it.plusMonths(1) else it
        }

    companion object {
        const val MIN_YEARS: Int = 1

        // Lo storico EOD FMP /stable/historical-price-eod tipicamente arriva a
        // 20+ anni: cap conservativo a 20 evita query costose senza ROI.
        const val MAX_YEARS: Int = 20

        // Horizon ammessi (verbatim US-105 §Endpoint: "1/3/6/12").
        val ALLOWED_HORIZONS: Set<Int> = setOf(1, 3, 6, 12)
    }
}
