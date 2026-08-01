package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.application.usecases.LeagueSyncService
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Profile("integration")
class PandaScoreMockSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val seasonRepository: SpringDataSeasonRepository
) : LeagueSyncService {

    private val esportsId = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")

    private val activeLeagueIds = setOf(
        UUID.fromString("7c1e3a11-b9db-44ab-ba02-411a0c0bcf14"), // CBLOL
        UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14"), // VCT Americas
        UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14"), // CS2 Major
        UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14")  // Worlds
    )

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == esportsId && activeLeagueIds.contains(leagueId)
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val existing = matchRepository.findByLeagueId(leagueId)
        matchRepository.deleteAll(existing)

        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val seasonId = activeSeason?.id ?: throw IllegalStateException("No active season for eSports league $leagueId in integration tests.")

        val fixtures = listOf(
            createMatch(leagueId, seasonId, "LOUD", "paiN Gaming", -2, 2, 1, MatchStatus.FINISHED, "Fase de Grupos", 3),
            createMatch(leagueId, seasonId, "FURIA", "RED Canids", -1, 0, 2, MatchStatus.FINISHED, "Fase de Grupos", 3),
            createMatch(leagueId, seasonId, "Kabum!", "Vivo Keyd", 0, 1, 0, MatchStatus.LIVE, "Fase de Grupos", 3),
            createMatch(leagueId, seasonId, "Fluxo", "INTZ", 1, null, null, MatchStatus.SCHEDULED, "Fase de Grupos", 1),
            createMatch(leagueId, seasonId, "T1", "Gen.G", 2, null, null, MatchStatus.SCHEDULED, "Grand Final", 5)
        )

        matchRepository.saveAll(fixtures)
    }

    override fun syncNews(sportId: UUID) {
        // News logic not required for eSports integration tests
    }

    private fun createMatch(
        leagueId: UUID,
        seasonId: UUID,
        home: String,
        away: String,
        daysOffset: Long,
        homeScore: Int?,
        awayScore: Int?,
        status: MatchStatus,
        phase: String?,
        numberOfGames: Int
    ): MatchJpaEntity {
        return MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = esportsId,
            leagueId = leagueId,
            seasonId = seasonId,
            homeTeamName = home,
            awayTeamName = away,
            homeTeamLogoUrl = "https://api.dicebear.com/7.x/initials/svg?seed=$home&radius=50",
            awayTeamLogoUrl = "https://api.dicebear.com/7.x/initials/svg?seed=$away&radius=50",
            homeScore = homeScore,
            awayScore = awayScore,
            kickoffTime = Instant.now().plus(daysOffset, ChronoUnit.DAYS),
            status = status,
            phase = phase,
            numberOfGames = numberOfGames,
            streamUrl = "https://www.twitch.tv/gaules",
            updatedAt = Instant.now()
        )
    }
}
