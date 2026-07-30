package com.ligadospalpites.admin.infrastructure.web

import com.ligadospalpites.admin.application.usecases.GetAdminUserStatsUseCase
import com.ligadospalpites.admin.application.usecases.GrantUserPlanUseCase
import com.ligadospalpites.admin.domain.models.AdminUserStats
import com.ligadospalpites.admin.infrastructure.web.dtos.GrantUserPlanRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/users", "/api/v1/workspace/users")
class AdminUserController(
    private val getAdminUserStatsUseCase: GetAdminUserStatsUseCase,
    private val grantUserPlanUseCase: GrantUserPlanUseCase
) {

    @GetMapping("/stats")
    fun getStats(): ResponseEntity<AdminUserStats> {
        val stats = getAdminUserStatsUseCase()
        return ResponseEntity.ok(stats)
    }

    @PostMapping("/{id}/grant-plan")
    fun grantPlan(
        @PathVariable id: UUID,
        @RequestBody @Valid request: GrantUserPlanRequest,
        @RequestHeader("X-User-Id", required = false, defaultValue = "admin-master") operatorId: String
    ): ResponseEntity<Map<String, Any>> {
        val success = grantUserPlanUseCase(
            userId = id,
            plan = request.plan,
            durationDays = request.durationDays,
            reason = request.reason,
            operatorId = operatorId
        )
        if (!success) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(mapOf("success" to true, "message" to "Plan ${request.plan} granted to user $id for ${request.durationDays} days"))
    }
}
