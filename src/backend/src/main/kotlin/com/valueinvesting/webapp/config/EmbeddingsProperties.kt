package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "embeddings")
data class EmbeddingsProperties(
    val sidecar: Sidecar = Sidecar(),
    val batchSize: Int = 32,
    val model: Model = Model(),
) {
    data class Sidecar(
        val url: String = "http://embeddings-sidecar:8001",
        val timeoutSeconds: Long = 30,
    )

    data class Model(
        val name: String = "Qwen/Qwen3-Embedding-0.6B",
    )
}
