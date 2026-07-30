package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.AuditLog
import com.ligadospalpites.admin.domain.ports.AuditLogRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ForceSyncLeagueUseCase(
    private val auditLogRepository: AuditLogRepository
) {
    operator fun invoke(leagueId: UUID, operatorId: String = "admin-master"): Boolean {
        auditLogRepository.save(
            AuditLog(
                operatorId = operatorId,
                action = "FORCE_SYNC_LEAGUE",
                targetId = leagueId.toString(),
                details = "Sincronização forçada solicitada para a liga"
            )
        )
        return true
    }
}
