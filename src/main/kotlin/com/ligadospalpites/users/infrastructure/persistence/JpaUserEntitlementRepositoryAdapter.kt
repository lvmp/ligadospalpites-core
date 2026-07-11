package com.ligadospalpites.users.infrastructure.persistence

import com.ligadospalpites.users.domain.models.UserEntitlement
import com.ligadospalpites.users.domain.ports.UserEntitlementRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JpaUserEntitlementRepositoryAdapter(
    private val springDataRepository: SpringDataUserEntitlementRepository
) : UserEntitlementRepository {

    override fun findByUserId(userId: UUID): List<UserEntitlement> {
        return springDataRepository.findByUserId(userId).map { it.toDomain() }
    }

    override fun save(entitlement: UserEntitlement): UserEntitlement {
        val entity = UserEntitlementJpaEntity.fromDomain(entitlement)
        return springDataRepository.save(entity).toDomain()
    }
}
