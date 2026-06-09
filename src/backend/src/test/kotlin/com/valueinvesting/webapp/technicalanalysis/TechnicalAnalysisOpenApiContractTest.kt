package com.valueinvesting.webapp.technicalanalysis

import com.valueinvesting.webapp.contract.OpenApiContractSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

// Contract drift test per lo schema TechnicalAnalysisResponse (TSK-327 / US-098 AC).
//
// Verifica che il canonical openapi.yaml dichiara:
//   - il path /api/analysis/{ticker}/technical (GET)
//   - gli schema-name di tutti i 6 blocchi indicatori + 3 advisor TA
//
// Approccio: puro YAML parsing (OpenApiContractSupport), nessun Spring context
// necessario — stile compatibile con OpenApiContractValidatorTest (TSK-037).
// Il test full-stack (runtime drift) e' in OpenApiContractIT che carica l'app
// completa; questo test e' lightweight e determina il contratto a livello di documento.
//
// [^src: management/kanban/EP-024-riepilogo-e-technical-analysis-tab/US-098-pipeline-ta-payload-be/TSK-327.md §"Contract test"]
// [^src: design_&_architecture/api/openapi.yaml §EP-024]
class TechnicalAnalysisOpenApiContractTest {

    // Path al canonical openapi.yaml rispetto alla project root.
    // Stessa logica di OpenApiContractIT che usa @Value("${contract.openapi.canonical}").
    private val canonicalPath: Path = Path.of(
        System.getProperty("user.dir")
            .let {
                // In Gradle il working dir e' il modulo :backend (src/backend).
                // In un context IntelliJ il cwd potrebbe essere la project root.
                // Proviamo prima il path relativo Gradle, poi quello IntelliJ.
                val gradlePath = "$it/../../design_&_architecture/api/openapi.yaml"
                val intellijPath = "$it/design_&_architecture/api/openapi.yaml"
                if (java.io.File(gradlePath).exists()) gradlePath else intellijPath
            },
    )

    @Test
    fun `canonical openapi yaml declares GET endpoint for technical analysis`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val paths = OpenApiContractSupport.pathOperations(doc.get("paths"))

        assertThat(paths.keys)
            .withFailMessage {
                "Path /api/analysis/{ticker}/technical non trovato nel canonical openapi.yaml. " +
                    "Paths disponibili: ${paths.keys.sorted()}"
            }
            .contains("/api/analysis/{ticker}/technical")

        val ops = paths["/api/analysis/{ticker}/technical"]
        assertThat(ops?.keys)
            .withFailMessage("Operazione GET assente per /api/analysis/{ticker}/technical")
            .contains("get")
    }

    @Test
    fun `canonical openapi yaml declares TechnicalAnalysisResponse schema with 6 indicator blocks`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val schemaNames = OpenApiContractSupport.schemaNames(doc.get("components"))

        // Schema principale
        assertThat(schemaNames)
            .withFailMessage("Schema TechnicalAnalysisResponse assente nel canonical openapi.yaml")
            .contains("TechnicalAnalysisResponse")

        // 6 blocchi indicatori (US-098 §"Indicatori in scope")
        val requiredBlocks = setOf(
            "TaTrendBlock",
            "TaMomentumBlock",
            "TaVolatilityBlock",
            "TaVolumeBlock",
            "TaLevelsBlock",
            "TaPriceContextBlock",
        )
        assertThat(schemaNames)
            .withFailMessage {
                "Blocchi indicatori TA assenti: ${requiredBlocks - schemaNames}. " +
                    "Presenti: $schemaNames"
            }
            .containsAll(requiredBlocks)
    }

    @Test
    fun `canonical openapi yaml declares EntryTimingAdvisor and StopAndSizing schemas`() {
        val doc = OpenApiContractSupport.loadCanonicalOpenApi(canonicalPath)
        val schemaNames = OpenApiContractSupport.schemaNames(doc.get("components"))

        // Advisor schema (US-099 + US-100)
        val advisorSchemas = setOf(
            "EntryTimingAdvisor",
            "EntryTimingVerdict",
            "ReentryConditionCode",
            "StopSuggestion",
            "StopType",
            "PositionSizing",
            "TwoPercentRule",
            "SixPercentRule",
            "RewardRiskRatio",
            "RewardRiskLabel",
        )
        assertThat(schemaNames)
            .withFailMessage {
                "Schema advisor TA assenti: ${advisorSchemas - schemaNames}"
            }
            .containsAll(advisorSchemas)
    }
}
