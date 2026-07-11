package com.ligadospalpites.users.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataUserEntitlementRepository : JpaRepository<UserEntitlementJpaEntity, UUID> {
    fun findByUserId(userId: UUID): List<UserEntitlementJpaEntity>
}
