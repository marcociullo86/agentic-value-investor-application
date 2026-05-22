package com.valueinvesting.webapp.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
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

    @Bean
    fun jacksonCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder
                .modulesToInstall(JavaTimeModule())
                .featuresToDisable(
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                )
                // Force-register the ProblemDetail flatten serializer directly
                // on the builder. Earlier attempts via Jackson mixin (b385926)
                // and @JsonComponent (873b9e6 / e8a0880) both failed: Spring's
                // @JsonComponent autodiscovery did not override Spring 6.x's
                // built-in ProblemDetail handling for the application/problem+
                // json message converter. serializerByType is the direct path
                // and takes precedence over any default ProblemDetail handling.
                .serializerByType(ProblemDetail::class.java, ProblemDetailJsonSerializer())
        }

    // Standalone ObjectMapper for utility usage (FMP DTO mapping in tests, etc.)
    @Bean
    fun kotlinObjectMapper() = jacksonObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}

/**
 * Custom serializer for `org.springframework.http.ProblemDetail` that flattens
 * the `properties` extension map into top-level fields, per RFC 9457 §3.2:
 *
 *   "Members of the problem details object that are not defined by this
 *    specification ("extension members") are siblings of the other members."
 *
 * Spring 6.x default behaviour leaves them nested:
 *   {"type":"...","title":"...","properties":{"ticker":"AAPL"}}
 * After this serializer:
 *   {"type":"...","title":"...","ticker":"AAPL"}
 *
 * Wired via Jackson2ObjectMapperBuilder.serializerByType(...) — earlier
 * attempts via Jackson mixin (b385926, signature-matching issue) and
 * @JsonComponent (873b9e6 / e8a0880, autodiscovery did not override Spring's
 * built-in ProblemDetail rendering) had no effect on the actual response.
 *
 * Referenced by ADR-007 §Error format (RFC 9457).
 */
class ProblemDetailJsonSerializer :
    StdSerializer<ProblemDetail>(ProblemDetail::class.java) {

    override fun serialize(
        value: ProblemDetail,
        gen: JsonGenerator,
        provider: SerializerProvider,
    ) {
        gen.writeStartObject()
        value.type?.let { gen.writeStringField("type", it.toString()) }
        value.title?.let { gen.writeStringField("title", it) }
        gen.writeNumberField("status", value.status)
        value.detail?.let { gen.writeStringField("detail", it) }
        value.instance?.let { gen.writeStringField("instance", it.toString()) }
        value.properties?.forEach { (key, propValue) ->
            gen.writeObjectField(key, propValue)
        }
        gen.writeEndObject()
    }
}
