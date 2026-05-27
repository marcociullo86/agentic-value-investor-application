package com.valueinvesting.webapp.client

import com.valueinvesting.webapp.config.AppProperties
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class HibpClientRestClientTest {

    private lateinit var restClientBuilder: RestClient.Builder
    private lateinit var appProperties: AppProperties
    private lateinit var client: HibpClientRestClient

    @BeforeEach
    fun setup() {
        restClientBuilder = mockk(relaxed = true)
        appProperties = AppProperties(
            security = AppProperties.Security(
                hibp = AppProperties.Security.Hibp(enabled = true),
            ),
        )
        client = HibpClientRestClient(restClientBuilder, appProperties)
    }

    @Test
    fun `sha1PrefixAndSuffix splits password hash for k-anonymity`() {
        val (prefix, suffix) = client.sha1PrefixAndSuffix("password")
        assertThat(prefix).hasSize(5)
        assertThat(prefix).isEqualTo("5BAA6")
        assertThat(suffix).isEqualTo("1E4C9B93F3F0682250B6CF8331B7EE68FD8")
    }

    @Test
    fun `responseContainsSuffix matches HIBP range line format`() {
        val suffix = "1E4C9B93F3F0682250B6CF8331B7EE68FD8"
        val body = """
            00000:1
            1E4C9B93F3F0682250B6CF8331B7EE68FD8:3730471
            ABCDE:2
        """.trimIndent()
        assertThat(client.responseContainsSuffix(body, suffix)).isTrue()
    }

    @Test
    fun `responseContainsSuffix returns false when suffix absent`() {
        val body = "ABCDE:1\nFFFFF:2"
        assertThat(client.responseContainsSuffix(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8")).isFalse()
    }

    @Test
    fun `isPasswordCompromised returns false when HIBP disabled`() {
        appProperties = AppProperties(
            security = AppProperties.Security(
                hibp = AppProperties.Security.Hibp(enabled = false),
            ),
        )
        client = HibpClientRestClient(restClientBuilder, appProperties)
        assertThat(client.isPasswordCompromised("password")).isFalse()
    }
}
