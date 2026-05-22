package com.valueinvesting.webapp.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// Registers FlatteningProblemDetailHttpMessageConverter ahead of Spring's
// default ProblemDetail serializer (ADR-012).
@Configuration
class ProblemDetailMvcConfig(
    private val objectMapper: ObjectMapper,
) : WebMvcConfigurer {

    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(0, FlatteningProblemDetailHttpMessageConverter(objectMapper))
    }
}
