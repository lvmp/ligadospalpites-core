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
class FootballGenericMockSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val seasonRepository: SpringDataSeasonRepository
) : LeagueSyncService {

    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")

    private val activeLeagueIds = setOf(
        UUID.fromString("3dbd8422-9e22-4411-b0db-b06d0421da6a"), // Brasileirão
        UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de"), // Libertadores
        UUID.fromString("9284ca51-bb54-47c1-841f-81ab28120fa2"), // La Liga
        UUID.fromString("827d043c-62c2-402c-b011-3ba2849e7b23"), // Premier League
        UUID.fromString("e2d03a11-b9db-44ab-ba02-411a0c0bcf14"), // Champions League
        UUID.fromString("5acdf011-fbde-4122-83bc-c46b1ba847de"), // Championship
        UUID.fromString("6acdf011-fbde-4122-83bc-c46b1ba847de"), // Eurocopa
        UUID.fromString("7acdf011-fbde-4122-83bc-c46b1ba847de"), // Ligue 1
        UUID.fromString("8acdf011-fbde-4122-83bc-c46b1ba847de"), // Bundesliga
        UUID.fromString("9acdf011-fbde-4122-83bc-c46b1ba847de"), // Serie A
        UUID.fromString("aacdf011-fbde-4122-83bc-c46b1ba847de"), // Eredivisie
        UUID.fromString("bacdf011-fbde-4122-83bc-c46b1ba847de"), // Primeira Liga
        UUID.fromString("b3cdf011-fbde-4122-83bc-c46b1ba847de")  // Copa do Brasil
    )

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == footballId && activeLeagueIds.contains(leagueId)
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val existing = matchRepository.findByLeagueId(leagueId)
        matchRepository.deleteAll(existing)

        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val seasonId = activeSeason?.id ?: throw IllegalStateException("No active season for league $leagueId in integration tests.")

        val fixtures = listOf(
            createMatch(leagueId, seasonId, "Flamengo", "Palmeiras", -2, 2, 1, MatchStatus.FINISHED, "Rodada 1"),
            createMatch(leagueId, seasonId, "São Paulo", "Corinthians", -1, 1, 1, MatchStatus.FINISHED, "Rodada 1"),
            createMatch(leagueId, seasonId, "Fluminense", "Botafogo", 0, 0, 0, MatchStatus.LIVE, "Rodada 2"),
            createMatch(leagueId, seasonId, "Vasco da Gama", "Cruzeiro", 1, null, null, MatchStatus.SCHEDULED, "Rodada 2"),
            createMatch(leagueId, seasonId, "Santos", "Grêmio", 2, null, null, MatchStatus.SCHEDULED, "Rodada 2")
        )

        matchRepository.saveAll(fixtures)
    }

    override fun syncNews(sportId: UUID) {
        // News logic not required for generic integration tests
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
        phase: String?
    ): MatchJpaEntity {
        return MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = footballId,
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
            updatedAt = Instant.now()
        )
    }
}
