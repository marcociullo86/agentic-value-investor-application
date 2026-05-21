package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
}
