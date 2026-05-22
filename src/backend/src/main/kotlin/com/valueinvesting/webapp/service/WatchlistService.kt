package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.api.model.WatchlistItemResponse
import com.valueinvesting.webapp.api.model.WatchlistResponse
import com.valueinvesting.webapp.persistence.entity.Stock
import com.valueinvesting.webapp.persistence.entity.Watchlist
import com.valueinvesting.webapp.persistence.entity.WatchlistItem
import com.valueinvesting.webapp.persistence.repository.StockRepository
import com.valueinvesting.webapp.persistence.repository.WatchlistItemRepository
import com.valueinvesting.webapp.persistence.repository.WatchlistRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Watchlist orchestration (TSK-029, US-017).
 *
 * - getWatchlist: returns the user's default watchlist; creates it lazily on
 *   first access (US-017 AC: watchlist personale persiste tra sessioni).
 * - addTicker: idempotent (UNIQUE (watchlist_id, ticker)); silently lazy-creates
 *   a placeholder `stocks` row when the ticker is unknown — full enrichment
 *   happens when the user opens the analysis page (FmpCacheService takes over).
 * - removeTicker: deletes the row, throws TickerNotInWatchlistException when
 *   the entry is absent (mapped to 404 by GlobalExceptionHandler).
 *
 * [^src: design_&_architecture/components/backend-components.md §WatchlistService]
 * [^src: design_&_architecture/api/openapi.yaml §/api/watchlist/**]
 */
@Service
class WatchlistService(
    private val watchlistRepository: WatchlistRepository,
    private val watchlistItemRepository: WatchlistItemRepository,
    private val stockRepository: StockRepository,
    private val clock: Clock,
) {

    @Transactional
    fun getWatchlist(userId: UUID): WatchlistResponse {
        val watchlist = ensureDefaultWatchlist(userId)
        val items = watchlistItemRepository
            .findByWatchlistIdOrderByAddedAtDesc(watchlist.id)
            .map { item -> item.toResponse() }
        return WatchlistResponse(
            id = watchlist.id,
            name = watchlist.name,
            isDefault = watchlist.isDefault,
            items = items,
        )
    }

    @Transactional
    fun addTicker(userId: UUID, ticker: String): WatchlistItemResponse {
        val normalized = ticker.uppercase()
        val watchlist = ensureDefaultWatchlist(userId)
        ensureStockExists(normalized)
        val existing = watchlistItemRepository.findByWatchlistIdAndTicker(watchlist.id, normalized)
        val saved = existing ?: watchlistItemRepository.save(
            WatchlistItem(
                watchlistId = watchlist.id,
                ticker = normalized,
                addedAt = Instant.now(clock),
            ),
        )
        return saved.toResponse()
    }

    @Transactional
    fun removeTicker(userId: UUID, ticker: String) {
        val normalized = ticker.uppercase()
        val watchlist = watchlistRepository.findByUserIdAndIsDefaultTrue(userId)
            ?: throw TickerNotInWatchlistException(normalized)
        val removed = watchlistItemRepository.deleteByWatchlistIdAndTicker(watchlist.id, normalized)
        if (removed == 0L) {
            throw TickerNotInWatchlistException(normalized)
        }
    }

    private fun ensureDefaultWatchlist(userId: UUID): Watchlist =
        watchlistRepository.findByUserIdAndIsDefaultTrue(userId)
            ?: watchlistRepository.save(
                Watchlist(
                    userId = userId,
                    name = "My Watchlist",
                    isDefault = true,
                    createdAt = Instant.now(clock),
                ),
            )

    private fun ensureStockExists(ticker: String) {
        if (!stockRepository.existsById(ticker)) {
            stockRepository.save(Stock(ticker = ticker))
        }
    }

    private fun WatchlistItem.toResponse(): WatchlistItemResponse {
        val stock = stockRepository.findById(ticker).orElse(null)
        return WatchlistItemResponse(
            ticker = ticker,
            companyName = stock?.companyName,
            sector = stock?.sector,
            marketCapUsd = stock?.marketCapUsd,
            addedAt = addedAt,
        )
    }
}

class TickerNotInWatchlistException(val ticker: String) :
    RuntimeException("Ticker '$ticker' is not in the watchlist")
