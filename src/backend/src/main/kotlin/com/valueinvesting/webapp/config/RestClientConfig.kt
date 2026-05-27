package com.valueinvesting.webapp.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

// REST client beans: sync RestClient for general use; WebClient (reactive) for FMP parallel fetch.
// [^src: design_&_architecture/decisions/ADR-002-backend-stack.md §Moduli Spring]
// [^src: design_&_architecture/components/backend-components.md §Decisioni di concorrenza]
@Configuration
class RestClientConfig {

    @Bean
    fun restClient(): RestClient =
        RestClient.builder()
            .defaultHeader("Accept", "application/json")
            .build()

    // WebClient targeted at FMP (future parallel fetch). Skipped when `app.fmp.mock=true`
    // so test profile does not bind reactor-netty to an invalid base URL.
    @Bean
    @ConditionalOnProperty(name = ["app.fmp.mock"], havingValue = "false", matchIfMissing = true)
    fun fmpWebClient(appProperties: AppProperties): WebClient {
        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(10))

        return WebClient.builder()
            .baseUrl(appProperties.fmp.baseUrl)
            .defaultHeader("Accept", "application/json")
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
