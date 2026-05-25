package com.valueinvesting.webapp

import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration
import io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// Bootstrap entrypoint Spring Boot 3.5 + Kotlin 2.2
// [^src: design_&_architecture/components/backend-components.md §Package map]
// [^src: design_&_architecture/decisions/ADR-002-backend-stack.md]
@SpringBootApplication(
    exclude = [
        CircuitBreakerAutoConfiguration::class,
        RetryAutoConfiguration::class,
        RateLimiterAutoConfiguration::class,
        BulkheadAutoConfiguration::class,
        TimeLimiterAutoConfiguration::class,
    ],
)
@ConfigurationPropertiesScan
@EnableScheduling
class ValueInvestingWebappApplication

fun main(args: Array<String>) {
    runApplication<ValueInvestingWebappApplication>(*args)
}
