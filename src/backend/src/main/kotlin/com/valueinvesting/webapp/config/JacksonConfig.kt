package com.valueinvesting.webapp.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Jackson tuning — RFC 8259 JSON, ISO-8601 timestamps, Kotlin null-safety friendly.
//
// Known gap: ProblemDetail extensions remain nested under a `properties` key
// (Spring 6.x default), diverging from RFC 9457 §3.2 which requires extensions
// as top-level siblings. Multiple flatten attempts were ineffective in CI:
//   - b385926: Jackson mixin + @JsonAnyGetter on abstract Kotlin method
//             (signature did not match Java bytecode of ProblemDetail
//             .getProperties())
//   - 873b9e6: StdSerializer<ProblemDetail> registered via @JsonComponent
//             (autodiscovery did not override Spring's default rendering)
//   - e8a0880: modulesToInstall vs modules() (additive vs replace) — no effect
//   - 20f846b: Jackson2ObjectMapperBuilder.serializerByType(...) — no effect
// All four landed correctly but never surfaced in the response body. Tracked
// as gap `be-problemdetail-flatten`. Tests currently assert the nested shape
// (`$.properties.ticker`) until a working flatten path is found.
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
