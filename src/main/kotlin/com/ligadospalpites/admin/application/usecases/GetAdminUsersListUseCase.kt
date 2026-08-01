package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import com.ligadospalpites.admin.infrastructure.web.dtos.AdminUsersPageResponse
import org.springframework.stereotype.Service

@Service
class GetAdminUsersListUseCase(
    private val adminStatsRepository: AdminStatsRepository
) {
    operator fun invoke(
        query: String? = null,
        name: String? = null,
        email: String? = null,
        id: String? = null,
        page: Int = 0,
        size: Int = 50
    ): AdminUsersPageResponse {
        return adminStatsRepository.getUsers(
            query = query,
            name = name,
            email = email,
            id = id,
            page = page,
            size = size
        )
    }
}
