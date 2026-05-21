package com.valueinvesting.webapp.config

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

    // WebClient targeted at FMP — used by FmpAdapterRestClient (TSK successivi).
    // Resilience4j is layered in FmpResilienceConfig, not here.
    @Bean
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
