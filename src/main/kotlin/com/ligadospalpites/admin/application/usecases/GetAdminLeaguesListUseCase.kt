package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import com.ligadospalpites.admin.infrastructure.web.dtos.AdminLeagueDto
import org.springframework.stereotype.Service

@Service
class GetAdminLeaguesListUseCase(
    private val adminStatsRepository: AdminStatsRepository
) {
    operator fun invoke(): List<AdminLeagueDto> {
        return adminStatsRepository.getAllLeagues()
    }
}
