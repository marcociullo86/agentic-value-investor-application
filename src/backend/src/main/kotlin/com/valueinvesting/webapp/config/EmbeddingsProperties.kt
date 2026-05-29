package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "embeddings")
data class EmbeddingsProperties(
    val sidecar: Sidecar = Sidecar(),
    // batchSize basso (4): Qwen3-0.6B/1024-dim su CPU impiega ~6s per chunk da
    // 6000 char. Batch piccoli tengono ogni POST /embed ben sotto il timeout
    // sidecar; la deep analysis è asincrona, quindi i round-trip extra non
    // bloccano l'utente. Per tornare veloci → modello CPU-friendly (bge-small
    // 384-dim) + migration dimensione pgvector.
    val batchSize: Int = 4,
    val model: Model = Model(),
) {
    data class Sidecar(
        val url: String = "http://embeddings-sidecar:8001",
        // 600s: un batch di 4 chunk grandi su CPU resta largamente sotto soglia.
        // Il timeout aggressivo (30s) causava 500/embedding_unavailable. Valore
        // alto perché l'embedding gira in background (vedi DeepAnalysisRunExecutor),
        // quindi attese lunghe non impattano l'utente.
        val timeoutSeconds: Long = 600,
    )

    data class Model(
        val name: String = "Qwen/Qwen3-Embedding-0.6B",
    )
}
