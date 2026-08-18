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
    private val copaDoBrasilLeagueId = UUID.fromString("b3cdf011-fbde-4122-83bc-c46b1ba847de")
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
        `when`(footballDataClient.fetchMatches("BSA", 2026)).thenReturn(listOf(fdMatch))

        syncService.syncMatches(UUID.randomUUID(), brasileiraoLeagueId)

        val saved = matchRepository.findByLeagueId(brasileiraoLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Flamengo", saved[0].homeTeamName)
        assertEquals("Palmeiras", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(2, saved[0].homeScore)
        assertEquals(1, saved[0].awayScore)

        verify(footballDataClient, times(1)).fetchMatches("BSA", 2026)
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
        `when`(footballDataClient.fetchMatches("BSA", 2026)).thenReturn(listOf(matchTurno1, matchTurno2))

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
        `when`(footballDataClient.fetchMatches("BSA", 2026)).thenThrow(RuntimeException("Football-Data is offline"))
        `when`(footballDataClient.fetchMatches("BSA", null)).thenThrow(RuntimeException("Football-Data is offline"))

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

        verify(footballDataClient, times(1)).fetchMatches("BSA", 2026)
        verify(apiFootballClient, times(1)).fetchMatches(leagueId = 71, season = 2026)
    }

    @Test
    fun `should call EspnSoccerClient for Libertadores league with isEuropeanCalendar false`() {
        val espnEvent = EspnSoccerEvent(
            id = "789",
            date = "2026-04-11T19:00:00Z",
            competitions = listOf(
                EspnSoccerCompetition(
                    id = "789",
                    date = "2026-04-11T19:00:00Z",
                    status = EspnSoccerStatus(EspnSoccerStatusType(state = "pre")),
                    notes = listOf(EspnSoccerNote(headline = "Group Stage - Matchday 1")),
                    competitors = listOf(
                        EspnSoccerCompetitor("1", "home", false, EspnSoccerTeam("1", displayName = "Flamengo")),
                        EspnSoccerCompetitor("2", "away", false, EspnSoccerTeam("2", displayName = "Palmeiras"))
                    )
                )
            )
        )
        `when`(espnSoccerClient.fetchSoccerMatches("conmebol.libertadores", 2026, false)).thenReturn(listOf(espnEvent))

        syncService.syncMatches(UUID.randomUUID(), libertadoresLeagueId)

        val saved = matchRepository.findByLeagueId(libertadoresLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Flamengo", saved[0].homeTeamName)
        assertEquals("Palmeiras", saved[0].awayTeamName)
        assertEquals(MatchStatus.SCHEDULED, saved[0].status)
        assertEquals("Fase de Grupos", saved[0].phase)

        verifyNoInteractions(footballDataClient)
        verifyNoInteractions(apiFootballClient)
        verify(espnSoccerClient, times(1)).fetchSoccerMatches("conmebol.libertadores", 2026, false)
    }

    @Test
    fun `should call FootballDataClient for Champions League with code CL`() {
        val championsLeagueId = UUID.fromString("e2d03a11-b9db-44ab-ba02-411a0c0bcf14")
        val fdMatch = FootballDataMatch(
            id = 777L,
            utcDate = "2026-09-16T19:00:00Z",
            status = "SCHEDULED",
            stage = "LEAGUE_STAGE",
            homeTeam = FootballDataTeam(101L, "Real Madrid", "Real Madrid"),
            awayTeam = FootballDataTeam(102L, "Barcelona", "Barcelona")
        )
        `when`(footballDataClient.fetchMatches("CL", 2026)).thenReturn(listOf(fdMatch))

        syncService.syncMatches(UUID.randomUUID(), championsLeagueId)

        val saved = matchRepository.findByLeagueId(championsLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Real Madrid", saved[0].homeTeamName)
        assertEquals("Barcelona", saved[0].awayTeamName)

        verify(footballDataClient, times(1)).fetchMatches("CL", 2026)
        verifyNoInteractions(espnSoccerClient)
    }

    @Test
    fun `should call FootballDataClient for Premier League with code PL`() {
        val premierLeagueId = UUID.fromString("827d043c-62c2-402c-b011-3ba2849e7b23")
        val fdMatch = FootballDataMatch(
            id = 888L,
            utcDate = "2026-08-15T14:00:00Z",
            status = "SCHEDULED",
            stage = "REGULAR_SEASON",
            matchday = 1,
            homeTeam = FootballDataTeam(201L, "Arsenal", "Arsenal"),
            awayTeam = FootballDataTeam(202L, "Chelsea", "Chelsea")
        )
        `when`(footballDataClient.fetchMatches("PL", 2026)).thenReturn(listOf(fdMatch))

        syncService.syncMatches(UUID.randomUUID(), premierLeagueId)

        val saved = matchRepository.findByLeagueId(premierLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Arsenal", saved[0].homeTeamName)
        assertEquals("Chelsea", saved[0].awayTeamName)

        verify(footballDataClient, times(1)).fetchMatches("PL", 2026)
        verifyNoInteractions(espnSoccerClient)
    }

    @Test
    fun `should call EspnSoccerClient for Copa do Brasil league without calling ApiFootballClient`() {
        val espnEvent = EspnSoccerEvent(
            id = "999",
            date = "2026-05-15T21:30:00Z",
            competitions = listOf(
                EspnSoccerCompetition(
                    id = "999",
                    date = "2026-05-15T21:30:00Z",
                    status = EspnSoccerStatus(EspnSoccerStatusType(state = "pre")),
                    notes = listOf(EspnSoccerNote(headline = "3ª Fase")),
                    competitors = listOf(
                        EspnSoccerCompetitor("10", "home", false, EspnSoccerTeam("10", displayName = "São Paulo")),
                        EspnSoccerCompetitor("20", "away", false, EspnSoccerTeam("20", displayName = "Corinthians"))
                    )
                )
            )
        )
        `when`(espnSoccerClient.fetchSoccerMatches("bra.copa_do_brazil", 2026, false)).thenReturn(listOf(espnEvent))

        syncService.syncMatches(UUID.randomUUID(), copaDoBrasilLeagueId)

        val saved = matchRepository.findByLeagueId(copaDoBrasilLeagueId)
        assertEquals(1, saved.size)
        assertEquals("São Paulo", saved[0].homeTeamName)
        assertEquals("Corinthians", saved[0].awayTeamName)
        assertEquals(MatchStatus.SCHEDULED, saved[0].status)
        assertEquals("3ª Fase", saved[0].phase)

        verifyNoInteractions(footballDataClient)
        verifyNoInteractions(apiFootballClient)
        verify(espnSoccerClient, times(1)).fetchSoccerMatches("bra.copa_do_brazil", 2026, false)
    }

    @Test
    fun `should correctly parse ESPN ISO date without seconds and preserve actual kickoff time`() {
        val espnEvent = EspnSoccerEvent(
            id = "888",
            date = "2026-04-10T22:00Z",
            competitions = listOf(
                EspnSoccerCompetition(
                    id = "888",
                    date = "2026-04-10T22:00Z",
                    status = EspnSoccerStatus(EspnSoccerStatusType(state = "pre")),
                    notes = listOf(EspnSoccerNote(headline = "Fase de Grupos")),
                    competitors = listOf(
                        EspnSoccerCompetitor("1", "home", false, EspnSoccerTeam("1", displayName = "Flamengo")),
                        EspnSoccerCompetitor("2", "away", false, EspnSoccerTeam("2", displayName = "Palmeiras"))
                    )
                )
            )
        )
        `when`(espnSoccerClient.fetchSoccerMatches("conmebol.libertadores", 2026, false)).thenReturn(listOf(espnEvent))

        syncService.syncMatches(UUID.randomUUID(), libertadoresLeagueId)

        val saved = matchRepository.findByLeagueId(libertadoresLeagueId)
        assertEquals(1, saved.size)
        assertEquals(java.time.Instant.parse("2026-04-10T22:00:00Z"), saved[0].kickoffTime)
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
        `when`(footballDataClient.fetchMatches("BSA", 2026)).thenReturn(listOf(fdMatch))

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
        `when`(footballDataClient.fetchMatches("BSA", 2026)).thenReturn(listOf(fdMatch))

        // Create league with null logoUrl in DB first
        val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
        leagueRepository.save(LeagueJpaEntity(id = brasileiraoLeagueId, name = "Campeonato Brasileiro", sportId = footballId, isActive = true, logoUrl = null))

        syncService.syncMatches(footballId, brasileiraoLeagueId)

        val updatedLeague = leagueRepository.findById(brasileiraoLeagueId).orElse(null)
        assertNotNull(updatedLeague)
        assertEquals("https://a.espncdn.com/i/leaguelogos/soccer/500/85.png", updatedLeague?.logoUrl)
    }
}
