package com.ligadospalpites.notifications.infrastructure.adapters

import com.ligadospalpites.notifications.domain.models.Notification
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.RecipientContactInfo
import com.ligadospalpites.notifications.domain.ports.InAppNotificationRepository
import com.ligadospalpites.notifications.domain.ports.NotificationSender
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class InAppNotificationSender(
    private val inAppRepository: InAppNotificationRepository
) : NotificationSender {
    override fun supports(channel: NotificationChannel) = channel == NotificationChannel.IN_APP

    override fun send(notification: Notification, recipient: RecipientContactInfo) {
        inAppRepository.save(
            userId = notification.recipientUserId,
            title = notification.title,
            content = notification.content
        )
    }
}
