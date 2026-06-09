package com.valueinvesting.webapp.backtest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

// Contract drift test (TSK-348 / TSK-349): verifica che il canonical
// openapi.yaml contenga schemi/enum/path richiesti per il backtest.
//
// Non un full springdoc-vs-canonical (gia' coperto da OpenApiContractIT) — qui
// controlliamo che le definizioni canonical siano CORRETTE e non regrediscano
// nel tempo (le @Schema name lato Kotlin matchino i nomi canonical).
//
// [^src: management/kanban/EP-024-.../US-105-.../TSK-348.md §"OpenAPI"]
// [^src: management/kanban/EP-024-.../US-105-.../TSK-349.md §"Contract drift"]
class BacktestOpenApiContractTest {

    private val mapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    private val canonical: com.fasterxml.jackson.databind.JsonNode by lazy {
        // Risolto rispetto alla CWD: in Gradle e' il modulo `src/backend`, in
        // IntelliJ puo' essere la project root. Lo stesso pattern di
        // TechnicalAnalysisOpenApiContractTest (TSK-327).
        val resolved = System.getProperty("user.dir").let { cwd ->
            val gradlePath = "$cwd/../../design_&_architecture/api/openapi.yaml"
            val intellijPath = "$cwd/design_&_architecture/api/openapi.yaml"
            val candidates = listOf(gradlePath, intellijPath)
            candidates.map { Path.of(it) }
                .firstOrNull { it.toFile().exists() }
                ?: error("openapi.yaml not found; checked: $candidates")
        }
        mapper.readTree(resolved.toFile())
    }

    @Test
    fun `path GET backtest is declared in canonical openapi`() {
        val path = canonical.path("paths").path("/api/analysis/{ticker}/backtest")
        assertThat(path.isMissingNode).isFalse
        assertThat(path.path("get").isMissingNode).isFalse
    }

    @Test
    fun `BacktestStatus enum has exactly OK and INSUFFICIENT_HISTORY`() {
        val values = canonical.path("components").path("schemas").path("BacktestStatus")
            .path("enum")
            .map { it.asText() }
            .toSet()
        assertThat(values).containsExactlyInAnyOrder("OK", "INSUFFICIENT_HISTORY")
    }

    @Test
    fun `BacktestStrategy enum has the 3 expected values`() {
        val values = canonical.path("components").path("schemas").path("BacktestStrategy")
            .path("enum")
            .map { it.asText() }
            .toSet()
        assertThat(values).containsExactlyInAnyOrder(
            "EP024_ENTER_NOW",
            "VI_ONLY",
            "BUY_AND_HOLD",
        )
    }

    @Test
    fun `BacktestExitReason enum has VI_TARGET STOP_HIT HORIZON`() {
        val values = canonical.path("components").path("schemas").path("BacktestExitReason")
            .path("enum")
            .map { it.asText() }
            .toSet()
        assertThat(values).containsExactlyInAnyOrder("VI_TARGET", "STOP_HIT", "HORIZON")
    }

    @Test
    fun `BacktestTimingEdgeLabel enum has POSITIVE NEUTRAL NEGATIVE`() {
        val values = canonical.path("components").path("schemas").path("BacktestTimingEdgeLabel")
            .path("enum")
            .map { it.asText() }
            .toSet()
        assertThat(values).containsExactlyInAnyOrder(
            "POSITIVE_EDGE",
            "NEUTRAL",
            "NEGATIVE_EDGE",
        )
    }

    @Test
    fun `BacktestResponse schema requires ticker evaluatedAt status caveats`() {
        val required = canonical.path("components").path("schemas").path("BacktestResponse")
            .path("required")
            .map { it.asText() }
            .toSet()
        assertThat(required).contains("ticker", "evaluatedAt", "status", "caveats")
    }

    @Test
    fun `BacktestCaveats exposes lookAheadResidual singleTicker notPortfolioPerformance`() {
        val props = canonical.path("components").path("schemas").path("BacktestCaveats")
            .path("properties")
        assertThat(props.path("lookAheadResidual").isMissingNode).isFalse
        assertThat(props.path("singleTicker").isMissingNode).isFalse
        assertThat(props.path("notPortfolioPerformance").isMissingNode).isFalse
    }

    @Test
    fun `BacktestStrategyMetrics exposes the canonical aggregate fields`() {
        val props = canonical.path("components").path("schemas").path("BacktestStrategyMetrics")
            .path("properties")
        listOf(
            "strategy", "trades", "winRate", "avgReturnPct", "medianReturnPct",
            "avgHoldingDays", "avgRealizedRewardRisk", "totalReturnPct",
            "maxTradeDrawdownPct", "exitBreakdown", "noSignalsInPeriod",
        ).forEach { name ->
            assertThat(props.path(name).isMissingNode)
                .withFailMessage("BacktestStrategyMetrics is missing canonical property: $name")
                .isFalse
        }
    }

    @Test
    fun `BacktestTrade exposes the canonical trade fields`() {
        val props = canonical.path("components").path("schemas").path("BacktestTrade")
            .path("properties")
        listOf(
            "strategy", "entryDate", "entryPrice", "exitDate", "exitPrice",
            "exitReason", "returnPct", "holdingDays", "maxIntraTradeDrawdownPct",
        ).forEach { name ->
            assertThat(props.path(name).isMissingNode)
                .withFailMessage("BacktestTrade is missing canonical property: $name")
                .isFalse
        }
    }
}
