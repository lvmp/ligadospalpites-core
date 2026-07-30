package com.ligadospalpites.admin.domain.ports

import com.ligadospalpites.admin.domain.models.AuditLog

interface AuditLogRepository {
    fun save(log: AuditLog): AuditLog
    fun findAll(page: Int, limit: Int): List<AuditLog>
}
