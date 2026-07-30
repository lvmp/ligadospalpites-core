package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.AdminLeagueStats
import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import org.springframework.stereotype.Service

@Service
class GetAdminLeagueStatsUseCase(
    private val adminStatsRepository: AdminStatsRepository
) {
    operator fun invoke(): AdminLeagueStats {
        return adminStatsRepository.getLeagueStats()
    }
}
