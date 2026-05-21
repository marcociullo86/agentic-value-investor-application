package com.valueinvesting.webapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

// Bootstrap entrypoint Spring Boot 3.5 + Kotlin 2.2
// [^src: design_&_architecture/components/backend-components.md §Package map]
// [^src: design_&_architecture/decisions/ADR-002-backend-stack.md]
@SpringBootApplication
@ConfigurationPropertiesScan
class ValueInvestingWebappApplication

fun main(args: Array<String>) {
    runApplication<ValueInvestingWebappApplication>(*args)
}
