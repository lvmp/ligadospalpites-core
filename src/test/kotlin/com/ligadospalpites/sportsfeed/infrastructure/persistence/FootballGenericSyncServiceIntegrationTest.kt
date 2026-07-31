package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.client.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

@ActiveProfiles(profiles = ["resilience_test"], inheritProfiles = false)
class FootballGenericSyncServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var syncService: FootballGenericSyncService

    @Autowired
    private lateinit var matchRepository: SpringDataMatchRepository

    @Autowired
    private lateinit var leagueRepository: SpringDataLeagueRepository

    @MockitoBean
    private lateinit var footballDataClient: FootballDataClient

    @MockitoBean
    private lateinit var apiFootballClient: ApiFootballClient

    @MockitoBean
    private lateinit var espnSoccerClient: EspnSoccerClient

    private val brasileiraoLeagueId = UUID.fromString("3dbd8422-9e22-4411-b0db-b06d0421da6a")
    private val libertadoresLeagueId = UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de")
    private val brasileiraoSeasonId = UUID.fromString("e89c6fb4-2be6-4447-b2e1-87bbca8474ef")

    @BeforeEach
    fun setUp() {
        matchRepository.deleteAll()
    }

    @Test
    fun `should sync from FootballData when primary provider is healthy`() {
        val fdMatch = FootballDataMatch(
            id = 123L,
            utcDate = "2026-04-11T19:00:00Z",
            status = "FINISHED",
            stage = "GROUP_STAGE",
            homeTeam = FootballDataTeam(1L, "Flamengo", "Flamengo"),
            awayTeam = FootballDataTeam(2L, "Palmeiras", "Palmeiras"),
            score = FootballDataScore(FootballDataTeamScore(2, 1))
        )
        `when`(footballDataClient.fetchMatches("BSA")).thenReturn(listOf(fdMatch))

        syncService.syncMatches(UUID.randomUUID(), brasileiraoLeagueId)

        val saved = matchRepository.findByLeagueId(brasileiraoLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Flamengo", saved[0].homeTeamName)
        assertEquals("Palmeiras", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(2, saved[0].homeScore)
        assertEquals(1, saved[0].awayScore)

        verify(footballDataClient, times(1)).fetchMatches("BSA")
        verifyNoInteractions(apiFootballClient)
    }

    @Test
    fun `should translate REGULAR_SEASON to 1º Turno for matchday 1 to 19 and 2º Turno for matchday 20+`() {
        val matchTurno1 = FootballDataMatch(
            id = 101L,
            utcDate = "2026-05-01T19:00:00Z",
            status = "SCHEDULED",
            stage = "REGULAR_SEASON",
            matchday = 5,
            homeTeam = FootballDataTeam(1L, "Flamengo", "Flamengo"),
            awayTeam = FootballDataTeam(2L, "Fluminense", "Fluminense")
        )
        val matchTurno2 = FootballDataMatch(
            id = 102L,
            utcDate = "2026-09-01T19:00:00Z",
            status = "SCHEDULED",
            stage = "REGULAR_SEASON",
            matchday = 25,
            homeTeam = FootballDataTeam(2L, "Fluminense", "Fluminense"),
            awayTeam = FootballDataTeam(1L, "Flamengo", "Flamengo")
        )
        `when`(footballDataClient.fetchMatches("BSA")).thenReturn(listOf(matchTurno1, matchTurno2))

        syncService.syncMatches(UUID.randomUUID(), brasileiraoLeagueId)

        val saved = matchRepository.findByLeagueId(brasileiraoLeagueId)
        assertEquals(2, saved.size)
        val t1 = saved.find { it.homeTeamName == "Flamengo" }
        val t2 = saved.find { it.homeTeamName == "Fluminense" }
        assertNotNull(t1)
        assertNotNull(t2)
        assertEquals("1º Turno", t1?.phase)
        assertEquals("2º Turno", t2?.phase)
    }

    @Test
    fun `should fallback to ApiFootball when primary provider fails`() {
        `when`(footballDataClient.fetchMatches("BSA")).thenThrow(RuntimeException("Football-Data is offline"))

        val afFixture = ApiFootballFixtureWrapper(
            fixture = ApiFootballFixture(456L, "2026-04-11T19:00:00Z", ApiFootballStatus("FT")),
            teams = ApiFootballTeams(ApiFootballTeam("Flamengo"), ApiFootballTeam("Palmeiras")),
            goals = ApiFootballGoals(3, 2)
        )
        `when`(apiFootballClient.fetchMatches(leagueId = 71, season = 2026)).thenReturn(listOf(afFixture))

        syncService.syncMatches(UUID.randomUUID(), brasileiraoLeagueId)

        val saved = matchRepository.findByLeagueId(brasileiraoLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Flamengo", saved[0].homeTeamName)
        assertEquals("Palmeiras", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(3, saved[0].homeScore)
        assertEquals(2, saved[0].awayScore)

        verify(footballDataClient, times(1)).fetchMatches("BSA")
        verify(apiFootballClient, times(1)).fetchMatches(leagueId = 71, season = 2026)
    }

    @Test
    fun `should call ApiFootball directly for Libertadores league`() {
        val afFixture = ApiFootballFixtureWrapper(
            fixture = ApiFootballFixture(789L, "2026-04-11T19:00:00Z", ApiFootballStatus("NS")),
            league = ApiFootballLeague(id = 13L, name = "Copa Libertadores", round = "Group Stage - 1"),
            teams = ApiFootballTeams(ApiFootballTeam("Flamengo"), ApiFootballTeam("Palmeiras")),
            goals = ApiFootballGoals(null, null)
        )
        `when`(apiFootballClient.fetchMatches(leagueId = 13, season = 2026)).thenReturn(listOf(afFixture))

        syncService.syncMatches(UUID.randomUUID(), libertadoresLeagueId)

        val saved = matchRepository.findByLeagueId(libertadoresLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Flamengo", saved[0].homeTeamName)
        assertEquals("Palmeiras", saved[0].awayTeamName)
        assertEquals(MatchStatus.SCHEDULED, saved[0].status)
        assertEquals("Fase de Grupos", saved[0].phase)

        verifyNoInteractions(footballDataClient)
        verify(apiFootballClient, times(1)).fetchMatches(leagueId = 13, season = 2026)
    }

    @Test
    fun `should perform upsert safely without deleting existing predictions or changing match UUIDs`() {
        val matchId = UUID.randomUUID()
        val oldMatch = MatchJpaEntity(
            id = matchId,
            sportId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c"),
            leagueId = brasileiraoLeagueId,
            seasonId = brasileiraoSeasonId,
            homeTeamName = "Flamengo",
            awayTeamName = "Palmeiras",
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null,
            phase = "Rodada 1"
        )
        matchRepository.save(oldMatch)

        val fdMatch = FootballDataMatch(
            id = 123L,
            utcDate = "2026-04-11T19:00:00Z",
            status = "FINISHED",
            stage = "GROUP_STAGE",
            homeTeam = FootballDataTeam(1L, "Flamengo", "Flamengo"),
            awayTeam = FootballDataTeam(2L, "Palmeiras", "Palmeiras"),
            score = FootballDataScore(FootballDataTeamScore(4, 2))
        )
        `when`(footballDataClient.fetchMatches("BSA")).thenReturn(listOf(fdMatch))

        syncService.syncMatches(UUID.randomUUID(), brasileiraoLeagueId)

        val saved = matchRepository.findByLeagueId(brasileiraoLeagueId)
        assertEquals(1, saved.size)
        assertEquals(matchId, saved[0].id)
        assertEquals("Flamengo", saved[0].homeTeamName)
        assertEquals("Palmeiras", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(4, saved[0].homeScore)
        assertEquals(2, saved[0].awayScore)
    }

    @Test
    fun `should auto-update league logoUrl during sync matches`() {
        val fdMatch = FootballDataMatch(
            id = 999L,
            utcDate = "2026-04-11T19:00:00Z",
            status = "SCHEDULED",
            stage = "REGULAR_SEASON",
            matchday = 1,
            homeTeam = FootballDataTeam(1L, "Flamengo", "Flamengo"),
            awayTeam = FootballDataTeam(2L, "Palmeiras", "Palmeiras")
        )
        `when`(footballDataClient.fetchMatches("BSA")).thenReturn(listOf(fdMatch))

        // Create league with null logoUrl in DB first
        val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
        leagueRepository.save(LeagueJpaEntity(id = brasileiraoLeagueId, name = "Campeonato Brasileiro", sportId = footballId, isActive = true, logoUrl = null))

        syncService.syncMatches(footballId, brasileiraoLeagueId)

        val updatedLeague = leagueRepository.findById(brasileiraoLeagueId).orElse(null)
        assertNotNull(updatedLeague)
        assertEquals("https://media.api-sports.io/football/leagues/71.png", updatedLeague?.logoUrl)
    }
}
