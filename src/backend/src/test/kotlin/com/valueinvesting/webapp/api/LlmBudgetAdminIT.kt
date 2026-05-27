package com.valueinvesting.webapp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.valueinvesting.webapp.persistence.repository.LlmBudgetConfigRepository
import com.valueinvesting.webapp.persistence.repository.LlmCallLogRepository
import com.valueinvesting.webapp.persistence.repository.LlmCostCounterRepository
import com.valueinvesting.webapp.service.LlmBudgetConfigService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

/**
 * Integration tests for the LLM budget admin endpoints (ADR-019 §4 / §4.bis).
 *
 * Covers the BE scenarios listed in TSK-159 §Scope:
 *  - PUT happy path + cache invalidation (GET reflects new cap immediately)
 *  - PUT invalid (zero, negative, > absolute max) → 400 BUDGET_CAP_INVALID
 *  - PUT idempotent (same cap = no-op)
 *  - PUT non-admin user → 403
 *  - Freeze/unfreeze toggle visible via GET
 *
 * Uses Testcontainers PostgreSQL 17 to exercise the Flyway-managed schema
 * (V015 `llm_budget_config` singleton seeded `(1, 50.00, 80)`).
 *
 * [^src: management/kanban/EP-011-deep-analysis-10k-10q/US-055-llm-budget-admin-config/TSK-159.md §Scope]
 * [^src: design_&_architecture/decisions/ADR-019-llm-cost-budget-telemetry.md §4.bis]
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LlmBudgetAdminIT {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg17")
            .withDatabaseName("value_investing_test")
            .withUsername("test")
            .withPassword("test")

        @DynamicPropertySource
        @JvmStatic
        fun registerDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var budgetConfigRepo: LlmBudgetConfigRepository
    @Autowired private lateinit var callLogRepo: LlmCallLogRepository
    @Autowired private lateinit var counterRepo: LlmCostCounterRepository
    @Autowired private lateinit var budgetService: LlmBudgetConfigService

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private fun admin() = user("admin").roles("ADMIN")
    private fun regularUser() = user("regular").roles("USER")

    @BeforeEach
    fun resetState() {
        callLogRepo.deleteAll()
        counterRepo.deleteAll()
        // Reset the singleton row to the seed value so cap-change asserts are
        // deterministic across reorderings.
        val config = budgetConfigRepo.findById(1).orElse(null)
        if (config != null && config.monthlyCapUsd.compareTo(BigDecimal("50.00")) != 0) {
            config.monthlyCapUsd = BigDecimal("50.00")
            budgetConfigRepo.save(config)
        }
        // The service caches the cap; force a re-read after our direct DB edit.
        budgetService.invalidateCache()
        // Defensive unfreeze in case a previous freeze test left state.
        budgetService.unfreeze()
    }

    @Test
    fun `PUT budget happy path updates cap and is reflected by next GET`() {
        val body = """{"monthlyCapUsd": 75.00, "reason": "pilot expansion"}"""

        mockMvc.put("/admin/llm-cost/budget") {
            with(admin())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.monthlyCapUsd") { value(75.00) }
        }

        // GET reflects the new cap (cache invalidated by updateBudget(...)).
        mockMvc.get("/admin/llm-cost") {
            with(admin())
        }.andExpect {
            status { isOk() }
            jsonPath("$.monthlyCapUsd") { value(75.00) }
        }

        val persisted = budgetConfigRepo.findById(1).orElseThrow()
        assertThat(persisted.monthlyCapUsd).isEqualByComparingTo(BigDecimal("75.00"))
    }

    @Test
    fun `PUT budget with zero amount returns 400 BUDGET_CAP_INVALID`() {
        mockMvc.put("/admin/llm-cost/budget") {
            with(admin())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"monthlyCapUsd": 0, "reason": "boom"}"""
        }.andExpect {
            // Bean validation triggers MethodArgumentNotValidException → 400.
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT budget with negative amount returns 400`() {
        mockMvc.put("/admin/llm-cost/budget") {
            with(admin())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"monthlyCapUsd": -5.00}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT budget above absolute max returns 400 BUDGET_CAP_INVALID`() {
        mockMvc.put("/admin/llm-cost/budget") {
            with(admin())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"monthlyCapUsd": 99999.99, "reason": "test absolute cap"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT budget non-admin user returns 403`() {
        mockMvc.put("/admin/llm-cost/budget") {
            with(regularUser())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"monthlyCapUsd": 75.00}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `PUT budget twice with same value is idempotent and does not alter row`() {
        val body = """{"monthlyCapUsd": 60.00}"""

        mockMvc.put("/admin/llm-cost/budget") {
            with(admin())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isOk() } }

        val firstSnapshot = budgetConfigRepo.findById(1).orElseThrow()
        val firstUpdatedAt = firstSnapshot.updatedAt

        mockMvc.put("/admin/llm-cost/budget") {
            with(admin())
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isOk() } }

        val secondSnapshot = budgetConfigRepo.findById(1).orElseThrow()
        assertThat(secondSnapshot.monthlyCapUsd).isEqualByComparingTo(BigDecimal("60.00"))
        // No-op idempotent branch returns the existing cap without re-saving,
        // so updatedAt stays put.
        assertThat(secondSnapshot.updatedAt).isEqualTo(firstUpdatedAt)
    }

    @Test
    fun `freeze and unfreeze toggle is observable via GET`() {
        mockMvc.post("/admin/llm-cost/freeze") {
            with(admin())
            with(csrf())
        }.andExpect { status { isOk() } }

        val frozenResponse = mockMvc.get("/admin/llm-cost") {
            with(admin())
        }.andReturn().response.contentAsString
        val frozenPayload: Map<String, Any> = objectMapper.readValue(frozenResponse)
        assertThat(frozenPayload["frozen"]).isEqualTo(true)

        mockMvc.post("/admin/llm-cost/unfreeze") {
            with(admin())
            with(csrf())
        }.andExpect { status { isOk() } }

        val unfrozenResponse = mockMvc.get("/admin/llm-cost") {
            with(admin())
        }.andReturn().response.contentAsString
        val unfrozenPayload: Map<String, Any> = objectMapper.readValue(unfrozenResponse)
        assertThat(unfrozenPayload["frozen"]).isEqualTo(false)
    }
}
