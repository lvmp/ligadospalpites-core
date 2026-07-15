package com.ligadospalpites.payments.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
import java.util.Optional

interface SpringDataSubscriptionRepository : JpaRepository<SubscriptionJpaEntity, UUID> {
    fun findByUserId(userId: UUID): List<SubscriptionJpaEntity>
    fun findByTransactionId(transactionId: String): Optional<SubscriptionJpaEntity>
}
