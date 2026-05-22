package com.valueinvesting.webapp.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI

class FlatteningProblemDetailHttpMessageConverterTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())

    private val converter = FlatteningProblemDetailHttpMessageConverter(objectMapper)

    @Test
    fun `supports ProblemDetail and application problem+json`() {
        assertThat(converter.canWrite(ProblemDetail::class.java, MediaType.APPLICATION_PROBLEM_JSON))
            .isTrue()
        assertThat(converter.canRead(ProblemDetail::class.java, MediaType.APPLICATION_PROBLEM_JSON))
            .isFalse()
    }

    @Test
    fun `writes extension members as top-level siblings not under properties`() {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Ticker ZZZZ not found on FMP")
        problem.type = URI.create("https://api/errors/ticker-not-found")
        problem.title = "Ticker not found"
        problem.instance = URI.create("/api/search/ZZZZ")
        problem.setProperty("ticker", "ZZZZ")
        problem.setProperty("timestamp", "2026-05-22T10:00:00Z")

        val bytes = ByteArrayOutputStream()
        converter.write(
            problem,
            MediaType.APPLICATION_PROBLEM_JSON,
            object : HttpOutputMessage {
                override fun getBody() = bytes
                override fun getHeaders() = org.springframework.http.HttpHeaders()
            },
        )

        val json = objectMapper.readTree(bytes.toByteArray())
        assertThat(json.get("ticker").asText()).isEqualTo("ZZZZ")
        assertThat(json.get("timestamp").asText()).isEqualTo("2026-05-22T10:00:00Z")
        assertThat(json.get("status").asInt()).isEqualTo(404)
        assertThat(json.get("title").asText()).isEqualTo("Ticker not found")
        assertThat(json.get("type").asText()).isEqualTo("https://api/errors/ticker-not-found")
        assertThat(json.get("instance").asText()).isEqualTo("/api/search/ZZZZ")
        assertThat(json.has("properties")).isFalse()
    }

    @Test
    fun `read is not supported`() {
        val input = ByteArrayInputStream("{}".toByteArray())
        val message = object : HttpInputMessage {
            override fun getBody() = input
            override fun getHeaders() = org.springframework.http.HttpHeaders()
        }
        org.junit.jupiter.api.assertThrows<HttpMessageNotReadableException> {
            converter.read(ProblemDetail::class.java, message)
        }
    }
}
