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
    }

    @Transactional
    fun analyze(ticker: String): PriceActionSnapshot {
        val today = LocalDate.now()
        val cached = snapshotRepo.findByTickerAndCalcDate(ticker, today)
        if (cached != null) {
            log.debug("Price action cache hit for {} on {}", ticker, today)
            return entityToSnapshot(cached)
        }

        val eodPrices = fmpAdapter.getHistoricalEodPrices(ticker, 365)
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
            val max52w = closePrices.max()
            val min52w = closePrices.min()
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

    private fun persistSnapshot(ticker: String, calcDate: LocalDate, s: PriceActionSnapshot) {
        val entity = PriceActionSnapshotEntity(
            ticker = ticker,
            calcDate = calcDate,
            priceNow = s.priceNow?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP),
            max52w = s.max52w?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP),
            min52w = s.min52w?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP),
            drawdownPct = s.drawdownPct?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP),
            trend3mPct = s.trend3mPct?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP),
            ma50 = s.ma50?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP),
            ma200 = s.ma200?.toBigDecimal()?.setScale(4, RoundingMode.HALF_UP),
            panicDiscount = s.panicDiscount,
            deteriorationWarning = s.deteriorationWarning,
            seriesDays = s.seriesDays,
        )
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
