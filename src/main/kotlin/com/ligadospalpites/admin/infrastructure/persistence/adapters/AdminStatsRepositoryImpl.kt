package com.ligadospalpites.admin.infrastructure.persistence.adapters

import com.ligadospalpites.admin.domain.models.*
import com.ligadospalpites.admin.domain.ports.AdminStatsRepository
import com.ligadospalpites.payments.infrastructure.persistence.SpringDataSubscriptionRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.LeagueJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import com.ligadospalpites.users.infrastructure.persistence.UserEntitlementJpaEntity
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Repository
class AdminStatsRepositoryImpl(
    private val leagueRepository: SpringDataLeagueRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val userRepository: SpringDataUserRepository,
    private val userEntitlementRepository: SpringDataUserEntitlementRepository,
    private val subscriptionRepository: SpringDataSubscriptionRepository
) : AdminStatsRepository {

    override fun getLeagueStats(): AdminLeagueStats {
        val active = leagueRepository.countByIsActive(true)
        val inactive = leagueRepository.countByIsActive(false)
        val totalMatches = matchRepository.count()
        return AdminLeagueStats(
            activeLeagues = active,
            inactiveLeagues = inactive,
            totalMatches = totalMatches,
            syncStatus = "HEALTHY"
        )
    }

    override fun updateLeagueStatus(leagueId: UUID, isActive: Boolean): Boolean {
        val optionalLeague = leagueRepository.findById(leagueId)
        if (optionalLeague.isEmpty) return false
        val existing = optionalLeague.get()
        val updated = LeagueJpaEntity(
            id = existing.id,
            name = existing.name,
            sportId = existing.sportId,
            isActive = isActive,
            logoUrl = existing.logoUrl,
            createdAt = existing.createdAt
        )
        leagueRepository.save(updated)
        return true
    }

    override fun getUserStats(): AdminUserStats {
        val totalUsers = userRepository.count()
        val entitlements = userEntitlementRepository.findAll()
        val premiumCount = entitlements.count { it.entitlementType == EntitlementType.PREMIUM }
        val proCount = entitlements.count { it.entitlementType == EntitlementType.SPORT_PASS }
        val vipCount = 0L
        val freeCount = (totalUsers - (premiumCount + proCount + vipCount)).coerceAtLeast(0)

        val conversionRate = if (totalUsers > 0) ((premiumCount + proCount + vipCount).toDouble() / totalUsers) * 100.0 else 0.0

        return AdminUserStats(
            activeUsers = totalUsers,
            inactiveUsers = 0L,
            planBreakdown = mapOf(
                "FREE" to freeCount,
                "PRO" to proCount.toLong(),
                "PREMIUM" to premiumCount.toLong(),
                "VIP" to vipCount
            ),
            conversionRate = Math.round(conversionRate * 100.0) / 100.0,
            retentionRate = 94.5,
            churnRate = 5.5
        )
    }

    override fun getUsers(page: Int, size: Int): com.ligadospalpites.admin.infrastructure.web.dtos.AdminUsersPageResponse {
        val pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
        val userPage = userRepository.findAll(pageable)
        val entitlements = userEntitlementRepository.findAll()

        val content = userPage.content.map { user ->
            val userEnts = entitlements.filter { it.userId == user.id }
            val activeEnt = userEnts.firstOrNull {
                val exp = it.expiresAt
                exp == null || exp.isAfter(Instant.now())
            }
            val planName = when (activeEnt?.entitlementType) {
                EntitlementType.PREMIUM -> "PREMIUM"
                EntitlementType.SPORT_PASS -> "PRO"
                else -> "FREE"
            }
            com.ligadospalpites.admin.infrastructure.web.dtos.AdminUserSummaryDto(
                id = user.id.toString(),
                firebaseUid = user.firebaseUid,
                email = user.email,
                name = user.name,
                avatarUrl = user.avatarUrl,
                plan = planName,
                createdAt = user.createdAt
            )
        }

        return com.ligadospalpites.admin.infrastructure.web.dtos.AdminUsersPageResponse(
            content = content,
            totalElements = userPage.totalElements,
            totalPages = userPage.totalPages,
            page = page,
            size = size
        )
    }

    override fun grantUserPlan(userId: UUID, plan: String, durationDays: Int): Boolean {
        val userOptional = userRepository.findById(userId)
        if (userOptional.isEmpty) return false

        val entitlementType = when (plan.uppercase()) {
            "PRO" -> EntitlementType.SPORT_PASS
            "PREMIUM", "VIP" -> EntitlementType.PREMIUM
            else -> EntitlementType.SPORT_PASS
        }

        val expiresAt = Instant.now().plus(durationDays.toLong(), ChronoUnit.DAYS)
        val entitlement = UserEntitlementJpaEntity(
            userId = userId,
            entitlementType = entitlementType,
            expiresAt = expiresAt
        )
        userEntitlementRepository.save(entitlement)
        return true
    }

    override fun getConnectorsHealth(): ConnectorsHealthInfo {
        val now = Instant.now()
        val providers = mapOf(
            "SOCCER" to ProviderHealth("HEALTHY", 110, now.minus(10, ChronoUnit.MINUTES)),
            "BASKETBALL" to ProviderHealth("HEALTHY", 95, now.minus(8, ChronoUnit.MINUTES)),
            "ESPORTS" to ProviderHealth("DEGRADED", 1240, now.minus(30, ChronoUnit.MINUTES)),
            "TENNIS" to ProviderHealth("HEALTHY", 105, now.minus(5, ChronoUnit.MINUTES))
        )
        val overallStatus = if (providers.values.any { it.status == "DOWN" }) "DOWN"
        else if (providers.values.any { it.status == "DEGRADED" }) "DEGRADED"
        else "UP"

        return ConnectorsHealthInfo(
            status = overallStatus,
            providers = providers
        )
    }
}
