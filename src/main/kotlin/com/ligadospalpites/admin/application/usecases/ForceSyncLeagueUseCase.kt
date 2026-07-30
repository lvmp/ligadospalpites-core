package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.AuditLog
import com.ligadospalpites.admin.domain.ports.AuditLogRepository
import com.ligadospalpites.sportsfeed.application.usecases.SyncOrchestrator
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ForceSyncLeagueUseCase(
    private val leagueRepository: SpringDataLeagueRepository,
    private val syncOrchestrator: SyncOrchestrator,
    private val auditLogRepository: AuditLogRepository
) {
    operator fun invoke(leagueId: UUID, operatorId: String = "admin-master"): Boolean {
        val league = leagueRepository.findById(leagueId).orElse(null) ?: return false
        
        syncOrchestrator.syncMatches(league.sportId, leagueId)

        auditLogRepository.save(
            AuditLog(
                operatorId = operatorId,
                action = "FORCE_SYNC_LEAGUE",
                targetId = leagueId.toString(),
                details = "Sincronização forçada executada para a liga ${league.name}"
            )
        )
        return true
    }
}
