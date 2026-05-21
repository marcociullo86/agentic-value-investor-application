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

// Generates / propagates X-Request-Id and injects it into MDC.
// [^src: design_&_architecture/decisions/ADR-008-observability-logging.md §1 Correlation ID]
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val incoming = request.getHeader(HEADER)?.takeIf { it.isNotBlank() }
        val requestId = incoming ?: UUID.randomUUID().toString()
        try {
            MDC.put(MDC_KEY, requestId)
            response.setHeader(HEADER, requestId)
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
