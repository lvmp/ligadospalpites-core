package com.ligadospalpites.notifications.infrastructure.adapters

import com.ligadospalpites.notifications.domain.models.Notification
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.RecipientContactInfo
import com.ligadospalpites.notifications.domain.ports.NotificationSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

@Component
class SmtpEmailNotificationSender(
    private val mailSender: JavaMailSender
) : NotificationSender {
    override fun supports(channel: NotificationChannel) = channel == NotificationChannel.EMAIL

    override fun send(notification: Notification, recipient: RecipientContactInfo) {
        if (recipient.email.isNullOrBlank()) return

        val message = SimpleMailMessage().apply {
            setTo(recipient.email)
            setSubject(notification.title)
            setText(notification.content)
        }
        mailSender.send(message)
    }
}
