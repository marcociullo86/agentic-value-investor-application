package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.fmp.FmpAdapter
import com.valueinvesting.webapp.persistence.entity.PriceActionSnapshotEntity
import com.valueinvesting.webapp.persistence.repository.PriceActionSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Analyzes 52-week price action to detect panic discounts and deterioration signals.
 * [^src: wiki/concepts/market-fluctuations-graham.md §Volatilità come opportunità]
 * [^src: wiki/concepts/mr-market.md §Cattura opportunità Mr. Market]
 */
@Service
class PriceActionAnalyzer(
    private val fmpAdapter: FmpAdapter,
    private val snapshotRepo: PriceActionSnapshotRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PANIC_THRESHOLD = -30.0
        private const val DETERIORATION_TREND_THRESHOLD = -10.0
        private const val MIN_SERIES_DAYS = 252

        // Finestra di download EOD in giorni di CALENDARIO. Serve a garantire
        // ≥ MIN_SERIES_DAYS (252) giorni di TRADING: 365 giorni di calendario
        // rendono solo ~250-252 trading days (weekend + festivi), proprio al
        // limite della soglia → con un anno "sfortunato" la serie cade sotto 252
        // e max52w/min52w diventano null (chart "Dati di prezzo non disponibili"
        // pur avendo il prezzo corrente). 400 giorni di calendario ≈ ~275 trading
        // days, margine sufficiente. Il calcolo di max/min 52w usa comunque solo
        // le ultime MIN_SERIES_DAYS sedute (vedi window52w), così la semantica
        // "52 week" resta esatta nonostante la finestra di download più ampia.
        private const val EOD_LOOKBACK_CALENDAR_DAYS = 400
    }

    @Transactional
    fun analyze(ticker: String): PriceActionSnapshot {
        val today = LocalDate.now()
        val cached = snapshotRepo.findByTickerAndCalcDate(ticker, today)
        // Riusa la cache SOLO se lo snapshot è completo. Uno snapshot degradato
        // (max52w null = serie EOD insufficiente) NON viene servito dalla cache:
        // lo ricalcoliamo, così un risultato parziale dovuto a una serie corta
        // transitoria non resta "congelato" per l'intera giornata (root cause del
        // box "Dati di prezzo non disponibili" pur avendo il prezzo corrente).
        if (cached != null && cached.max52w != null) {
            log.debug("Price action cache hit for {} on {}", ticker, today)
            return entityToSnapshot(cached)
        }

        val eodPrices = fmpAdapter.getHistoricalEodPrices(ticker, EOD_LOOKBACK_CALENDAR_DAYS)
        val profile = fmpAdapter.getProfile(ticker)
        val currentPrice = profile.price

        val closePrices = eodPrices
            .filter { it.close != null }
            .sortedBy { it.date }
            .map { it.close!! }

        val seriesDays = closePrices.size
        val snapshot: PriceActionSnapshot

        if (seriesDays < MIN_SERIES_DAYS || currentPrice == null) {
            snapshot = PriceActionSnapshot(
                ticker = ticker,
                priceNow = currentPrice,
                max52w = null,
                min52w = null,
                drawdownPct = null,
                trend3mPct = null,
                ma50 = null,
                ma200 = null,
                panicDiscount = false,
                deteriorationWarning = false,
                seriesDays = seriesDays,
                note = "Serie insufficiente ($seriesDays < $MIN_SERIES_DAYS giorni)",
            )
        } else {
            // 52w high/low sull'ultimo anno di trading (252 sedute), non
            // sull'intera serie scaricata (~275 sedute con la finestra a 400gg):
            // mantiene la semantica "52 week" anche con il margine di download.
            val window52w = closePrices.takeLast(MIN_SERIES_DAYS)
            val max52w = window52w.max()
            val min52w = window52w.min()
            val drawdownPct = (currentPrice - max52w) / max52w * 100.0

            val trend3mPct = if (closePrices.size > 63) {
                val price63dAgo = closePrices[closePrices.size - 63]
                (closePrices.last() - price63dAgo) / price63dAgo * 100.0
            } else null

            val ma50 = closePrices.takeLast(50).average()
            val ma200 = if (closePrices.size >= 200) closePrices.takeLast(200).average() else null

            val panicDiscount = drawdownPct <= PANIC_THRESHOLD
            val deteriorationWarning = trend3mPct != null && ma200 != null &&
                trend3mPct <= DETERIORATION_TREND_THRESHOLD && ma50 < ma200

            snapshot = PriceActionSnapshot(
                ticker = ticker,
                priceNow = currentPrice,
                max52w = max52w,
                min52w = min52w,
                drawdownPct = drawdownPct,
                trend3mPct = trend3mPct,
                ma50 = ma50,
                ma200 = ma200,
                panicDiscount = panicDiscount,
                deteriorationWarning = deteriorationWarning,
                seriesDays = seriesDays,
                note = null,
            )
        }

        persistSnapshot(ticker, today, snapshot)
        return snapshot
    }

    // Upsert sulla riga (ticker, calc_date): la tabella ha un unique constraint
    // uq_price_action_ticker_date, quindi un ricalcolo nello stesso giorno (es.
    // dopo uno snapshot degradato non servito dalla cache) deve AGGIORNARE la
    // riga esistente, non inserirne una nuova (che violerebbe il constraint).
    private fun persistSnapshot(ticker: String, calcDate: LocalDate, s: PriceActionSnapshot) {
        val entity = snapshotRepo.findByTickerAndCalcDate(ticker, calcDate)
            ?: PriceActionSnapshotEntity(ticker = ticker, calcDate = calcDate)
        entity.priceNow = s.priceNow?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP)
        entity.max52w = s.max52w?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP)
        entity.min52w = s.min52w?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP)
        entity.drawdownPct = s.drawdownPct?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP)
        entity.trend3mPct = s.trend3mPct?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP)
        entity.ma50 = s.ma50?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP)
        entity.ma200 = s.ma200?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP)
        entity.panicDiscount = s.panicDiscount
        entity.deteriorationWarning = s.deteriorationWarning
        entity.seriesDays = s.seriesDays
        snapshotRepo.save(entity)
    }

    private fun entityToSnapshot(e: PriceActionSnapshotEntity) = PriceActionSnapshot(
        ticker = e.ticker,
        priceNow = e.priceNow?.toDouble(),
        max52w = e.max52w?.toDouble(),
        min52w = e.min52w?.toDouble(),
        drawdownPct = e.drawdownPct?.toDouble(),
        trend3mPct = e.trend3mPct?.toDouble(),
        ma50 = e.ma50?.toDouble(),
        ma200 = e.ma200?.toDouble(),
        panicDiscount = e.panicDiscount ?: false,
        deteriorationWarning = e.deteriorationWarning ?: false,
        seriesDays = e.seriesDays ?: 0,
        note = null,
    )
}

data class PriceActionSnapshot(
    val ticker: String,
    val priceNow: Double?,
    val max52w: Double?,
    val min52w: Double?,
    val drawdownPct: Double?,
    val trend3mPct: Double?,
    val ma50: Double?,
    val ma200: Double?,
    val panicDiscount: Boolean,
    val deteriorationWarning: Boolean,
    val seriesDays: Int,
    val note: String?,
)
