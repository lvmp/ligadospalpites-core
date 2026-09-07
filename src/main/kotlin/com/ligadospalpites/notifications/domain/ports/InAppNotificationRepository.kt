package com.ligadospalpites.notifications.domain.ports

import com.ligadospalpites.notifications.domain.models.InAppNotification
import java.util.UUID

interface InAppNotificationRepository {
    fun save(userId: UUID, title: String, content: String)
    fun findByUserId(userId: UUID, page: Int, size: Int): List<InAppNotification>
    fun countTotalByUserId(userId: UUID): Long
    fun countUnreadByUserId(userId: UUID): Long
    fun markAsRead(id: UUID, userId: UUID): Boolean
    fun markAllAsRead(userId: UUID)
}
