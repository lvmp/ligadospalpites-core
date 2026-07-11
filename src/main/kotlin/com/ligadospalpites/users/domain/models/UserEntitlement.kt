package com.ligadospalpites.users.domain.models

import java.time.Instant
import java.util.UUID

enum class EntitlementType {
    PREMIUM,
    SPORT_PASS
}

data class UserEntitlement(
    val id: UUID,
    val userId: UUID,
    val entitlementType: EntitlementType,
    val sportId: UUID?, // Null if PREMIUM (which unlocks all sports)
    val expiresAt: Instant?,
    val createdAt: Instant = Instant.now()
) {
    fun isExpired(): Boolean {
        return expiresAt != null && expiresAt.isBefore(Instant.now())
    }
}
