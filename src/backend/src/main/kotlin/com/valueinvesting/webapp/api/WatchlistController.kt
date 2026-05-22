package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.WatchlistItemRequest
import com.valueinvesting.webapp.api.model.WatchlistItemResponse
import com.valueinvesting.webapp.api.model.WatchlistResponse
import com.valueinvesting.webapp.security.UserPrincipal
import com.valueinvesting.webapp.service.WatchlistService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Watchlist CRUD endpoints (TSK-029, US-017).
 *
 * [^src: design_&_architecture/api/openapi.yaml §/api/watchlist /api/watchlist/items]
 */
@RestController
@RequestMapping("/api/watchlist")
class WatchlistController(
    private val watchlistService: WatchlistService,
) {

    @GetMapping
    fun getWatchlist(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<WatchlistResponse> =
        ResponseEntity.ok(watchlistService.getWatchlist(principal.userId))

    @PostMapping("/items")
    fun addItem(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: WatchlistItemRequest,
    ): ResponseEntity<WatchlistItemResponse> =
        ResponseEntity.ok(watchlistService.addTicker(principal.userId, request.ticker))

    @DeleteMapping("/items/{ticker}")
    fun removeItem(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable ticker: String,
    ): ResponseEntity<Void> {
        watchlistService.removeTicker(principal.userId, ticker)
        return ResponseEntity.noContent().build()
    }
}
