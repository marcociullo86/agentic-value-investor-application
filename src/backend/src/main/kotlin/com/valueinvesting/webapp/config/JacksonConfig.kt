package com.valueinvesting.webapp.config

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.boot.jackson.JsonComponent
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
                // modulesToInstall (additive) — NOT .modules() (replace), since
                // replacing drops Spring Boot's JsonComponentModule that auto-
                // discovers @JsonComponent serializers (e.g. our
                // ProblemDetailJsonSerializer below). JavaTimeModule is auto-
                // registered by Spring Boot when jackson-datatype-jsr310 is on
                // the classpath, but we list it explicitly to keep the contract
                // visible at this config site.
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
 * Picked up automatically by Spring Boot via @JsonComponent. Earlier attempt
 * via a Jackson mixin + @JsonAnyGetter (commit b385926) did not take effect —
 * Jackson did not match the Kotlin mixin signature against the Java bytecode
 * signature `Map<String, Object>` of ProblemDetail.getProperties(). An
 * explicit StdSerializer sidesteps that signature-matching path entirely.
 *
 * Referenced by ADR-007 §Error format (RFC 9457).
 */
@JsonComponent
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
