package com.ligadospalpites.notifications.domain.models

import java.time.Instant
import java.util.UUID

data class InAppNotification(
    val id: UUID,
    val userId: UUID,
    val title: String,
    val content: String,
    val isRead: Boolean,
    val createdAt: Instant
)
