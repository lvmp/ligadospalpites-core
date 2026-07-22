package com.ligadospalpites.notifications.domain.models

import java.util.UUID

enum class NotificationChannel {
    IN_APP,
    PUSH,
    EMAIL
}

enum class NotificationTarget {
    USER,
    LEAGUE,
    SPORT,
    ALL
}

data class Notification(
    val id: UUID,
    val recipientUserId: UUID,
    val title: String,
    val content: String
)

data class RecipientContactInfo(
    val email: String?,
    val activeFcmTokens: List<String>
)
