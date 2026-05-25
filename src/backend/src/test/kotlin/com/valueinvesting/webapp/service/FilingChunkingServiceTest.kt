package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.config.ChunkingProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FilingChunkingServiceTest {

    private val service = FilingChunkingService(ChunkingProperties(chunkSize = 6000, chunkOverlap = 400))

    @Test
    fun `short text produces single chunk`() {
        val text = "a".repeat(100)
        val chunks = service.chunk(text)
        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].content).isEqualTo(text)
        assertThat(chunks[0].index).isEqualTo(0)
    }

    @Test
    fun `text exactly at chunk size produces single chunk`() {
        val text = "a".repeat(6000)
        val chunks = service.chunk(text)
        assertThat(chunks).hasSize(1)
    }

    @Test
    fun `12000 chars produces at least 2 chunks with overlap`() {
        val text = "a".repeat(12000)
        val chunks = service.chunk(text)
        assertThat(chunks.size).isGreaterThanOrEqualTo(2)
        chunks.forEach { chunk ->
            assertThat(chunk.content.length).isLessThanOrEqualTo(6000)
        }
    }

    @Test
    fun `contiguous chunks overlap by approximately 400 chars`() {
        val text = "word ".repeat(2400)
        val chunks = service.chunk(text)
        assertThat(chunks.size).isGreaterThanOrEqualTo(2)

        for (i in 0 until chunks.size - 1) {
            val currentEnd = chunks[i].content.takeLast(400)
            val nextStart = chunks[i + 1].content.take(400)
            assertThat(nextStart).isEqualTo(currentEnd)
        }
    }

    @Test
    fun `18000 chars produces at least 3 chunks`() {
        val text = "a".repeat(18000)
        val chunks = service.chunk(text)
        assertThat(chunks.size).isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `chunks split on paragraph separators when possible`() {
        val para1 = "A".repeat(3000)
        val para2 = "B".repeat(3000)
        val para3 = "C".repeat(3000)
        val text = "$para1\n\n$para2\n\n$para3"

        val chunks = service.chunk(text)
        assertThat(chunks.size).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `chunk indices are sequential`() {
        val text = "x".repeat(18000)
        val chunks = service.chunk(text)
        chunks.forEachIndexed { idx, chunk ->
            assertThat(chunk.index).isEqualTo(idx)
        }
    }
}
