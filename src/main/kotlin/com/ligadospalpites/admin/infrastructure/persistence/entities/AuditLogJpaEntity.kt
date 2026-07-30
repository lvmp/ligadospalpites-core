package com.ligadospalpites.admin.infrastructure.persistence.entities

import com.ligadospalpites.admin.domain.models.AuditLog
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_admin_audit_logs")
class AuditLogJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "operator_id", nullable = false, length = 128)
    val operatorId: String = "system",

    @Column(name = "action", nullable = false, length = 100)
    val action: String = "",

    @Column(name = "target_id", length = 128)
    val targetId: String? = null,

    @Column(name = "details", columnDefinition = "TEXT")
    val details: String? = null,

    @Column(name = "timestamp", nullable = false, updatable = false)
    val timestamp: Instant = Instant.now()
) {
    fun toDomain(): AuditLog = AuditLog(
        id = id,
        operatorId = operatorId,
        action = action,
        targetId = targetId,
        details = details,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(domain: AuditLog): AuditLogJpaEntity = AuditLogJpaEntity(
            id = domain.id,
            operatorId = domain.operatorId,
            action = domain.action,
            targetId = domain.targetId,
            details = domain.details,
            timestamp = domain.timestamp
        )
    }
}
