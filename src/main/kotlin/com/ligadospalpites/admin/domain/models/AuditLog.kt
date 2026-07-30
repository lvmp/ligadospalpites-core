package com.ligadospalpites.admin.domain.models

import java.time.Instant
import java.util.UUID

data class AuditLog(
    val id: UUID = UUID.randomUUID(),
    val operatorId: String,
    val action: String,
    val targetId: String?,
    val details: String?,
    val timestamp: Instant = Instant.now()
)
