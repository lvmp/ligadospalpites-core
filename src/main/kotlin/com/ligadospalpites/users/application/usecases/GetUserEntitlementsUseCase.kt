package com.ligadospalpites.users.application.usecases

import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.domain.ports.UserEntitlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class GetUserEntitlementsUseCase(
    private val entitlementRepository: UserEntitlementRepository
) {

    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): UserEntitlementsResult {
        val entitlements = entitlementRepository.findByUserId(userId)
        val now = Instant.now()

        val itemDtos = entitlements.map { ent ->
            val isActive = ent.expiresAt == null || ent.expiresAt.isAfter(now)
            UserEntitlementItem(
                id = ent.id,
                entitlementType = ent.entitlementType,
                sportId = ent.sportId,
                expiresAt = ent.expiresAt,
                active = isActive,
                createdAt = ent.createdAt
            )
        }

        val hasPremium = itemDtos.any { it.active && it.entitlementType == EntitlementType.PREMIUM }

        return UserEntitlementsResult(
            userId = userId,
            hasPremium = hasPremium,
            entitlements = itemDtos
        )
    }

    data class UserEntitlementsResult(
        val userId: UUID,
        val hasPremium: Boolean,
        val entitlements: List<UserEntitlementItem>
    )

    data class UserEntitlementItem(
        val id: UUID,
        val entitlementType: EntitlementType,
        val sportId: UUID?,
        val expiresAt: Instant?,
        val active: Boolean,
        val createdAt: Instant
    )
}
