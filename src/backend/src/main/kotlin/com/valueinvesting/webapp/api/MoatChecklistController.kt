package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.MoatChecklistEntryRequest
import com.valueinvesting.webapp.api.model.MoatChecklistEntryResponse
import com.valueinvesting.webapp.api.model.MoatChecklistResponse
import com.valueinvesting.webapp.security.UserPrincipal
import com.valueinvesting.webapp.service.MoatChecklistService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Moat qualitative checklist endpoints (TSK-026, US-016).
 *
 * [^src: design_&_architecture/api/openapi.yaml §/api/moat-checklist/{ticker}]
 */
@RestController
@RequestMapping("/api/moat-checklist")
class MoatChecklistController(
    private val moatService: MoatChecklistService,
) {

    @GetMapping("/{ticker}")
    fun getChecklist(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable ticker: String,
    ): ResponseEntity<MoatChecklistResponse> =
        ResponseEntity.ok(moatService.getChecklist(principal.userId, ticker))

    @PostMapping("/{ticker}")
    fun upsertEntry(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable ticker: String,
        @Valid @RequestBody request: MoatChecklistEntryRequest,
    ): ResponseEntity<MoatChecklistEntryResponse> =
        ResponseEntity.ok(moatService.upsertEntry(principal.userId, ticker, request))
}
