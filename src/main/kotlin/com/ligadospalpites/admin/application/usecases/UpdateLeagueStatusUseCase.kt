package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.AuditLog
import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import com.ligadospalpites.admin.domain.ports.AuditLogRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UpdateLeagueStatusUseCase(
    private val adminStatsRepository: AdminStatsRepository,
    private val auditLogRepository: AuditLogRepository
) {
    operator fun invoke(leagueId: UUID, isActive: Boolean, operatorId: String = "admin-master"): Boolean {
        val success = adminStatsRepository.updateLeagueStatus(leagueId, isActive)
        if (success) {
            auditLogRepository.save(
                AuditLog(
                    operatorId = operatorId,
                    action = "UPDATE_LEAGUE_STATUS",
                    targetId = leagueId.toString(),
                    details = "Status da liga alterado para: ${if (isActive) "ACTIVE" else "INACTIVE"}"
                )
            )
        }
        return success
    }
}
