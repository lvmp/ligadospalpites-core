package com.ligadospalpites.users.domain.ports

import com.ligadospalpites.users.domain.models.UserEntitlement
import java.util.UUID

interface UserEntitlementRepository {
    fun findByUserId(userId: UUID): List<UserEntitlement>
    fun save(entitlement: UserEntitlement): UserEntitlement
}
