package com.ligadospalpites.payments.domain.ports

import com.ligadospalpites.payments.domain.models.Subscription
import java.util.UUID

interface SubscriptionRepository {
    fun findByTransactionId(transactionId: String): Subscription?
    fun findByUserId(userId: UUID): List<Subscription>
    fun save(subscription: Subscription): Subscription
}
