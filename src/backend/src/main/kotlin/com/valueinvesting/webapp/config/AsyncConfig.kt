package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

// Async infrastructure for fire-and-forget side effects (observability writes,
// FMP event logging, etc.).  The default Spring `@Async` would use a
// SimpleAsyncTaskExecutor (new thread per call — unbounded, no naming) which
// is unsuitable for production.  We declare a small named pool with a bounded
// queue and a CallerRunsPolicy backpressure strategy so that, under pressure,
// logging cannot fail silently nor starve the JVM.
//
// Used by FmpEventLogger (`@Async("eventLoggerExecutor")`).
//
// [^src: design_&_architecture/decisions/ADR-008-observability-logging.md §Async writes]
// [^src: management/kanban/.../TSK-011.md §FmpEventLogger]
@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("fmpExecutor")
    fun fmpExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 50
            setThreadNamePrefix("fmp-fetch-")
            setRejectedExecutionHandler(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(15)
            initialize()
        }

    @Bean("eventLoggerExecutor")
    @Profile("!test")
    fun eventLoggerExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 200
            setThreadNamePrefix("evt-logger-")
            // CallerRunsPolicy: if the queue is full, run on the calling thread.
            // For audit/log writes this is preferable to silently dropping events.
            setRejectedExecutionHandler(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }

    // Executor dedicato per la deep-analysis async pipeline (POST
    // /api/analysis/{ticker}/deep/runs). DeepAnalysisService.analyze impiega
    // minuti (embedding sidecar + LLM): un pool separato evita che un burst
    // di richieste deep saturi `fmpExecutor` o gli HTTP thread Tomcat.
    //
    // - core=2, max=4 → cap concorrenza deep analysis (LLM/embedding sono cari)
    // - queue=50      → buffer in caso di burst, evita reject troppo aggressivi
    // - CallerRunsPolicy → fallback: meglio eseguire sul caller (raro, dato che
    //   l'enqueue avviene da MVC thread) che droppare silenziosamente la run.
    // - waitForTasksToCompleteOnShutdown=true + 30s → consente alle run in volo
    //   di completare durante un graceful shutdown senza lasciare row RUNNING
    //   appese (l'executor robustamente le marca FAILED nel finally; vedi
    //   DeepAnalysisRunExecutor).
    @Bean("deepAnalysisExecutor")
    fun deepAnalysisExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 50
            setThreadNamePrefix("deep-analysis-")
            setRejectedExecutionHandler(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(30)
            initialize()
        }
}
