package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.ConnectorsHealthInfo
import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import org.springframework.stereotype.Service

@Service
class GetConnectorsHealthUseCase(
    private val adminStatsRepository: AdminStatsRepository
) {
    operator fun invoke(): ConnectorsHealthInfo {
        return adminStatsRepository.getConnectorsHealth()
    }
}
