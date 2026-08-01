package com.ligadospalpites.users.application.usecases

import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.domain.ports.UserEntitlementRepository
import com.ligadospalpites.users.domain.ports.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class GetUserStateUseCase(
    private val userRepository: UserRepository,
    private val entitlementRepository: UserEntitlementRepository
) {

    @Transactional(readOnly = true)
    operator fun invoke(userId: UUID): UserStateResult {
        val user = userRepository.findById(userId)
            ?: throw NoSuchElementException("Usuário não encontrado: $userId")

        val entitlements = entitlementRepository.findByUserId(userId)
        val now = Instant.now()
        val activeEntitlements = entitlements.filter { it.expiresAt == null || it.expiresAt.isAfter(now) }

        val hasPremium = activeEntitlements.any { it.entitlementType == EntitlementType.PREMIUM }
        val unlockedSportIds = activeEntitlements
            .filter { it.entitlementType == EntitlementType.SPORT_PASS }
            .mapNotNull { it.sportId }

        val plan = when {
            hasPremium -> "PREMIUM"
            unlockedSportIds.isNotEmpty() -> "PRO"
            else -> "FREE"
        }

        return UserStateResult(
            userId = user.id,
            name = user.name,
            email = user.email,
            plan = plan,
            unlockedSports = unlockedSportIds
        )
    }

    data class UserStateResult(
        val userId: UUID,
        val name: String,
        val email: String,
        val plan: String,
        val unlockedSports: List<UUID>
    )
}
