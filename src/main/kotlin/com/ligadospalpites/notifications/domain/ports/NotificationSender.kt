package com.ligadospalpites.notifications.domain.ports

import com.ligadospalpites.notifications.domain.models.Notification
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.RecipientContactInfo

interface NotificationSender {
    fun supports(channel: NotificationChannel): Boolean
    fun send(notification: Notification, recipient: RecipientContactInfo)
}
