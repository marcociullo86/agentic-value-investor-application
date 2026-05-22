package com.valueinvesting.webapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// Bootstrap entrypoint Spring Boot 3.5 + Kotlin 2.2
// [^src: design_&_architecture/components/backend-components.md §Package map]
// [^src: design_&_architecture/decisions/ADR-002-backend-stack.md]
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class ValueInvestingWebappApplication

fun main(args: Array<String>) {
    runApplication<ValueInvestingWebappApplication>(*args)
}
