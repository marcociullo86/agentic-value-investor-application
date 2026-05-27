package com.valueinvesting.webapp.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.AbstractHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.stereotype.Component

// RFC 9457 Problem Details with extension members at top-level (ADR-012).
// Spring 6.x default nests extensions under `properties`; this converter
// flattens them for `application/problem+json` responses. Registered both
// as an MVC HttpMessageConverter (ProblemDetailMvcConfig) and as a Spring
// bean injectable by servlet-chain handlers — see SecurityConfig entry
// points (TSK-033) that need byte-identical bodies for 401/403 emitted
// outside the MVC dispatcher.
// [^src: design_&_architecture/decisions/ADR-012-problemdetail-rfc9457-flatten.md §1]
@Component
class FlatteningProblemDetailHttpMessageConverter(
    private val objectMapper: ObjectMapper,
) : AbstractHttpMessageConverter<ProblemDetail>(MediaType.APPLICATION_PROBLEM_JSON) {

    override fun supports(clazz: Class<*>): Boolean =
        ProblemDetail::class.java.isAssignableFrom(clazz)

    override fun canRead(mediaType: MediaType?): Boolean = false

    override fun readInternal(
        clazz: Class<out ProblemDetail>,
        inputMessage: HttpInputMessage,
    ): ProblemDetail = throw HttpMessageNotReadableException(
        "FlatteningProblemDetailHttpMessageConverter does not support reading ProblemDetail",
        inputMessage,
    )

    override fun writeInternal(
        problemDetail: ProblemDetail,
        outputMessage: HttpOutputMessage,
    ) {
        outputMessage.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val bytes = objectMapper.writeValueAsBytes(flatten(problemDetail))
        outputMessage.body.write(bytes)
    }

    internal fun flatten(problemDetail: ProblemDetail): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        problemDetail.type?.let { map["type"] = it.toString() }
        problemDetail.title?.let { map["title"] = it }
        problemDetail.status?.let { map["status"] = it }
        problemDetail.detail?.let { map["detail"] = it }
        problemDetail.instance?.let { map["instance"] = it.toString() }
        problemDetail.properties?.forEach { (key, value) -> map[key] = value }
        return map
    }
}
