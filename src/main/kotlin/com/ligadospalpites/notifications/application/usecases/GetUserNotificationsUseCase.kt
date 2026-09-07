package com.ligadospalpites.notifications.application.usecases

import com.ligadospalpites.notifications.domain.models.InAppNotification
import com.ligadospalpites.notifications.domain.ports.InAppNotificationRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetUserNotificationsUseCase(
    private val inAppNotificationRepository: InAppNotificationRepository
) {
    data class Result(
        val content: List<InAppNotification>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val unreadCount: Long,
        val totalPages: Int,
        val hasNext: Boolean
    )

    fun execute(userId: UUID, page: Int = 0, size: Int = 20): Result {
        val safePage = if (page < 0) 0 else page
        val safeSize = if (size <= 0) 20 else size

        val notifications = inAppNotificationRepository.findByUserId(userId, safePage, safeSize)
        val totalElements = inAppNotificationRepository.countTotalByUserId(userId)
        val unreadCount = inAppNotificationRepository.countUnreadByUserId(userId)

        val totalPages = if (safeSize > 0) Math.ceil(totalElements.toDouble() / safeSize).toInt() else 0
        val hasNext = (safePage + 1) < totalPages

        return Result(
            content = notifications,
            page = safePage,
            size = safeSize,
            totalElements = totalElements,
            unreadCount = unreadCount,
            totalPages = totalPages,
            hasNext = hasNext
        )
    }
}
