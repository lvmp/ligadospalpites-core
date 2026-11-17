package com.ligadospalpites.sportsfeed.application.usecases

import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SeasonJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataSeasonRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.UUID

class GetBracketsUseCaseTest {

    private val seasonRepository = mock(SpringDataSeasonRepository::class.java)
    private val matchRepository = mock(SpringDataMatchRepository::class.java)
    private lateinit var useCase: GetBracketsUseCase

    private val leagueId = UUID.randomUUID()
    private val seasonId = UUID.randomUUID()
    private val sportId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        useCase = GetBracketsUseCase(seasonRepository, matchRepository)
    }

    @Test
    fun `should group matches into correct tournament bracket stages`() {
        val season = SeasonJpaEntity(
            id = seasonId,
            leagueId = leagueId,
            name = "2026",
            startDate = Instant.now(),
            endDate = Instant.now().plusSeconds(86400 * 30),
            isActive = true
        )
        `when`(seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)).thenReturn(season)

        val matches = listOf(
            createMatch("Flamengo", "Palmeiras", "Oitavas de Final"),
            createMatch("São Paulo", "Corinthians", "quarterFinals"),
            createMatch("Grêmio", "Internacional", "Semifinal"),
            createMatch("Flamengo", "Grêmio", "Grande Final")
        )
        `when`(matchRepository.findBySeasonId(seasonId)).thenReturn(matches)

        val result = useCase.execute(leagueId)

        assertEquals(leagueId, result.leagueId)
        val phases = result.phases
        assertEquals(1, phases["OITAVAS"]?.size)
        assertEquals("Flamengo", phases["OITAVAS"]?.first()?.homeTeam)

        assertEquals(1, phases["QUARTAS"]?.size)
        assertEquals("São Paulo", phases["QUARTAS"]?.first()?.homeTeam)

        assertEquals(1, phases["SEMI"]?.size)
        assertEquals("Grêmio", phases["SEMI"]?.first()?.homeTeam)

        assertEquals(1, phases["FINAL"]?.size)
        assertEquals("Flamengo", phases["FINAL"]?.first()?.homeTeam)
    }

    @Test
    fun `should fallback to query by leagueId when active season is not present`() {
        `when`(seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)).thenReturn(null)
        val matches = listOf(
            createMatch("Chelsea", "Arsenal", "finalMatch")
        )
        `when`(matchRepository.findByLeagueId(leagueId)).thenReturn(matches)

        val result = useCase.execute(leagueId)

        assertEquals(leagueId, result.leagueId)
        assertEquals(1, result.phases["FINAL"]?.size)
        verify(matchRepository, times(1)).findByLeagueId(leagueId)
    }

    private fun createMatch(home: String, away: String, phase: String): MatchJpaEntity {
        return MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = sportId,
            leagueId = leagueId,
            seasonId = seasonId,
            homeTeamName = home,
            awayTeamName = away,
            kickoffTime = Instant.now(),
            status = MatchStatus.FINISHED,
            homeScore = 2,
            awayScore = 1,
            phase = phase
        )
    }
}
