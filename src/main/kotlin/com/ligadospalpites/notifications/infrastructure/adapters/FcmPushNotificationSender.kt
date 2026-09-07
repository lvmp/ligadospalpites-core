package com.ligadospalpites.notifications.infrastructure.adapters

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.ligadospalpites.notifications.domain.models.Notification
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.RecipientContactInfo
import com.ligadospalpites.notifications.domain.ports.NotificationSender
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

data class DeviceTokenExpiredEvent(val fcmToken: String)

@Component
class FcmPushNotificationSender(
    private val firebaseMessaging: FirebaseMessaging?,
    private val eventPublisher: ApplicationEventPublisher
) : NotificationSender {

    private val log = LoggerFactory.getLogger(FcmPushNotificationSender::class.java)

    override fun supports(channel: NotificationChannel) = channel == NotificationChannel.PUSH

    override fun send(notification: Notification, recipient: RecipientContactInfo) {
        val dataMap = mutableMapOf<String, String>()
        dataMap.putAll(notification.metadata)
        if (!dataMap.containsKey("click_action")) {
            dataMap["click_action"] = "FLUTTER_NOTIFICATION_CLICK"
        }

        recipient.activeFcmTokens.forEach { token ->
            if (firebaseMessaging == null) {
                log.info("FCM Messaging disabled. Simulating push notification to token '{}': [{}] {} Data: {}", token, notification.title, notification.content, dataMap)
                return@forEach
            }

            try {
                val message = Message.builder()
                    .setToken(token)
                    .setNotification(
                        com.google.firebase.messaging.Notification.builder()
                            .setTitle(notification.title)
                            .setBody(notification.content)
                            .build()
                    )
                    .putAllData(dataMap)
                    .build()
                firebaseMessaging.send(message)
                log.debug("Push notification successfully sent to token '{}' with data {}", token, dataMap)
            } catch (ex: FirebaseMessagingException) {
                log.warn("Failed to send FCM push to token '{}': {}", token, ex.message)
                if (ex.messagingErrorCode == MessagingErrorCode.UNREGISTERED ||
                    ex.messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    log.info("Token '{}' is unregistered/invalid. Publishing expired token event.", token)
                    eventPublisher.publishEvent(DeviceTokenExpiredEvent(token))
                }
            }
        }
    }
}
