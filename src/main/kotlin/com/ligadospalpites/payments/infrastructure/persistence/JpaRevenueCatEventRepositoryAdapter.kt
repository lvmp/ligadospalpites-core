package com.ligadospalpites.payments.infrastructure.persistence

import com.ligadospalpites.payments.domain.models.RevenueCatEvent
import com.ligadospalpites.payments.domain.ports.RevenueCatEventRepository
import org.springframework.stereotype.Repository

@Repository
class JpaRevenueCatEventRepositoryAdapter(
    private val springDataRepository: SpringDataRevenueCatEventRepository
) : RevenueCatEventRepository {

    override fun existsById(id: String): Boolean {
        return springDataRepository.existsById(id)
    }

    override fun save(event: RevenueCatEvent): RevenueCatEvent {
        val entity = RevenueCatEventJpaEntity.fromDomain(event)
        return springDataRepository.save(entity).toDomain()
    }
}
