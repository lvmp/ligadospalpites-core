package com.ligadospalpites.payments.infrastructure.persistence

import com.ligadospalpites.payments.domain.models.StorePlatform
import com.ligadospalpites.payments.domain.models.Subscription
import com.ligadospalpites.payments.domain.models.SubscriptionStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_subscriptions")
class SubscriptionJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val store: StorePlatform = StorePlatform.GOOGLE_PLAY,

    @Column(name = "transaction_id", nullable = false, unique = true, length = 255)
    val transactionId: String = "",

    @Column(name = "product_id", nullable = false, length = 255)
    val productId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,

    @Column(name = "current_period_end")
    val currentPeriodEnd: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    fun toDomain(): Subscription = Subscription(
        id = id,
        userId = userId,
        store = store,
        transactionId = transactionId,
        productId = productId,
        status = status,
        currentPeriodEnd = currentPeriodEnd,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(domain: Subscription): SubscriptionJpaEntity = SubscriptionJpaEntity(
            id = domain.id,
            userId = domain.userId,
            store = domain.store,
            transactionId = domain.transactionId,
            productId = domain.productId,
            status = domain.status,
            currentPeriodEnd = domain.currentPeriodEnd,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
