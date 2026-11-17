package com.ligadospalpites.payments.domain.models

import java.time.Instant

data class RevenueCatEvent(
    val id: String,
    val type: String,
    val appUserId: String,
    val productId: String,
    val payload: String,
    val status: String = "RECEIVED",
    val failureReason: String? = null,
    val createdAt: Instant = Instant.now()
)
