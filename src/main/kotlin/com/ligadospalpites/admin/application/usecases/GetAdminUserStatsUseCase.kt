package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.AdminUserStats
import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import org.springframework.stereotype.Service

@Service
class GetAdminUserStatsUseCase(
    private val adminStatsRepository: AdminStatsRepository
) {
    operator fun invoke(): AdminUserStats {
        return adminStatsRepository.getUserStats()
    }
}
