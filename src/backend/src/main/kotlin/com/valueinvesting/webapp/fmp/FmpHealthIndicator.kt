package com.valueinvesting.webapp.fmp

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

// Contributes to GET /actuator/health under the `fmp` key.  State is derived
// from the CircuitBreakerRegistry for the "fmp" instance (FmpResilienceConfig).
//
// State mapping (per endpoints-overview.md §Endpoint trasversali):
//   CLOSED          -> UP
//   HALF_OPEN       -> DEGRADED   (probing; partial traffic allowed)
//   OPEN            -> DOWN       (CB tripped; using stale fallback)
//   FORCED_OPEN     -> DOWN
//   DISABLED        -> UP         (CB intentionally bypassed)
//   METRICS_ONLY    -> UP
//
// Also bridges CB state transitions to FmpEventLogger so an OPEN transition
// is captured in fmp_api_event_log even if no caller invokes the adapter
// during the OPEN window (e.g. low-traffic dev).
//
// [^src: design_&_architecture/api/endpoints-overview.md §Endpoint trasversali]
// [^src: design_&_architecture/decisions/ADR-008-observability-logging.md §Health]
// [^src: management/kanban/.../TSK-011.md §FmpHealthIndicator]
// Bean name "fmpHealth" -> contributes to /actuator/health under the `fmpHealth`
// key (Spring strips the trailing "HealthIndicator" only when the suffix is exact).
// Naming chosen to avoid collision with the `fmpCircuitBreaker` / `fmpRetry`
// resilience beans in the same package.
@Component("fmpHealth")
class FmpHealthIndicator(
    @Qualifier("fmpCircuitBreakerRegistry") registry: CircuitBreakerRegistry,
    private val eventLogger: FmpEventLogger,
) : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val circuitBreaker: CircuitBreaker =
        registry.circuitBreaker(FmpResilienceConfig.FMP_INSTANCE)

    init {
        // Persist OPEN/HALF_OPEN transitions for forensic analysis.
        circuitBreaker.eventPublisher
            .onStateTransition { ev ->
                log.info(
                    "FMP circuit transition {} -> {}",
                    ev.stateTransition.fromState,
                    ev.stateTransition.toState,
                )
                if (ev.stateTransition.toState == CircuitBreaker.State.OPEN) {
                    eventLogger.logCircuitOpen(
                        "state transition ${ev.stateTransition.fromState} -> OPEN",
                    )
                }
            }
    }

    override fun health(): Health {
        val state = circuitBreaker.state
        val metrics = circuitBreaker.metrics
        val detail = mapOf(
            "state" to state.name,
            "failureRate" to metrics.failureRate,
            "slowCallRate" to metrics.slowCallRate,
            "bufferedCalls" to metrics.numberOfBufferedCalls,
            "failedCalls" to metrics.numberOfFailedCalls,
            "successfulCalls" to metrics.numberOfSuccessfulCalls,
        )
        return when (state) {
            CircuitBreaker.State.CLOSED,
            CircuitBreaker.State.DISABLED,
            CircuitBreaker.State.METRICS_ONLY,
            -> Health.up().withDetails(detail).build()
            CircuitBreaker.State.HALF_OPEN,
            -> Health.status(DEGRADED_STATUS).withDetails(detail).build()
            CircuitBreaker.State.OPEN,
            CircuitBreaker.State.FORCED_OPEN,
            -> Health.down().withDetails(detail).build()
        }
    }

    companion object {
        // Custom non-standard health status surfaced on /actuator/health.
        // Spring renders it as-is; ops dashboards should treat DEGRADED as
        // "serve but alert" (per ADR-008 §Health).
        val DEGRADED_STATUS = org.springframework.boot.actuate.health.Status("DEGRADED")
    }
}
