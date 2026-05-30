package com.valueinvesting.webapp.fmp

import com.valueinvesting.webapp.config.FmpRateLimitProperties
import com.valueinvesting.webapp.fmp.dto.IncomeStatementDto
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.retry.Retry
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

// Unit test for the Resilience4j chain produced by FmpResilienceConfig.
// We intentionally do NOT spin up @SpringBootTest here: testing the chain is a
// behavior test about Resilience4j wiring + ResilientFmpAdapter glue, and the
// config beans are pure factories — so we instantiate the same registries the
// @Configuration would build and drive them through the decorator.  This keeps
// the test < 1s, no DB / no AOP, while still exercising real Resilience4j code
// paths (not mocks) for CB, Retry, RateLimiter, Bulkhead.
//
// DoD coverage:
//   - transient error -> ≥ 1 retry before failing            (testRetryOnTransientFailure)
//   - 5xx triggers FmpEventLogger.log5xx                     (testEventLoggerOn5xx)
//   - circuit opens after sustained failures                  (testCircuitOpensAfterFailures)
//   - circuit OPEN fast-fails subsequent calls                (testCircuitOpenFastFails)
//
// The cache + stale fallback path is covered separately in FinancialDataServiceTest.
//
// [^src: management/kanban/.../TSK-011.md §DoD]
class FmpResilienceConfigTest {

    private val config = FmpResilienceConfig()
    private lateinit var cb: CircuitBreaker
    private lateinit var retry: Retry
    private lateinit var rateLimiter: RateLimiter
    private lateinit var bulkhead: Bulkhead
    private val delegate: FmpAdapter = mockk()
    private val eventLogger: FmpEventLogger = mockk(relaxed = true)
    private lateinit var resilient: ResilientFmpAdapter

    @Test
    fun `default rate limiter uses the shared per-account cap (DEFAULT_RATE_LIMIT_PER_MINUTE)`() {
        // Limite UNICO condiviso online+batch (FMP Starter 300/min → 280 default).
        val limiter = config.fmpRateLimiterRegistry().rateLimiter(FmpResilienceConfig.FMP_INSTANCE)
        assertThat(limiter.rateLimiterConfig.limitForPeriod)
            .isEqualTo(FmpRateLimitProperties.DEFAULT_RATE_LIMIT_PER_MINUTE)
    }

    @BeforeEach
    fun setUp() {
        // Build fresh registries per test so CB state doesn't leak between tests.
        cb = config.fmpCircuitBreakerRegistry().circuitBreaker(FmpResilienceConfig.FMP_INSTANCE)
        retry = config.fmpRetryRegistry().retry(FmpResilienceConfig.FMP_INSTANCE)
        rateLimiter = config.fmpRateLimiterRegistry().rateLimiter(FmpResilienceConfig.FMP_INSTANCE)
        bulkhead = config.fmpBulkheadRegistry().bulkhead(FmpResilienceConfig.FMP_INSTANCE)

        resilient = ResilientFmpAdapter(
            delegate = delegate,
            circuitBreaker = cb,
            retry = retry,
            rateLimiter = rateLimiter,
            bulkhead = bulkhead,
            eventLogger = eventLogger,
        )
    }

    @Test
    fun `transient FmpUnavailableException triggers at least one retry before fail (DoD)`() {
        val attempts = AtomicInteger(0)
        every { delegate.getIncomeStatement("AAPL", any()) } answers {
            attempts.incrementAndGet()
            throw FmpUnavailableException("simulated 5xx")
        }
        justRun { eventLogger.log5xx(any(), any(), any(), any()) }

        assertThatThrownBy { resilient.getIncomeStatement("AAPL") }
            .isInstanceOf(FmpUnavailableException::class.java)

        // maxAttempts = 3 -> 1 original + 2 retries.  At minimum: > 1 (>= 1 retry).
        assertThat(attempts.get()).isGreaterThan(1)
        assertThat(attempts.get()).isLessThanOrEqualTo(FmpResilienceConfig.MAX_ATTEMPTS)
    }

    @Test
    fun `5xx classified upstream triggers FmpEventLogger log5xx (DoD)`() {
        every { delegate.getIncomeStatement("AAPL", any()) } throws
            FmpUnavailableException("FMP returned 500 for income-statement/AAPL")
        justRun { eventLogger.log5xx(any(), any(), any(), any()) }

        assertThatThrownBy { resilient.getIncomeStatement("AAPL") }
            .isInstanceOf(FmpUnavailableException::class.java)

        // Each failed retry attempt logs (see ResilientFmpAdapter.execute) — assert ≥ 1.
        verify(atLeast = 1) { eventLogger.log5xx(eq("AAPL"), eq("income-statement"), any(), any()) }
    }

    @Test
    fun `recovers on second attempt when first attempt fails`() {
        val attempts = AtomicInteger(0)
        every { delegate.getIncomeStatement("AAPL", any()) } answers {
            val n = attempts.incrementAndGet()
            if (n == 1) throw FmpUnavailableException("transient")
            listOf(IncomeStatementDto(symbol = "AAPL", calendarYear = "2024"))
        }
        justRun { eventLogger.log5xx(any(), any(), any(), any()) }

        val result = resilient.getIncomeStatement("AAPL")

        assertThat(result).hasSize(1)
        assertThat(attempts.get()).isEqualTo(2)
    }

    @Test
    fun `FmpTickerNotFoundException is NOT retried (ignored by retry config)`() {
        val attempts = AtomicInteger(0)
        every { delegate.getIncomeStatement("ZZZZ", any()) } answers {
            attempts.incrementAndGet()
            throw FmpTickerNotFoundException("ZZZZ")
        }
        justRun { eventLogger.logTickerNotFound(any(), any()) }

        assertThatThrownBy { resilient.getIncomeStatement("ZZZZ") }
            .isInstanceOf(FmpTickerNotFoundException::class.java)

        // Not-found is a legitimate semantic response — no retry, no CB increment.
        assertThat(attempts.get()).isEqualTo(1)
        verify(exactly = 1) { eventLogger.logTickerNotFound("ZZZZ", "income-statement") }
    }

    @Test
    fun `circuit opens after sustained failures and subsequent calls fast-fail`() {
        every { delegate.getIncomeStatement(any(), any()) } throws
            FmpUnavailableException("persistent 5xx")
        justRun { eventLogger.log5xx(any(), any(), any(), any()) }
        justRun { eventLogger.logCircuitOpen(any()) }

        // Drive enough failed calls to exceed the min-calls-before-eval window
        // and the failure rate.  Each call = MAX_ATTEMPTS adapter invocations
        // (1 + retries) but the CB records logical-call outcomes, so we need
        // at least MIN_CALLS_BEFORE_EVAL logical failures.
        repeat(FmpResilienceConfig.MIN_CALLS_BEFORE_EVAL + 2) {
            runCatching { resilient.getIncomeStatement("AAPL") }
        }

        assertThat(cb.state).isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN)
        // While CB is OPEN, subsequent call must fast-fail with FmpUnavailableException
        // (we wrap CallNotPermittedException in ResilientFmpAdapter).
        assertThatThrownBy { resilient.getIncomeStatement("AAPL") }
            .isInstanceOf(FmpUnavailableException::class.java)
        verify(atLeast = 1) { eventLogger.logCircuitOpen(any()) }
    }
}
