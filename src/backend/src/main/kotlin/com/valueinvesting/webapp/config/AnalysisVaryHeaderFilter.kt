package com.valueinvesting.webapp.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Ensures `Vary: Authorization` is present on analysis responses after CORS
 * filters may have set `Vary: Origin` only (ADR-011).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class AnalysisVaryHeaderFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(request, response)
        if (!request.requestURI.startsWith("/api/analysis/")) {
            return
        }
        val current = response.getHeader(HttpHeaders.VARY).orEmpty()
        if (current.contains("Authorization", ignoreCase = true)) {
            return
        }
        val updated = if (current.isBlank()) {
            "Authorization"
        } else {
            "$current, Authorization"
        }
        response.setHeader(HttpHeaders.VARY, updated)
    }
}
