package com.valueinvesting.webapp.config

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ProblemDetail

// Jackson tuning — RFC 8259 JSON, ISO-8601 timestamps, Kotlin null-safety friendly.
// [^src: raw/tech_stack.md §Standards verbatim]
@Configuration
class JacksonConfig {

    /**
     * Jackson mixin that forces ProblemDetail.getProperties() to be serialized
     * with @JsonAnyGetter, i.e. each key in the properties map becomes a
     * top-level field on the JSON object. This matches RFC 9457 §3.2 which
     * states that extension members are siblings of `type`, `title`, `status`,
     * `detail`, `instance` — not nested under a `properties` key.
     *
     * Without this mixin, Spring 6.x ProblemDetail serializes the properties
     * map as a nested object:
     *   {"type":"...","title":"...","properties":{"ticker":"AAPL"}}
     * With this mixin, the extensions are flattened:
     *   {"type":"...","title":"...","ticker":"AAPL"}
     *
     * Referenced by ADR-007 §Error format (RFC 9457).
     */
    @Suppress("unused") // referenced via mixIn() — Jackson resolves at serialization time
    abstract class ProblemDetailUnwrapMixin {
        @JsonAnyGetter
        abstract fun getProperties(): Map<String, Any?>?
    }

    @Bean
    fun jacksonCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder
                .modules(JavaTimeModule())
                .featuresToDisable(
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                )
                .mixIn(ProblemDetail::class.java, ProblemDetailUnwrapMixin::class.java)
        }

    // Standalone ObjectMapper for utility usage (FMP DTO mapping in tests, etc.)
    @Bean
    fun kotlinObjectMapper() = jacksonObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
