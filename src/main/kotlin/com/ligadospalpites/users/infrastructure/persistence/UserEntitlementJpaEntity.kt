package com.ligadospalpites.users.infrastructure.persistence

import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.domain.models.UserEntitlement
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_user_entitlements")
class UserEntitlementJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_type", nullable = false, length = 50)
    val entitlementType: EntitlementType = EntitlementType.SPORT_PASS,

    @Column(name = "sport_id")
    val sportId: UUID? = null,

    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): UserEntitlement = UserEntitlement(
        id = id,
        userId = userId,
        entitlementType = entitlementType,
        sportId = sportId,
        expiresAt = expiresAt,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(domain: UserEntitlement): UserEntitlementJpaEntity = UserEntitlementJpaEntity(
            id = domain.id,
            userId = domain.userId,
            entitlementType = domain.entitlementType,
            sportId = domain.sportId,
            expiresAt = domain.expiresAt,
            createdAt = domain.createdAt
        )
    }
}
