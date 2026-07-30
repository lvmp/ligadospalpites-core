package com.ligadospalpites.admin.infrastructure.web

import com.ligadospalpites.admin.application.usecases.ForceSyncLeagueUseCase
import com.ligadospalpites.admin.application.usecases.GetAdminLeagueStatsUseCase
import com.ligadospalpites.admin.application.usecases.UpdateLeagueStatusUseCase
import com.ligadospalpites.admin.domain.models.AdminLeagueStats
import com.ligadospalpites.admin.infrastructure.web.dtos.UpdateLeagueStatusRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/leagues", "/api/v1/workspace/leagues")
class AdminLeagueController(
    private val getAdminLeagueStatsUseCase: GetAdminLeagueStatsUseCase,
    private val updateLeagueStatusUseCase: UpdateLeagueStatusUseCase,
    private val forceSyncLeagueUseCase: ForceSyncLeagueUseCase
) {

    @GetMapping("/stats")
    fun getStats(): ResponseEntity<AdminLeagueStats> {
        val stats = getAdminLeagueStatsUseCase()
        return ResponseEntity.ok(stats)
    }

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateLeagueStatusRequest,
        @RequestHeader("X-User-Id", required = false, defaultValue = "admin-master") operatorId: String
    ): ResponseEntity<Map<String, Any>> {
        val isActive = request.status.equals("ACTIVE", ignoreCase = true)
        val success = updateLeagueStatusUseCase(id, isActive, operatorId)
        if (!success) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(mapOf("success" to true, "status" to if (isActive) "ACTIVE" else "INACTIVE"))
    }

    @PostMapping("/{id}/force-sync")
    fun forceSync(
        @PathVariable id: UUID,
        @RequestHeader("X-User-Id", required = false, defaultValue = "admin-master") operatorId: String
    ): ResponseEntity<Map<String, Any>> {
        val success = forceSyncLeagueUseCase(id, operatorId)
        return ResponseEntity.ok(mapOf("success" to success, "message" to "Force sync initiated for league $id"))
    }
}
