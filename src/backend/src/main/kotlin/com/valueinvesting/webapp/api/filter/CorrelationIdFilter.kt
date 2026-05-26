package com.valueinvesting.webapp.api.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

// Generates / propagates X-Correlation-Id and injects it into MDC.
// [^src: design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md §3]
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CorrelationIdFilter : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Correlation-Id"
        const val MDC_KEY = "correlationId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val incoming = request.getHeader(HEADER)?.takeIf { it.isNotBlank() }
        val correlationId = incoming ?: UUID.randomUUID().toString()
        try {
            MDC.put(MDC_KEY, correlationId)
            response.setHeader(HEADER, correlationId)
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
