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
class BasketballMockSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val seasonRepository: SpringDataSeasonRepository
) : LeagueSyncService {

    private val basketballId = UUID.fromString("e5284bf1-d576-4740-97cc-f06bca181cb2")

    private val activeLeagueIds = setOf(
        UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14"), // NBA
        UUID.fromString("2dbd1112-9cde-4411-b0db-b06d0421da6a")  // NBB
    )

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == basketballId && activeLeagueIds.contains(leagueId)
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val existing = matchRepository.findByLeagueId(leagueId)
        matchRepository.deleteAll(existing)

        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val seasonId = activeSeason?.id ?: throw IllegalStateException("No active season for basketball league $leagueId in integration tests.")

        val isNba = leagueId == UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14")

        val fixtures = if (isNba) {
            listOf(
                createMatch(leagueId, seasonId, "Boston Celtics", "Miami Heat", -2, 110, 100, MatchStatus.FINISHED, "Temporada Regular"),
                createMatch(leagueId, seasonId, "Los Angeles Lakers", "Golden State Warriors", -1, 105, 108, MatchStatus.FINISHED, "Temporada Regular"),
                createMatch(leagueId, seasonId, "Milwaukee Bucks", "Philadelphia 76ers", 0, 88, 85, MatchStatus.LIVE, "Temporada Regular"),
                createMatch(leagueId, seasonId, "Phoenix Suns", "Dallas Mavericks", 1, null, null, MatchStatus.SCHEDULED, "Temporada Regular"),
                createMatch(leagueId, seasonId, "Denver Nuggets", "Los Angeles Clippers", 2, null, null, MatchStatus.SCHEDULED, "Temporada Regular")
            )
        } else {
            listOf(
                createMatch(leagueId, seasonId, "Flamengo", "Franca", -2, 85, 82, MatchStatus.FINISHED, "Temporada Regular"),
                createMatch(leagueId, seasonId, "Minas", "São Paulo", -1, 90, 93, MatchStatus.FINISHED, "Temporada Regular"),
                createMatch(leagueId, seasonId, "Bauru", "Corinthians", 0, 72, 70, MatchStatus.LIVE, "Temporada Regular"),
                createMatch(leagueId, seasonId, "Paulistano", "Pinheiros", 1, null, null, MatchStatus.SCHEDULED, "Temporada Regular"),
                createMatch(leagueId, seasonId, "Pato Basquete", "Caxias do Sul", 2, null, null, MatchStatus.SCHEDULED, "Temporada Regular")
            )
        }

        matchRepository.saveAll(fixtures)
    }

    override fun syncNews(sportId: UUID) {
        // News logic not required for basketball tests
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
            sportId = basketballId,
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
