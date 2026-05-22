package com.valueinvesting.webapp.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Jackson tuning — RFC 8259 JSON, ISO-8601 timestamps, Kotlin null-safety friendly.
// ProblemDetail responses use FlatteningProblemDetailHttpMessageConverter (ADR-012),
// not Jackson customization on ProblemDetail.
//
// [^src: raw/tech_stack.md §Standards verbatim]
@Configuration
class JacksonConfig {

    @Bean
    fun jacksonCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder
                .modulesToInstall(JavaTimeModule())
                .featuresToDisable(
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                )
        }

    // Standalone ObjectMapper for utility usage (FMP DTO mapping in tests, etc.)
    @Bean
    fun kotlinObjectMapper() = jacksonObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
