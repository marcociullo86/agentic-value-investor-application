package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor

/**
 * Test profile: run @Async("eventLoggerExecutor") synchronously so ITs can
 * assert fmp_api_event_log rows without racing the thread pool (TSK-070 / CI).
 */
@Configuration
@Profile("test")
class TestAsyncConfig {

    @Bean("eventLoggerExecutor")
    fun eventLoggerExecutor(): TaskExecutor = SyncTaskExecutor()
}
