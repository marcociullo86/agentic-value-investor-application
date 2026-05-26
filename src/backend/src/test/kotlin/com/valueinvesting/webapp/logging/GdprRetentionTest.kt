package com.valueinvesting.webapp.logging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * GDPR retention policy and pseudonymization tests (TSK-183, US-063).
 *
 * Validates logback retention config (FILE_OPS 30d, FILE_SECURITY 365d),
 * security event marker filter, application.yml defaults, pseudonymization
 * script behaviour, and ADR-021 §7 documentation.
 *
 * [^src: management/kanban/EP-014-logging-strutturato-observability/US-063-gdpr-retention-policy/TSK-183.md §Technical Specs]
 */
class GdprRetentionTest {

    private val yamlMapper = ObjectMapper(YAMLFactory())

    private val repoRoot: Path by lazy {
        val canonical = System.getProperty("contract.openapi.canonical")
        if (canonical != null) {
            Path.of(canonical).parent.parent.parent
        } else {
            Path.of("").toAbsolutePath().parent.parent
        }
    }

    // -- XML helpers -------------------------------------------------------

    private fun loadLogbackXml(): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val input = javaClass.classLoader.getResourceAsStream("logback-spring.xml")
            ?: error("logback-spring.xml not found on classpath")
        return factory.newDocumentBuilder().parse(input)
    }

    private fun xpath(doc: Document, expr: String): String =
        XPathFactory.newInstance().newXPath().evaluate(expr, doc).trim()

    private fun xpathNodes(doc: Document, expr: String): NodeList =
        XPathFactory.newInstance().newXPath()
            .evaluate(expr, doc, XPathConstants.NODESET) as NodeList

    // -- YAML helpers ------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun loadBaseYamlDocument(): Map<String, Any> {
        val raw = javaClass.classLoader.getResourceAsStream("application.yml")
            ?.bufferedReader()?.readText()
            ?: error("application.yml not found on classpath")
        val firstDoc = raw.split(Regex("\n---\n"))[0]
        return yamlMapper.readValue(firstDoc, Map::class.java) as Map<String, Any>
    }

    // -- Script helpers ----------------------------------------------------

    private data class ScriptResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun resolveScript(): Path {
        val script = repoRoot.resolve("src/backend/scripts/pseudonymize-user-logs.sh")
        assertThat(script)
            .describedAs("Pseudonymization script must exist at %s", script)
            .exists()
        return script
    }

    private fun runScript(script: Path, userId: String, logDir: Path): ScriptResult {
        val process = ProcessBuilder("/bin/bash", script.toString(), userId, logDir.toString())
            .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return ScriptResult(exitCode, stdout, stderr)
    }

    private fun createSampleLogs(dir: Path, targetId: String, otherId: String) {
        Files.writeString(dir.resolve("app.log"), buildString {
            appendLine("""{"@timestamp":"2026-05-26T10:00:00Z","level":"INFO","userId":"$targetId","message":"Login success"}""")
            appendLine("""{"@timestamp":"2026-05-26T10:01:00Z","level":"INFO","userId":"$otherId","message":"Login success"}""")
            appendLine("""{"@timestamp":"2026-05-26T10:02:00Z","level":"WARN","userId":$targetId,"message":"Rate limited"}""")
            appendLine("""{"@timestamp":"2026-05-26T10:03:00Z","level":"WARN","userId":$otherId,"message":"Rate limited"}""")
        })
        Files.writeString(dir.resolve("app-pretty.log"), buildString {
            appendLine("10:00:00.000 INFO  [req-001] [corr-001] [userId:$targetId] c.v.w.api - Login success")
            appendLine("10:01:00.000 INFO  [req-002] [corr-002] [userId:$otherId] c.v.w.api - Login success")
        })
    }

    private fun readAllLogs(dir: Path): String = buildString {
        Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .forEach { appendLine(Files.readString(it)) }
        }
    }

    // == Retention config: FILE_OPS appender ===============================

    @Test
    fun `FILE_OPS maxHistory references OPS_RETENTION_DAYS`() {
        val doc = loadLogbackXml()
        val maxHistory = xpath(doc,
            "//appender[@name='FILE_OPS']/rollingPolicy/maxHistory")

        assertThat(maxHistory)
            .describedAs("FILE_OPS maxHistory must reference OPS_RETENTION_DAYS")
            .contains("OPS_RETENTION_DAYS")
    }

    @Test
    fun `FILE_OPS uses TimeBasedRollingPolicy`() {
        val doc = loadLogbackXml()
        val policyClass = xpath(doc,
            "//appender[@name='FILE_OPS']/rollingPolicy/@class")

        assertThat(policyClass)
            .isEqualTo("ch.qos.logback.core.rolling.TimeBasedRollingPolicy")
    }

    // == Retention config: FILE_SECURITY appender ==========================

    @Test
    fun `FILE_SECURITY maxHistory references SECURITY_RETENTION_DAYS`() {
        val doc = loadLogbackXml()
        val maxHistory = xpath(doc,
            "//appender[@name='FILE_SECURITY']/rollingPolicy/maxHistory")

        assertThat(maxHistory)
            .describedAs("FILE_SECURITY maxHistory must reference SECURITY_RETENTION_DAYS")
            .contains("SECURITY_RETENTION_DAYS")
    }

    // == Security event filter =============================================

    @Test
    fun `FILE_SECURITY has OnMarkerEvaluator filtering SECURITY_EVENT`() {
        val doc = loadLogbackXml()
        val evalClass = xpath(doc,
            "//appender[@name='FILE_SECURITY']/filter/evaluator/@class")
        val marker = xpath(doc,
            "//appender[@name='FILE_SECURITY']/filter/evaluator/marker")

        assertThat(evalClass)
            .isEqualTo("ch.qos.logback.classic.boolex.OnMarkerEvaluator")
        assertThat(marker).isEqualTo("SECURITY_EVENT")
    }

    @Test
    fun `FILE_SECURITY filter accepts matching marker and denies non-matching`() {
        val doc = loadLogbackXml()

        assertThat(xpath(doc, "//appender[@name='FILE_SECURITY']/filter/onMatch"))
            .isEqualTo("ACCEPT")
        assertThat(xpath(doc, "//appender[@name='FILE_SECURITY']/filter/onMismatch"))
            .isEqualTo("DENY")
    }

    // == springProperty defaults in logback-spring.xml =====================

    @Test
    fun `springProperty OPS_RETENTION_DAYS defaults to 30`() {
        val doc = loadLogbackXml()
        val nodes = xpathNodes(doc, "//springProperty[@name='OPS_RETENTION_DAYS']")

        assertThat(nodes.length).isGreaterThan(0)
        val defaultVal = nodes.item(0).attributes.getNamedItem("defaultValue").textContent
        assertThat(defaultVal).isEqualTo("30")
    }

    @Test
    fun `springProperty SECURITY_RETENTION_DAYS defaults to 365`() {
        val doc = loadLogbackXml()
        val nodes = xpathNodes(doc, "//springProperty[@name='SECURITY_RETENTION_DAYS']")

        assertThat(nodes.length).isGreaterThan(0)
        val defaultVal = nodes.item(0).attributes.getNamedItem("defaultValue").textContent
        assertThat(defaultVal).isEqualTo("365")
    }

    // == application.yml retention defaults ================================

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `application yml operational-days defaults to 30`() {
        val yml = loadBaseYamlDocument()
        val retention = ((yml["app"] as Map<String, Any>)["logging"] as Map<String, Any>)["retention"] as Map<String, Any>
        val value = retention["operational-days"].toString()

        assertThat(value)
            .describedAs("operational-days env default must be 30")
            .contains("30")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `application yml security-events-days defaults to 365`() {
        val yml = loadBaseYamlDocument()
        val retention = ((yml["app"] as Map<String, Any>)["logging"] as Map<String, Any>)["retention"] as Map<String, Any>
        val value = retention["security-events-days"].toString()

        assertThat(value)
            .describedAs("security-events-days env default must be 365")
            .contains("365")
    }

    // == springProperty sources cross-reference application.yml keys =======

    @Test
    fun `springProperty sources map to existing application yml keys`() {
        val doc = loadLogbackXml()
        val opsSource = xpathNodes(doc, "//springProperty[@name='OPS_RETENTION_DAYS']")
            .item(0).attributes.getNamedItem("source").textContent
        val secSource = xpathNodes(doc, "//springProperty[@name='SECURITY_RETENTION_DAYS']")
            .item(0).attributes.getNamedItem("source").textContent

        assertThat(opsSource).isEqualTo("app.logging.retention.operational-days")
        assertThat(secSource).isEqualTo("app.logging.retention.security-events-days")

        val raw = javaClass.classLoader.getResourceAsStream("application.yml")
            ?.bufferedReader()?.readText() ?: error("application.yml not on classpath")
        assertThat(raw).contains("operational-days:")
        assertThat(raw).contains("security-events-days:")
    }

    // == Pseudonymization script: selective ================================

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `pseudonymization replaces target userId leaving others untouched`(@TempDir tempDir: Path) {
        val targetId = "42"
        val otherId = "99"
        createSampleLogs(tempDir, targetId, otherId)

        val result = runScript(resolveScript(), targetId, tempDir)
        assertThat(result.exitCode)
            .describedAs("Script exit code (stderr: %s)", result.stderr)
            .isEqualTo(0)

        val content = readAllLogs(tempDir)
        assertThat(content).contains("\"userId\":\"$otherId\"")
        assertThat(content).contains("[userId:$otherId]")
    }

    // == Pseudonymization script: completeness =============================

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `pseudonymization removes all occurrences of target userId`(@TempDir tempDir: Path) {
        val targetId = "42"
        createSampleLogs(tempDir, targetId, "99")

        val result = runScript(resolveScript(), targetId, tempDir)
        assertThat(result.exitCode).isEqualTo(0)
        assertThat(result.stdout).contains("SUCCESS")

        val content = readAllLogs(tempDir)
        assertThat(content)
            .doesNotContain("\"userId\":\"$targetId\"")
            .doesNotContain("\"userId\":$targetId,")
            .doesNotContain("[userId:$targetId]")
    }

    // == Pseudonymization script: deterministic pseudonym ===================

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `pseudonymized value uses deterministic USER_DELETED prefix`(@TempDir tempDir: Path) {
        val targetId = "42"
        createSampleLogs(tempDir, targetId, "99")

        runScript(resolveScript(), targetId, tempDir)

        val content = readAllLogs(tempDir)
        assertThat(content).contains("USER_DELETED_")

        val pseudonyms = Regex("USER_DELETED_[a-f0-9]{12}").findAll(content)
            .map { it.value }.toSet()
        assertThat(pseudonyms)
            .describedAs("All pseudonyms for the same userId must be identical")
            .hasSize(1)
    }

    // == ADR-021 §7 documentation ==========================================

    @Test
    fun `ADR-021 documents 30d operational and 365d security retention`() {
        val adrPath = repoRoot.resolve(
            "design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md")
        assertThat(adrPath).exists()

        val content = Files.readString(adrPath)
        assertThat(content).contains("30")
        assertThat(content).contains("365")
        assertThat(content).containsIgnoringCase("retention")
        assertThat(content).containsIgnoringCase("GDPR")
    }

    @Test
    fun `ADR-021 documents pseudonymization procedure`() {
        val adrPath = repoRoot.resolve(
            "design_&_architecture/decisions/ADR-021-structured-logging-pii-redaction.md")
        val content = Files.readString(adrPath)

        assertThat(content).containsIgnoringCase("pseudon")
    }
}
