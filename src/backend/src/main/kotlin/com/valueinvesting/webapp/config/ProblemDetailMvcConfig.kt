package com.valueinvesting.webapp.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// Registers FlatteningProblemDetailHttpMessageConverter ahead of Spring's
// default ProblemDetail serializer (ADR-012). The same Spring-managed bean
// is reused by SecurityConfig entry-point handlers (TSK-033) so the body
// emitted by the servlet filter chain matches the MVC body byte-for-byte.
@Configuration
class ProblemDetailMvcConfig(
    private val flatteningProblemDetailHttpMessageConverter: FlatteningProblemDetailHttpMessageConverter,
) : WebMvcConfigurer {

    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(0, flatteningProblemDetailHttpMessageConverter)
    }
}
