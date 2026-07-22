package com.ligadospalpites.sportsfeed.domain.events

import java.util.UUID

data class MatchStartedEvent(
    val matchId: UUID,
    val homeTeamName: String,
    val awayTeamName: String,
    val sportId: UUID,
    val leagueId: UUID
)

data class MatchGoalEvent(
    val matchId: UUID,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int,
    val awayScore: Int,
    val scoringTeam: String, // "HOME" ou "AWAY"
    val sportId: UUID,
    val leagueId: UUID
)

data class MatchFinishedEvent(
    val matchId: UUID,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int,
    val awayScore: Int,
    val sportId: UUID,
    val leagueId: UUID
)
