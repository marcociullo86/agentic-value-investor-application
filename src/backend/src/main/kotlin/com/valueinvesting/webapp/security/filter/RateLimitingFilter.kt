package com.valueinvesting.webapp.security.filter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.valueinvesting.webapp.service.AuthRateLimitService
import com.valueinvesting.webapp.service.AuthRateLimitService.AuthEndpoint
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

// Per-IP and per-account rate limiting on auth endpoints (TSK-229 / US-081).
// [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §5]
class RateLimitingFilter(
    private val authRateLimitService: AuthRateLimitService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val endpoint = resolveEndpoint(request) ?: run {
            filterChain.doFilter(request, response)
            return
        }

        val repeatable = if (request is RepeatableReadHttpServletRequest) {
            request
        } else {
            RepeatableReadHttpServletRequest(request)
        }
        val bodyBytes = repeatable.cachedBody()

        val ip = resolveClientIp(repeatable)
        val email = extractEmail(bodyBytes, repeatable.contentType)
        val userAgent = repeatable.getHeader("User-Agent")

        val decision = authRateLimitService.checkAndRecord(endpoint, ip, email, userAgent)
        if (!decision.allowed) {
            writeTooManyRequests(response, decision.retryAfterSeconds)
            return
        }

        filterChain.doFilter(repeatable, response)
    }

    private fun resolveEndpoint(request: HttpServletRequest): AuthEndpoint? {
        if (!HttpMethod.POST.matches(request.method)) {
            return null
        }
        return when (authPath(request)) {
            LOGIN_PATH -> AuthEndpoint.LOGIN
            REGISTER_PATH -> AuthEndpoint.REGISTER
            PASSWORD_RESET_PATH -> AuthEndpoint.PASSWORD_RESET
            else -> null
        }
    }

    private fun authPath(request: HttpServletRequest): String {
        val servletPath = request.servletPath
        if (servletPath.isNotEmpty()) {
            return servletPath
        }
        val contextPath = request.contextPath.orEmpty()
        val uri = request.requestURI.orEmpty()
        return if (contextPath.isNotEmpty() && uri.startsWith(contextPath)) {
            uri.removePrefix(contextPath)
        } else {
            uri
        }
    }

    private fun extractEmail(body: ByteArray, contentType: String?): String? {
        if (!contentType.orEmpty().startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            return null
        }
        if (body.isEmpty()) {
            return null
        }
        return runCatching {
            val root: JsonNode = objectMapper.readTree(body)
            root.get("email")?.asText()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr
    }

    private fun writeTooManyRequests(response: HttpServletResponse, retryAfterSeconds: Long) {
        response.status = TOO_MANY_REQUESTS
        response.setHeader(RETRY_AFTER_HEADER, retryAfterSeconds.toString())
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write(
            """
            {
              "type": "https://api/errors/rate-limited",
              "title": "Too Many Requests",
              "status": 429,
              "detail": "Rate limit exceeded. Retry after $retryAfterSeconds seconds."
            }
            """.trimIndent(),
        )
    }

    companion object {
        private const val TOO_MANY_REQUESTS = 429

        const val LOGIN_PATH = "/api/auth/login"
        const val REGISTER_PATH = "/api/auth/register"
        const val PASSWORD_RESET_PATH = "/api/auth/password-reset"
        const val RETRY_AFTER_HEADER = "Retry-After"
    }
}
