package com.valueinvesting.webapp.api

import com.valueinvesting.webapp.service.LlmBudgetConfigService
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.net.URI

@RestController
@RequestMapping("/admin/llm-cost")
@PreAuthorize("hasRole('ADMIN')")
class LlmBudgetAdminController(
    private val budgetService: LlmBudgetConfigService,
) {

    @GetMapping
    fun getCostStatus(): ResponseEntity<LlmCostStatusResponse> {
        val response = LlmCostStatusResponse(
            utilization = budgetService.getUtilizationPercent(),
            monthlyCapUsd = budgetService.getMonthlyCapUsd(),
            totalCostUsd = budgetService.getCurrentMonthCost(),
            frozen = budgetService.frozen,
        )
        return ResponseEntity.ok(response)
    }

    @PostMapping("/freeze")
    fun freeze(): ResponseEntity<Void> {
        budgetService.freeze()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/unfreeze")
    fun unfreeze(): ResponseEntity<Void> {
        budgetService.unfreeze()
        return ResponseEntity.ok().build()
    }

    @PutMapping("/budget")
    fun updateBudget(
        @Valid @RequestBody request: UpdateBudgetRequest,
        @AuthenticationPrincipal principal: UserDetails?,
    ): ResponseEntity<Any> {
        val cap = request.monthlyCapUsd
        if (cap <= BigDecimal.ZERO || cap > BigDecimal("10000")) {
            val problem = ProblemDetail.forStatus(400)
            problem.type = URI.create("urn:vi:error:BUDGET_CAP_INVALID")
            problem.title = "Budget cap invalid"
            problem.detail = "monthlyCapUsd must be > 0 and <= 10000, got: $cap"
            return ResponseEntity.badRequest().body(problem)
        }

        val newCap = budgetService.updateBudget(cap, null, request.reason)
        return ResponseEntity.ok(mapOf("monthlyCapUsd" to newCap))
    }
}

data class LlmCostStatusResponse(
    val utilization: Double,
    val monthlyCapUsd: BigDecimal,
    val totalCostUsd: BigDecimal,
    val frozen: Boolean,
)

data class UpdateBudgetRequest(
    @field:NotNull
    @field:DecimalMin("0.01")
    @field:DecimalMax("10000")
    val monthlyCapUsd: BigDecimal,
    val reason: String? = null,
)
