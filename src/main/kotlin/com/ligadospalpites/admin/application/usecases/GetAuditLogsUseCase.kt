package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.AuditLog
import com.ligadospalpites.admin.domain.ports.AuditLogRepository
import org.springframework.stereotype.Service

@Service
class GetAuditLogsUseCase(
    private val auditLogRepository: AuditLogRepository
) {
    operator fun invoke(page: Int = 0, limit: Int = 50): List<AuditLog> {
        return auditLogRepository.findAll(page, limit)
    }
}
