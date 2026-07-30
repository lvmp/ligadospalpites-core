package com.ligadospalpites.admin.infrastructure.persistence.repositories

import com.ligadospalpites.admin.infrastructure.persistence.entities.AuditLogJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataAuditLogRepository : JpaRepository<AuditLogJpaEntity, UUID> {
    fun findAllByOrderByTimestampDesc(pageable: Pageable): List<AuditLogJpaEntity>
}
