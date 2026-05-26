package com.valueinvesting.webapp.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.encoder.EncoderBase
import net.logstash.logback.encoder.LogstashEncoder
import java.util.regex.Pattern

/**
 * Logback encoder that wraps [LogstashEncoder] and applies PII redaction regex
 * on the serialized JSON output before writing. Operates on byte[]-to-String
 * level — no JSON tree re-parsing — to stay within the 2 ms p99 budget.
 *
 * Environment-aware: when [relaxedMode] is true (dev/test) and the event level
 * is DEBUG or TRACE, IBAN and email patterns are skipped.
 *
 * Recursive: after top-level redaction the encoder detects escaped-JSON strings
 * (fields whose value starts with `{\"` or `[\"`) and applies redaction inside
 * those fragments as well.
 *
 * [^src: design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md §4]
 */
class PiiRedactionEncoder : EncoderBase<ILoggingEvent>() {

    // ── configurable from logback-spring.xml ──
    var innerEncoder: LogstashEncoder = LogstashEncoder()
    var enabled: Boolean = true
    var relaxedMode: Boolean = false

    // ── compiled patterns (loaded once at start) ──
    private lateinit var alwaysPatterns: List<CompiledRedaction>
    private lateinit var strictOnlyPatterns: List<CompiledRedaction>
    private lateinit var escapedJsonDetector: Pattern

    override fun start() {
        innerEncoder.context = context
        innerEncoder.start()

        alwaysPatterns = listOf(
            CompiledRedaction(
                Pattern.compile("""\b(\d{6})\d{3,9}(\d{4})\b"""),
                "$1****$2"
            ),
            CompiledRedaction(
                Pattern.compile("""eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"""),
                "[REDACTED]"
            ),
            CompiledRedaction(
                Pattern.compile("""\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.)\d{1,3}\b"""),
                "$1" + "0"
            ),
            CompiledRedaction(
                Pattern.compile("""(?i)("(?:password|api[_-]?key|secret|refresh[_-]?token|access[_-]?token|authorization|x-api-key)"\s*:\s*")([^"]+)(")"""),
                "$1[REDACTED]$3"
            ),
        )

        strictOnlyPatterns = listOf(
            CompiledRedaction(
                Pattern.compile("""\b([A-Z]{2})\d{2}[A-Z0-9]{4,30}([A-Z0-9]{4})\b"""),
                "$1****$2"
            ),
            CompiledRedaction(
                Pattern.compile("""\b[\w.+-]+@([\w-]+\.[\w.-]+)\b"""),
                "***@$1"
            ),
        )

        escapedJsonDetector = Pattern.compile("""\\?"\\?[{\[]\\?"""")

        super.start()
    }

    override fun stop() {
        innerEncoder.stop()
        super.stop()
    }

    override fun headerBytes(): ByteArray? = innerEncoder.headerBytes()

    override fun encode(event: ILoggingEvent): ByteArray {
        val raw = innerEncoder.encode(event)
        if (!enabled) return raw

        val json = String(raw, Charsets.UTF_8)
        val redacted = redact(json, isRelaxed(event))
        return redacted.toByteArray(Charsets.UTF_8)
    }

    override fun footerBytes(): ByteArray? = innerEncoder.footerBytes()

    // ── internals ──

    private fun isRelaxed(event: ILoggingEvent): Boolean =
        relaxedMode && (event.level == Level.DEBUG || event.level == Level.TRACE)

    internal fun redact(input: String, relaxed: Boolean): String {
        var result = applyPatterns(input, relaxed)
        result = redactEscapedJson(result, relaxed)
        return result
    }

    private fun applyPatterns(text: String, relaxed: Boolean): String {
        var result = text
        for (p in alwaysPatterns) {
            result = p.pattern.matcher(result).replaceAll(p.replacement)
        }
        if (!relaxed) {
            for (p in strictOnlyPatterns) {
                result = p.pattern.matcher(result).replaceAll(p.replacement)
            }
        }
        return result
    }

    /**
     * Detects JSON-in-string values (escaped JSON embedded as field values)
     * and applies redaction inside those fragments. Handles one nesting level
     * which covers all practical structured-logging cases.
     */
    private fun redactEscapedJson(text: String, relaxed: Boolean): String {
        if (!escapedJsonDetector.matcher(text).find()) return text

        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '"' && i > 0 && text[i - 1] == ':') {
                val start = i + 1
                val end = findEndOfJsonString(text, start)
                if (end > start) {
                    val inner = text.substring(start, end)
                    if (looksLikeEscapedJson(inner)) {
                        val unescaped = inner.replace("\\\"", "\"").replace("\\\\", "\\")
                        val redacted = applyPatterns(unescaped, relaxed)
                        val reescaped = redacted.replace("\\", "\\\\").replace("\"", "\\\"")
                        sb.append('"')
                        sb.append(reescaped)
                        sb.append('"')
                        i = end + 1
                        continue
                    }
                }
            }
            sb.append(text[i])
            i++
        }
        return sb.toString()
    }

    private fun findEndOfJsonString(text: String, start: Int): Int {
        var i = start
        while (i < text.length) {
            if (text[i] == '"' && (i == start || text[i - 1] != '\\')) return i
            i++
        }
        return start
    }

    private fun looksLikeEscapedJson(s: String): Boolean =
        (s.startsWith("{\\\"") || s.startsWith("[\\\"")) && s.length > 10

    private data class CompiledRedaction(val pattern: Pattern, val replacement: String)
}
