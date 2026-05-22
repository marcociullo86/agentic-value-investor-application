package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.DcfOverrideRequest
import com.valueinvesting.webapp.api.model.DcfOverrideResponse
import com.valueinvesting.webapp.security.UserPrincipal
import com.valueinvesting.webapp.service.DcfOverrideService
import com.valueinvesting.webapp.service.exception.DcfOverrideNotFoundException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/dcf-overrides")
class DcfOverrideController(
    private val dcfOverrideService: DcfOverrideService,
) {

    @GetMapping("/{ticker}")
    fun getOverride(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable ticker: String,
    ): DcfOverrideResponse {
        return dcfOverrideService.findByUserAndTicker(principal.userId, ticker)
            ?: throw DcfOverrideNotFoundException(ticker.uppercase())
    }

    @PostMapping
    fun upsert(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: DcfOverrideRequest,
    ): ResponseEntity<DcfOverrideResponse> {
        val saved = dcfOverrideService.upsertWithFeasibilityCheck(principal.userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @DeleteMapping("/{ticker}")
    fun delete(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable ticker: String,
    ): ResponseEntity<Void> {
        dcfOverrideService.delete(principal.userId, ticker)
        return ResponseEntity.noContent().build()
    }
}
