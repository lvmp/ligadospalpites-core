package com.ligadospalpites.payments.domain.models

import java.time.Instant
import java.util.UUID

enum class SubscriptionStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED
}

enum class StorePlatform {
    APPLE_APP_STORE,
    GOOGLE_PLAY,
    STRIPE
}

data class Subscription(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val store: StorePlatform,
    val transactionId: String,
    val productId: String,
    val status: SubscriptionStatus,
    val currentPeriodEnd: Instant?,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)
