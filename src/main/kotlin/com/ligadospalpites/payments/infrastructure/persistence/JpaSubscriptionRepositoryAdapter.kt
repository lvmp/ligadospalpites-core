package com.ligadospalpites.payments.infrastructure.persistence

import com.ligadospalpites.payments.domain.models.Subscription
import com.ligadospalpites.payments.domain.ports.SubscriptionRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JpaSubscriptionRepositoryAdapter(
    private val springDataRepository: SpringDataSubscriptionRepository
) : SubscriptionRepository {

    override fun findByTransactionId(transactionId: String): Subscription? {
        return springDataRepository.findByTransactionId(transactionId)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findByUserId(userId: UUID): List<Subscription> {
        return springDataRepository.findByUserId(userId).map { it.toDomain() }
    }

    override fun save(subscription: Subscription): Subscription {
        val entity = SubscriptionJpaEntity.fromDomain(subscription)
        return springDataRepository.save(entity).toDomain()
    }
}
