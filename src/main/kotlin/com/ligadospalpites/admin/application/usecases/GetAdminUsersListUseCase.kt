package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import com.ligadospalpites.admin.infrastructure.web.dtos.AdminUsersPageResponse
import org.springframework.stereotype.Service

@Service
class GetAdminUsersListUseCase(
    private val adminStatsRepository: AdminStatsRepository
) {
    operator fun invoke(page: Int = 0, size: Int = 50): AdminUsersPageResponse {
        return adminStatsRepository.getUsers(page, size)
    }
}
