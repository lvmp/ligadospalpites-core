package com.ligadospalpites.admin.domain.models

import java.time.Instant

data class AdminLeagueStats(
    val activeLeagues: Long,
    val inactiveLeagues: Long,
    val totalMatches: Long,
    val syncStatus: String
)

data class AdminUserStats(
    val activeUsers: Long,
    val inactiveUsers: Long,
    val planBreakdown: Map<String, Long>,
    val conversionRate: Double,
    val retentionRate: Double,
    val churnRate: Double
)

data class ProviderHealth(
    val status: String,
    val latencyMs: Long,
    val lastSync: Instant
)

data class ConnectorsHealthInfo(
    val status: String,
    val providers: Map<String, ProviderHealth>
)
