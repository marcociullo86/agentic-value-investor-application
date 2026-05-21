package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.api.model.DcfOverrideRequest
import com.valueinvesting.webapp.api.model.DcfOverrideResponse
import com.valueinvesting.webapp.service.DcfOverrideService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/dcf-overrides")
class DcfOverrideController(
    private val dcfOverrideService: DcfOverrideService,
) {

    @PostMapping
    fun upsert(
        @RequestHeader(name = HEADER_USER_ID) userIdHeader: String?,
        @Valid @RequestBody request: DcfOverrideRequest,
    ): ResponseEntity<DcfOverrideResponse> {
        val userId = parseUserId(userIdHeader)
        val saved = dcfOverrideService.upsert(userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    @DeleteMapping("/{ticker}")
    fun delete(
        @RequestHeader(name = HEADER_USER_ID) userIdHeader: String?,
        @PathVariable ticker: String,
    ): ResponseEntity<Void> {
        val userId = parseUserId(userIdHeader)
        dcfOverrideService.delete(userId, ticker)
        return ResponseEntity.noContent().build()
    }

    private fun parseUserId(header: String?): UUID =
        header?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw IllegalArgumentException("Missing or invalid $HEADER_USER_ID header (JWT in TSK-033)")

    companion object {
        const val HEADER_USER_ID = "X-User-Id"
    }
}
