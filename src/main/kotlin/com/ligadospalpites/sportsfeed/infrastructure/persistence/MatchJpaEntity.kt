package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.Match
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_matches")
class MatchJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "sport_id", nullable = false)
    val sportId: UUID = UUID.randomUUID(),

    @Column(name = "league_id", nullable = false)
    val leagueId: UUID = UUID.randomUUID(),

    @Column(name = "season_id", nullable = false)
    val seasonId: UUID = UUID.randomUUID(),

    @Column(name = "home_team_name", nullable = false, length = 150)
    val homeTeamName: String = "",

    @Column(name = "away_team_name", nullable = false, length = 150)
    val awayTeamName: String = "",

    @Column(name = "kickoff_time", nullable = false)
    val kickoffTime: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val status: MatchStatus = MatchStatus.SCHEDULED,

    @Column(name = "home_score")
    val homeScore: Int? = null,

    @Column(name = "away_score")
    val awayScore: Int? = null,

    @Column(name = "phase", length = 100)
    val phase: String? = null,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    fun toDomain(): Match = Match(
        id = id,
        sportId = sportId,
        leagueId = leagueId,
        seasonId = seasonId,
        homeTeamName = homeTeamName,
        awayTeamName = awayTeamName,
        kickoffTime = kickoffTime,
        status = status,
        homeScore = homeScore,
        awayScore = awayScore,
        phase = phase,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(match: Match): MatchJpaEntity = MatchJpaEntity(
            id = match.id,
            sportId = match.sportId,
            leagueId = match.leagueId,
            seasonId = match.seasonId,
            homeTeamName = match.homeTeamName,
            awayTeamName = match.awayTeamName,
            kickoffTime = match.kickoffTime,
            status = match.status,
            homeScore = match.homeScore,
            awayScore = match.awayScore,
            phase = match.phase,
            updatedAt = match.updatedAt
        )
    }
}
