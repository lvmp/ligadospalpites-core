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
    val homeTeamLogoUrl: String? = null,
    val awayTeamLogoUrl: String? = null,
    val periodScoresJson: String? = null,
    val updatedAt: Instant = Instant.now()
)

fun formatMatchPhase(phase: String?): String? {
    if (phase.isNullOrBlank()) return phase
    val upper = phase.uppercase().replace("_", " ").trim()
    return when {
        upper == "REGULAR SEASON 1" || upper == "REGULAR SEASON" || upper == "RODADA REGULAR" || upper == "FASE REGULAR" -> "1º Turno"
        upper == "REGULAR SEASON 2" -> "2º Turno"
        upper.startsWith("REGULAR SEASON") -> {
            val num = Regex("\\d+").find(upper)?.value?.toIntOrNull()
            if (num != null) {
                if (num <= 19) "1º Turno" else "2º Turno"
            } else {
                "1º Turno"
            }
        }
        else -> phase
    }
}
