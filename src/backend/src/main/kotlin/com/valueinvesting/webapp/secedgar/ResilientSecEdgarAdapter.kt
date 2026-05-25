package com.valueinvesting.webapp.secedgar

import com.valueinvesting.webapp.secedgar.dto.SecFilingMetadata
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.function.Supplier

// Decorator che wrappa ogni chiamata SEC EDGAR con la chain Resilience4j
// configurata in `SecEdgarResilienceConfig`.
//
// @Primary → tutti i call site `SecEdgarAdapter` (DeepAnalysisService et al.,
// TSK-094+) iniettano questo bean. Il `SecEdgarRestClient` rimane esposto via
// `@Qualifier("secEdgarRestClient")` per i test unit che bypassano la resilienza.
//
// CHAIN ORDER (per allineamento con FMP module):
//   Request → CircuitBreaker → Retry → HTTP call
//   RateLimiter applicato all'outermost layer (= 1 token per logical call, NON
//   per attempt — un retry burst non brucia 3 token).
//
// NB: nessun event-logger SEC (a differenza di `ResilientFmpAdapter` con
// `FmpEventLogger`). Audit/observability è demandato a un futuro TSK
// (probabilmente parte di EP-014 LLM cascade observability). Per ora, i log
// strutturati di `SecEdgarRestClient` + Resilience4j metrics su /actuator/health
// bastano per debugging.
//
// [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/TSK-091.md §6]
// [^src: raw/tech_stack.md §Backend - Resilience]
@Component
@Primary
class ResilientSecEdgarAdapter(
    @Qualifier("secEdgarRestClient") private val delegate: SecEdgarAdapter,
    private val secEdgarCircuitBreaker: CircuitBreaker,
    private val secEdgarRetry: Retry,
    private val secEdgarRateLimiter: RateLimiter,
) : SecEdgarAdapter {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun resolveCikFromTicker(ticker: String): String? =
        execute("resolveCikFromTicker", ticker) { delegate.resolveCikFromTicker(ticker) }

    override fun listFilings(
        cik: String,
        formTypes: List<String>,
        limit: Int,
    ): List<SecFilingMetadata> =
        execute("listFilings", cik) { delegate.listFilings(cik, formTypes, limit) }

    override fun downloadFilingHtml(url: String): String? =
        execute("downloadFilingHtml", url) { delegate.downloadFilingHtml(url) }

    /**
     * Applica la chain: Request → CircuitBreaker → Retry → block().
     * RateLimiter gate all'outermost layer (1 token per chiamata logica).
     */
    private fun <T> execute(operation: String, target: String, block: () -> T): T {
        val instrumented: Supplier<T> = Supplier { block() }

        // Compose innermost-first: Retry around CircuitBreaker.
        val decorated: Supplier<T> = CircuitBreaker.decorateSupplier(
            secEdgarCircuitBreaker,
            Retry.decorateSupplier(secEdgarRetry, instrumented),
        )

        // RateLimiter al layer più esterno: 1 logical call ↔ 1 token (NON per attempt).
        val gated: Supplier<T> = RateLimiter.decorateSupplier(secEdgarRateLimiter, decorated)

        return try {
            gated.get()
        } catch (ex: CallNotPermittedException) {
            log.warn(
                "SEC EDGAR circuit OPEN for {}/{} — call not permitted",
                operation, target,
            )
            throw SecEdgarServiceException(
                "SEC EDGAR circuit open for $operation",
                httpStatus = 503,
                cause = ex,
            )
        }
    }
}
