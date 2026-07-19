package com.ligadospalpites.sportsfeed.domain.models

import java.time.Instant
import java.util.UUID

enum class MatchStatus {
    SCHEDULED,
    LIVE,
    FINISHED,
    CANCELLED
}

data class Match(
    val id: UUID,
    val sportId: UUID,
    val leagueId: UUID,
    val seasonId: UUID,
    val homeTeamName: String,
    val awayTeamName: String,
    val kickoffTime: Instant,
    val status: MatchStatus,
    val homeScore: Int?,
    val awayScore: Int?,
    val phase: String? = null,
    val updatedAt: Instant = Instant.now()
)
