package com.ligadospalpites.admin.infrastructure.persistence.adapters

import com.ligadospalpites.admin.domain.models.AuditLog
import com.ligadospalpites.admin.domain.ports.AuditLogRepository
import com.ligadospalpites.admin.infrastructure.persistence.entities.AuditLogJpaEntity
import com.ligadospalpites.admin.infrastructure.persistence.repositories.SpringDataAuditLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class AuditLogRepositoryImpl(
    private val springDataAuditLogRepository: SpringDataAuditLogRepository
) : AuditLogRepository {

    override fun save(log: AuditLog): AuditLog {
        val entity = AuditLogJpaEntity.fromDomain(log)
        return springDataAuditLogRepository.save(entity).toDomain()
    }

    override fun findAll(page: Int, limit: Int): List<AuditLog> {
        val pageable = PageRequest.of(page, limit)
        return springDataAuditLogRepository.findAllByOrderByTimestampDesc(pageable)
            .map { it.toDomain() }
    }
}
