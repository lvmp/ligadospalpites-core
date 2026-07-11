package com.ligadospalpites.notifications.domain.ports

import java.util.UUID

interface InAppNotificationRepository {
    fun save(userId: UUID, title: String, content: String)
}
