package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.client.ApiBasketballClient
import com.ligadospalpites.sportsfeed.infrastructure.client.BalldontlieClient
import com.ligadospalpites.sportsfeed.infrastructure.client.EspnBasketballClient
import com.ligadospalpites.sportsfeed.infrastructure.client.EspnBasketballGame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.*

class BasketballSyncServiceUnitTest {

    private val matchRepository = mock(SpringDataMatchRepository::class.java)
    private val apiBasketballClient = mock(ApiBasketballClient::class.java)
    private val espnBasketballClient = mock(EspnBasketballClient::class.java)
    private val balldontlieClient = mock(BalldontlieClient::class.java)
    private val seasonRepository = mock(SpringDataSeasonRepository::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val leagueRepository = mock(SpringDataLeagueRepository::class.java)

    private lateinit var syncService: BasketballSyncService

    private val nbaLeagueId = UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    private val basketballId = UUID.fromString("e5284bf1-d576-4740-97cc-f06bca181cb2")
    private val seasonId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        syncService = BasketballSyncService(
            matchRepository = matchRepository,
            apiBasketballClient = apiBasketballClient,
            espnBasketballClient = espnBasketballClient,
            balldontlieClient = balldontlieClient,
            statsNbaClient = null,
            seasonRepository = seasonRepository,
            eventPublisher = eventPublisher,
            leagueRepository = leagueRepository
        )

        val season = SeasonJpaEntity(
            id = seasonId,
            leagueId = nbaLeagueId,
            name = "2026",
            startDate = Instant.now(),
            endDate = Instant.now().plusSeconds(86400 * 180),
            isActive = true,
            externalSeasonCode = 2026
        )
        `when`(seasonRepository.findByLeagueIdAndIsActiveTrue(nbaLeagueId)).thenReturn(season)
    }

    @Test
    fun `mapBasketballStatus should correctly map quarter and live details to LIVE`() {
        assertEquals(MatchStatus.LIVE, syncService.mapBasketballStatus("LIVE"))
        assertEquals(MatchStatus.LIVE, syncService.mapBasketballStatus("IN"))
        assertEquals(MatchStatus.LIVE, syncService.mapBasketballStatus("10:24 - 3rd"))
        assertEquals(MatchStatus.LIVE, syncService.mapBasketballStatus("End of 1st"))
        assertEquals(MatchStatus.LIVE, syncService.mapBasketballStatus("Halftime"))
        assertEquals(MatchStatus.LIVE, syncService.mapBasketballStatus("Q4"))
        assertEquals(MatchStatus.LIVE, syncService.mapBasketballStatus("OT"))

        assertEquals(MatchStatus.FINISHED, syncService.mapBasketballStatus("Final"))
        assertEquals(MatchStatus.FINISHED, syncService.mapBasketballStatus("Final/OT"))

        assertEquals(MatchStatus.SCHEDULED, syncService.mapBasketballStatus("7:30 PM EST"))
        assertEquals(MatchStatus.SCHEDULED, syncService.mapBasketballStatus("SCHEDULED"))
    }

    @Test
    fun `fetchFromApiBasketball should prioritize ESPN and filter out Pre-Season matches`() {
        val nowStr = Instant.now().toString()
        val espnGames = listOf(
            EspnBasketballGame(
                externalId = "espn-1",
                date = nowStr,
                homeTeamName = "Boston Celtics",
                awayTeamName = "Miami Heat",
                homeTeamLogoUrl = null,
                awayTeamLogoUrl = null,
                homeScore = 105,
                awayScore = 98,
                statusShort = "LIVE",
                periodScoresJson = "{\"home\":[28,25,26,26],\"away\":[24,24,25,25]}",
                phase = "Temporada Regular",
                seasonType = 2
            ),
            EspnBasketballGame(
                externalId = "espn-2",
                date = nowStr,
                homeTeamName = "Los Angeles Lakers",
                awayTeamName = "Golden State Warriors",
                homeTeamLogoUrl = null,
                awayTeamLogoUrl = null,
                homeScore = 80,
                awayScore = 75,
                statusShort = "FINAL",
                periodScoresJson = null,
                phase = "Pré-Temporada",
                seasonType = 1
            )
        )
        `when`(espnBasketballClient.fetchNbaScoreboard()).thenReturn(espnGames)
        `when`(espnBasketballClient.fetchNbaScoreboard(any())).thenReturn(espnGames)

        val result = syncService.fetchFromApiBasketball(basketballId, nbaLeagueId)

        assertEquals(1, result.size, "Should only return regular season match, ignoring pre-season")
        assertEquals("Boston Celtics", result[0].homeTeamName)
        assertEquals("Miami Heat", result[0].awayTeamName)
        assertEquals(MatchStatus.LIVE, result[0].status)
        assertNotNull(result[0].periodScoresJson)
    }

    @Test
    fun `performUpsert should not overwrite matches between same teams if dates differ by more than 48 hours`() {
        val now = Instant.now()
        val novemberMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = basketballId,
            leagueId = nbaLeagueId,
            seasonId = seasonId,
            homeTeamName = "Boston Celtics",
            awayTeamName = "Los Angeles Lakers",
            kickoffTime = now.minusSeconds(86400 * 60), // 60 dias atrás
            status = MatchStatus.FINISHED,
            homeScore = 115,
            awayScore = 110,
            phase = "Temporada Regular"
        )
        `when`(matchRepository.findByLeagueId(nbaLeagueId)).thenReturn(listOf(novemberMatch))

        val marchMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = basketballId,
            leagueId = nbaLeagueId,
            seasonId = seasonId,
            homeTeamName = "Boston Celtics",
            awayTeamName = "Los Angeles Lakers",
            kickoffTime = now.plusSeconds(86400 * 5), // 5 dias no futuro
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null,
            phase = "Temporada Regular"
        )

        syncService.performUpsert(nbaLeagueId, listOf(marchMatch))

        // Deve salvar a nova partida sem sobrescrever o id da partida de novembro
        verify(matchRepository).saveAll(argThat<List<MatchJpaEntity>> { list ->
            list.size == 1 && list[0].id == marchMatch.id && list[0].id != novemberMatch.id
        })
    }
}
