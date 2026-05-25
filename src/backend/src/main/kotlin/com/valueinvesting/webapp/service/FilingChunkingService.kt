package com.valueinvesting.webapp.service

import com.valueinvesting.webapp.config.ChunkingProperties
import org.springframework.stereotype.Service

/**
 * Recursive character text splitter (LangChain-equivalent, pure Kotlin).
 * Splits text into chunks of configurable size with overlap.
 * [^src: wiki/runbooks/sec-10k-10q-analysis-playbook.md §Step 2 — Indicizzazione]
 */
@Service
class FilingChunkingService(private val props: ChunkingProperties) {

    companion object {
        private val SEPARATORS = listOf("\n\n\n", "\n\n", "\n", " ", "")
    }

    fun chunk(text: String): List<TextChunk> {
        if (text.length <= props.chunkSize) {
            return listOf(TextChunk(index = 0, content = text))
        }

        val rawChunks = splitRecursive(text, SEPARATORS)
        return rawChunks.mapIndexed { idx, content -> TextChunk(index = idx, content = content) }
    }

    private fun splitRecursive(text: String, separators: List<String>): List<String> {
        if (text.length <= props.chunkSize) return listOf(text)
        if (separators.isEmpty()) {
            return splitBySize(text)
        }

        val separator = separators.first()
        val remainingSeparators = separators.drop(1)

        val splits = if (separator.isEmpty()) {
            return splitBySize(text)
        } else {
            text.split(separator)
        }

        val merged = mergeWithOverlap(splits, separator)

        return merged.flatMap { chunk ->
            if (chunk.length > props.chunkSize) {
                splitRecursive(chunk, remainingSeparators)
            } else {
                listOf(chunk)
            }
        }
    }

    private fun mergeWithOverlap(splits: List<String>, separator: String): List<String> {
        val results = mutableListOf<String>()
        var current = StringBuilder()

        for (split in splits) {
            val candidate = if (current.isEmpty()) split
            else current.toString() + separator + split

            if (candidate.length > props.chunkSize && current.isNotEmpty()) {
                results.add(current.toString())

                val overlapText = extractOverlap(current.toString())
                current = StringBuilder(overlapText)
                if (current.isNotEmpty()) current.append(separator)
                current.append(split)
            } else {
                current = StringBuilder(candidate)
            }
        }

        if (current.isNotEmpty()) {
            results.add(current.toString())
        }

        return results
    }

    private fun extractOverlap(text: String): String {
        if (props.chunkOverlap <= 0) return ""
        val start = (text.length - props.chunkOverlap).coerceAtLeast(0)
        return text.substring(start)
    }

    private fun splitBySize(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = (start + props.chunkSize).coerceAtMost(text.length)
            chunks.add(text.substring(start, end))
            start = end - props.chunkOverlap
            if (start >= text.length) break
            if (end == text.length) break
        }
        return chunks
    }
}

data class TextChunk(val index: Int, val content: String)
