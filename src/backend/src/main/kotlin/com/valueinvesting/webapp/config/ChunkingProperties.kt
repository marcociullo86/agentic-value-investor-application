package com.valueinvesting.webapp.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "chunking")
data class ChunkingProperties(
    val chunkSize: Int = 6000,
    val chunkOverlap: Int = 400,
)
