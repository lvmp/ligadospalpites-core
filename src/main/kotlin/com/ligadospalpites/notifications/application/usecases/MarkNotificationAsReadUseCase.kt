package com.ligadospalpites.notifications.application.usecases

import com.ligadospalpites.notifications.domain.ports.InAppNotificationRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MarkNotificationAsReadUseCase(
    private val inAppNotificationRepository: InAppNotificationRepository
) {
    fun markSingle(notificationId: UUID, userId: UUID): Boolean {
        return inAppNotificationRepository.markAsRead(notificationId, userId)
    }

    fun markAll(userId: UUID) {
        inAppNotificationRepository.markAllAsRead(userId)
    }
}
