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
class BasketballSyncServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var syncService: BasketballSyncService

    @Autowired
    private lateinit var matchRepository: SpringDataMatchRepository

    @MockitoBean
    private lateinit var apiBasketballClient: ApiBasketballClient

    @MockitoBean
    private lateinit var espnBasketballClient: EspnBasketballClient

    @MockitoBean
    private lateinit var balldontlieClient: BalldontlieClient

    private val nbaLeagueId = UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    private val nbaSeasonId = UUID.fromString("8a6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef")

    @BeforeEach
    fun setUp() {
        matchRepository.deleteAll()
        `when`(balldontlieClient.fetchNbaGames()).thenReturn(emptyList())
        `when`(espnBasketballClient.fetchNbaScoreboard()).thenReturn(emptyList())
    }

    @Test
    fun `should sync from ApiBasketball when provider is healthy`() {
        val game = ApiBasketballGameWrapper(
            date = "2026-10-21T00:00:00Z",
            stage = "Temporada Regular",
            status = ApiBasketballStatus("FT"),
            teams = ApiBasketballTeams(ApiBasketballTeam("Boston Celtics"), ApiBasketballTeam("Miami Heat")),
            scores = ApiBasketballScores(ApiBasketballTeamScore(110), ApiBasketballTeamScore(100))
        )
        `when`(apiBasketballClient.fetchGames(leagueId = 12, season = 2026)).thenReturn(listOf(game))

        syncService.syncMatches(UUID.randomUUID(), nbaLeagueId)

        val saved = matchRepository.findByLeagueId(nbaLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Boston Celtics", saved[0].homeTeamName)
        assertEquals("Miami Heat", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(110, saved[0].homeScore)
        assertEquals(100, saved[0].awayScore)

        verify(apiBasketballClient, times(1)).fetchGames(leagueId = 12, season = 2026)
    }

    @Test
    fun `should perform upsert safely without deleting predictions or changing game UUIDs`() {
        val gameId = UUID.randomUUID()
        val oldGame = MatchJpaEntity(
            id = gameId,
            sportId = UUID.fromString("e5284bf1-d576-4740-97cc-f06bca181cb2"),
            leagueId = nbaLeagueId,
            seasonId = nbaSeasonId,
            homeTeamName = "Boston Celtics",
            awayTeamName = "Miami Heat",
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null,
            phase = "Temporada Regular"
        )
        matchRepository.save(oldGame)

        val game = ApiBasketballGameWrapper(
            date = "2026-10-21T00:00:00Z",
            stage = "Temporada Regular",
            status = ApiBasketballStatus("FT"),
            teams = ApiBasketballTeams(ApiBasketballTeam("Boston Celtics"), ApiBasketballTeam("Miami Heat")),
            scores = ApiBasketballScores(ApiBasketballTeamScore(115), ApiBasketballTeamScore(105))
        )
        `when`(apiBasketballClient.fetchGames(leagueId = 12, season = 2026)).thenReturn(listOf(game))

        syncService.syncMatches(UUID.randomUUID(), nbaLeagueId)

        val saved = matchRepository.findByLeagueId(nbaLeagueId)
        assertEquals(1, saved.size)
        assertEquals(gameId, saved[0].id)
        assertEquals("Boston Celtics", saved[0].homeTeamName)
        assertEquals("Miami Heat", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(115, saved[0].homeScore)
        assertEquals(105, saved[0].awayScore)
    }

    @Test
    fun `should sync from EspnBasketballClient and persist periodScoresJson`() {
        val espnGame = EspnBasketballGame(
            externalId = "401584001",
            date = "2026-10-21T00:00:00Z",
            homeTeamName = "Boston Celtics",
            awayTeamName = "Miami Heat",
            homeTeamLogoUrl = "https://cdn.espn.com/celtics.png",
            awayTeamLogoUrl = "https://cdn.espn.com/heat.png",
            homeScore = 110,
            awayScore = 100,
            statusShort = "FT",
            periodScoresJson = """{"home":[28,30,22,30],"away":[24,25,29,22],"homeOvertime":0,"awayOvertime":0}""",
            phase = "NBA"
        )
        `when`(espnBasketballClient.fetchNbaScoreboard()).thenReturn(listOf(espnGame))

        syncService.syncMatches(UUID.randomUUID(), nbaLeagueId)

        val saved = matchRepository.findByLeagueId(nbaLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Boston Celtics", saved[0].homeTeamName)
        assertEquals("Miami Heat", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(110, saved[0].homeScore)
        assertEquals(100, saved[0].awayScore)
        assertNotNull(saved[0].periodScoresJson)
        assertTrue(saved[0].periodScoresJson!!.contains("28, 30, 22, 30") || saved[0].periodScoresJson!!.contains("28,30,22,30"))
    }

    @Test
    fun `should sync from BalldontlieClient as primary provider for NBA`() {
        val bdlGame = BalldontlieNbaGame(
            externalId = "101",
            date = "2026-10-21T00:00:00Z",
            homeTeamName = "Boston Celtics",
            awayTeamName = "Miami Heat",
            homeTeamLogoUrl = null,
            awayTeamLogoUrl = null,
            homeScore = 115,
            awayScore = 108,
            statusShort = "FT",
            phase = "Temporada Regular"
        )
        `when`(balldontlieClient.fetchNbaGames()).thenReturn(listOf(bdlGame))

        syncService.syncMatches(UUID.randomUUID(), nbaLeagueId)

        val saved = matchRepository.findByLeagueId(nbaLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Boston Celtics", saved[0].homeTeamName)
        assertEquals("Miami Heat", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(115, saved[0].homeScore)
        assertEquals(108, saved[0].awayScore)
        verify(balldontlieClient, times(1)).fetchNbaGames()
        verify(espnBasketballClient, never()).fetchNbaScoreboard()
    }
}
