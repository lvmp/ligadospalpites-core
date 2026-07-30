package com.ligadospalpites.admin.domain.ports

import com.ligadospalpites.admin.domain.models.AdminLeagueStats
import com.ligadospalpites.admin.domain.models.AdminUserStats
import com.ligadospalpites.admin.domain.models.ConnectorsHealthInfo
import java.util.UUID

interface AdminStatsRepository {
    fun getLeagueStats(): AdminLeagueStats
    fun updateLeagueStatus(leagueId: UUID, isActive: Boolean): Boolean
    fun getUserStats(): AdminUserStats
    fun grantUserPlan(userId: UUID, plan: String, durationDays: Int): Boolean
    fun getConnectorsHealth(): ConnectorsHealthInfo
}
