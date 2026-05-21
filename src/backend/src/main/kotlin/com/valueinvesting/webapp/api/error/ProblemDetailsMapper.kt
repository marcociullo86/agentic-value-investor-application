package com.valueinvesting.webapp.api.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Instant

// Builds RFC 9457 Problem Details payloads.
// [^src: design_&_architecture/decisions/ADR-007-api-contract.md §Error format]
// [^src: raw/tech_stack.md §Standards verbatim — RFC 9457]
@Component
class ProblemDetailsMapper {

    fun build(
        status: HttpStatus,
        type: String,
        title: String,
        detail: String,
        request: HttpServletRequest?,
        extensions: Map<String, Any?> = emptyMap(),
    ): ProblemDetail {
        val problem = ProblemDetail.forStatus(status)
        problem.type = URI.create(type)
        problem.title = title
        problem.detail = detail
        problem.instance = request?.requestURI?.let { URI.create(it) }
        problem.setProperty("timestamp", Instant.now().toString())
        MDC.get("requestId")?.let { problem.setProperty("requestId", it) }
        extensions.forEach { (key, value) ->
            if (value != null) problem.setProperty(key, value)
        }
        return problem
    }
}
