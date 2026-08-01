package com.ligadospalpites.admin.domain.ports

import com.ligadospalpites.admin.domain.models.AdminLeagueStats
import com.ligadospalpites.admin.domain.models.AdminUserStats
import com.ligadospalpites.admin.domain.models.ConnectorsHealthInfo
import java.util.UUID

interface AdminStatsRepository {
    fun getLeagueStats(): AdminLeagueStats
    fun getAllLeagues(): List<com.ligadospalpites.admin.infrastructure.web.dtos.AdminLeagueDto>
    fun updateLeagueStatus(leagueId: UUID, isActive: Boolean): Boolean
    fun getUserStats(): AdminUserStats
    fun getUsers(
        query: String? = null,
        name: String? = null,
        email: String? = null,
        id: String? = null,
        page: Int = 0,
        size: Int = 50
    ): com.ligadospalpites.admin.infrastructure.web.dtos.AdminUsersPageResponse
    fun grantUserPlan(userId: UUID, plan: String, durationDays: Int): Boolean
    fun getConnectorsHealth(): ConnectorsHealthInfo
}
