package com.valueinvesting.webapp.job

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

// Async trigger for manual invocation of TopValuePicksJob.
//
// The batch job may take 10-30 minutes (universe of ~500 candidates, deep
// analysis per ticker). HTTP requests must NOT block the request thread for
// that long, so the trigger fires-and-forgets on a dedicated single-thread
// executor and returns 202 Accepted immediately.
//
// Concurrency contract:
//   - At most ONE run at a time (atomic flag). Concurrent triggers receive
//     `AlreadyRunning` and must retry later.
//   - This protects the DELETE-then-INSERT upsert in `TopValuePicksJob.run()`
//     against duplicate-key races on `top_value_picks (run_date, ticker)`.
@Component
class TopPicksManualTrigger(
    private val job: TopValuePicksJob,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val lastStartedAt = AtomicReference<Instant?>(null)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "top-picks-manual-trigger").apply { isDaemon = true }
    }

    fun trigger(): TriggerResult {
        if (!running.compareAndSet(false, true)) {
            return TriggerResult.AlreadyRunning(startedAt = lastStartedAt.get())
        }
        val startedAt = Instant.now()
        lastStartedAt.set(startedAt)
        executor.execute {
            try {
                log.info("TopValuePicksJob manual trigger START at={}", startedAt)
                job.run()
                log.info("TopValuePicksJob manual trigger DONE startedAt={}", startedAt)
            } catch (ex: Exception) {
                log.error("TopValuePicksJob manual trigger FAILED startedAt={}", startedAt, ex)
            } finally {
                running.set(false)
            }
        }
        return TriggerResult.Started(startedAt = startedAt)
    }

    fun isRunning(): Boolean = running.get()

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }

    sealed class TriggerResult {
        data class Started(val startedAt: Instant) : TriggerResult()
        data class AlreadyRunning(val startedAt: Instant?) : TriggerResult()
    }
}
