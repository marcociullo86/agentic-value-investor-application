package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.contract.OpenApiContractSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

// Contract drift test per lo schema SummaryVerdictResponse + enum tipati (TSK-341 / US-103 AC).
//
// Verifica che il canonical openapi.yaml dichiara:
//   - Il path /api/analysis/{ticker}/summary (GET).
//   - Lo schema SummaryVerdictResponse con i campi obbligatori.
//   - Gli enum tipati: SummaryVerdict, ViVerdict, DeepAnalysisStatus, DeepVerdict.
//   - Lo schema SummaryRationale.
//   - Lo schema WikiCitation.
//
// Approccio: YAML parsing puro (OpenApiContractSupport), nessun Spring context.
// Stile identico a TechnicalAnalysisOpenApiContractTest (TSK-327 / US-098).
// Il test full-stack (runtime drift) è delegato a OpenApiContractIT che carica
// l'intera applicazione con Testcontainers.
//
// [^src: management/kanban/EP-024-.../US-103-.../TSK-341.md §"Contract drift"]
// [^src: design_&_architecture/api/openapi.yaml §EP-024-Fase-2]
// [^src: management/kanban/EP-024-.../US-103-.../US-103.md §"Schema OpenAPI aggiornato"]
class SummaryOpenApiContractTest {

    // Path al canonical openapi.yaml. Stesso pattern di TechnicalAnalysisOpenApiContractTest:
    // Gradle cwd = src/backend; IntelliJ cwd = project root.
    private val canonicalPath: Path = Path.of(
        System.getProperty("user.dir")
            .let {
                val gradlePath = "$it/../../design_&_architecture/api/openapi.yaml"
                val intellijPath = "$it/design_&_architecture/api/openapi.yaml"
                if (java.io.File(gradlePath).exists()) gradlePath else intellijPath
            },
    )

    @Test
    fun `canonical openapi yaml declares GET endpoint for summary`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val paths = OpenApiContractSupport.pathOperations(doc.get("paths"))

        assertThat(paths.keys)
            .withFailMessage {
                "Path /api/analysis/{ticker}/summary non trovato nel canonical openapi.yaml. " +
                    "Paths disponibili: ${paths.keys.sorted()}"
            }
            .contains("/api/analysis/{ticker}/summary")

        val ops = paths["/api/analysis/{ticker}/summary"]
        assertThat(ops?.keys)
            .withFailMessage("Operazione GET assente per /api/analysis/{ticker}/summary")
            .contains("get")
    }

    @Test
    fun `canonical openapi yaml declares SummaryVerdictResponse schema`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val schemaNames = OpenApiContractSupport.schemaNames(doc.get("components"))

        assertThat(schemaNames)
            .withFailMessage("Schema SummaryVerdictResponse assente nel canonical openapi.yaml")
            .contains("SummaryVerdictResponse")
    }

    @Test
    fun `canonical openapi yaml declares all Summary typed enums`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val schemaNames = OpenApiContractSupport.schemaNames(doc.get("components"))

        // Enum tipati obbligatori (US-103 AC: "Schema OpenAPI aggiornato con enum tipati")
        val requiredEnums = setOf(
            "SummaryVerdict",     // ENTER_NOW / WAIT_FOR_SETUP / AVOID / INSUFFICIENT_DATA
            "ViVerdict",          // GREEN_DOMINANT / YELLOW_DOMINANT / RED_DOMINANT / INDETERMINATE_DOMINANT
            "DeepAnalysisStatus", // AVAILABLE / NOT_INDEXED / NOT_AVAILABLE
            "DeepVerdict",        // OK / WATCHLIST / RISCHIO_ESTREMO
        )
        assertThat(schemaNames)
            .withFailMessage {
                "Enum tipati Summary assenti: ${requiredEnums - schemaNames}. " +
                    "Presenti: $schemaNames"
            }
            .containsAll(requiredEnums)
    }

    @Test
    fun `canonical openapi yaml declares SummaryRationale and WikiCitation schemas`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val schemaNames = OpenApiContractSupport.schemaNames(doc.get("components"))

        val requiredSchemas = setOf("SummaryRationale", "WikiCitation")
        assertThat(schemaNames)
            .withFailMessage {
                "Schema Summary ausiliari assenti: ${requiredSchemas - schemaNames}"
            }
            .containsAll(requiredSchemas)
    }

    @Test
    fun `SummaryVerdict enum values match Kotlin enum — no drift`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val components = doc.get("components")

        // Naviga lo schema SummaryVerdict → campo 'enum'
        val summaryVerdictEnum = components
            ?.get("schemas")
            ?.get("SummaryVerdict")
            ?.get("enum")

        assertThat(summaryVerdictEnum).isNotNull
        val enumValues = (0 until (summaryVerdictEnum!!.size())).map { summaryVerdictEnum.get(it).asText() }.toSet()

        // Confronta con i valori dell'enum Kotlin — nessun drift
        val kotlinValues = SummaryVerdict.entries.map { it.name }.toSet()

        assertThat(enumValues)
            .withFailMessage {
                "SummaryVerdict enum drift! " +
                    "YAML: $enumValues, Kotlin: $kotlinValues"
            }
            .isEqualTo(kotlinValues)
    }

    @Test
    fun `ViVerdict enum values match Kotlin enum — no drift`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val components = doc.get("components")

        val viVerdictEnum = components
            ?.get("schemas")
            ?.get("ViVerdict")
            ?.get("enum")

        assertThat(viVerdictEnum).isNotNull
        val enumValues = (0 until (viVerdictEnum!!.size())).map { viVerdictEnum.get(it).asText() }.toSet()
        val kotlinValues = ViVerdict.entries.map { it.name }.toSet()

        assertThat(enumValues)
            .withFailMessage("ViVerdict enum drift! YAML: $enumValues, Kotlin: $kotlinValues")
            .isEqualTo(kotlinValues)
    }

    @Test
    fun `DeepVerdict enum values match Kotlin enum — no drift`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val components = doc.get("components")

        val deepVerdictEnum = components
            ?.get("schemas")
            ?.get("DeepVerdict")
            ?.get("enum")

        assertThat(deepVerdictEnum).isNotNull
        val enumValues = (0 until (deepVerdictEnum!!.size())).map { deepVerdictEnum.get(it).asText() }.toSet()
        val kotlinValues = DeepVerdict.entries.map { it.name }.toSet()

        assertThat(enumValues)
            .withFailMessage("DeepVerdict enum drift! YAML: $enumValues, Kotlin: $kotlinValues")
            .isEqualTo(kotlinValues)
    }

    @Test
    fun `DeepAnalysisStatus enum values match Kotlin enum — no drift`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val components = doc.get("components")

        val statusEnum = components
            ?.get("schemas")
            ?.get("DeepAnalysisStatus")
            ?.get("enum")

        assertThat(statusEnum).isNotNull
        val enumValues = (0 until (statusEnum!!.size())).map { statusEnum.get(it).asText() }.toSet()
        val kotlinValues = DeepAnalysisStatus.entries.map { it.name }.toSet()

        assertThat(enumValues)
            .withFailMessage("DeepAnalysisStatus enum drift! YAML: $enumValues, Kotlin: $kotlinValues")
            .isEqualTo(kotlinValues)
    }
}
