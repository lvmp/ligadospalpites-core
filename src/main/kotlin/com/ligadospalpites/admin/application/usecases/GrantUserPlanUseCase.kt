package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.AuditLog
import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import com.ligadospalpites.admin.domain.ports.AuditLogRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GrantUserPlanUseCase(
    private val adminStatsRepository: AdminStatsRepository,
    private val auditLogRepository: AuditLogRepository
) {
    operator fun invoke(
        userId: UUID,
        plan: String,
        durationDays: Int,
        reason: String?,
        operatorId: String = "admin-master"
    ): Boolean {
        val success = adminStatsRepository.grantUserPlan(userId, plan, durationDays)
        if (success) {
            auditLogRepository.save(
                AuditLog(
                    operatorId = operatorId,
                    action = "GRANT_USER_PLAN",
                    targetId = userId.toString(),
                    details = "Concedido plano $plan ($durationDays dias). Motivo: ${reason ?: "Nenhum"}"
                )
            )
        }
        return success
    }
}
