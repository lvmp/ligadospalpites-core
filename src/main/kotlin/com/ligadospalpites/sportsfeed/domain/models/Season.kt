package com.ligadospalpites.sportsfeed.domain.models

import java.time.Instant
import java.util.UUID

data class Season(
    val id: UUID,
    val leagueId: UUID,
    val name: String,
    val startDate: Instant,
    val endDate: Instant,
    val isActive: Boolean,
    val externalSeasonCode: Int,
    val createdAt: Instant = Instant.now()
) {
    fun isRunning(now: Instant = Instant.now()): Boolean {
        return isActive && now.isAfter(startDate) && now.isBefore(endDate)
    }
}
