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
class FootballWorldCupSyncServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var syncService: FootballWorldCupSyncService

    @Autowired
    private lateinit var matchRepository: SpringDataMatchRepository

    @MockitoBean
    private lateinit var footballDataClient: FootballDataClient

    @MockitoBean
    private lateinit var apiFootballClient: ApiFootballClient

    @MockitoBean
    private lateinit var newsApiClient: NewsApiClient

    @Autowired
    private lateinit var dashboardController: com.ligadospalpites.shared.bff.DashboardController

    private val worldCupLeagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")

    @BeforeEach
    fun setUp() {
        matchRepository.deleteAll()
    }

    @Test
    fun `should sync from FootballData when primary provider is healthy`() {
        val fdMatch = FootballDataMatch(
            id = 123L,
            utcDate = "2026-06-11T19:00:00Z",
            status = "FINISHED",
            stage = "GROUP_STAGE",
            homeTeam = FootballDataTeam(1L, "Brazil", "Brazil"),
            awayTeam = FootballDataTeam(2L, "France", "France"),
            score = FootballDataScore(FootballDataTeamScore(2, 1))
        )
        `when`(footballDataClient.fetchMatches("WC")).thenReturn(listOf(fdMatch))

        syncService.syncMatches(UUID.randomUUID(), worldCupLeagueId)

        val saved = matchRepository.findByLeagueId(worldCupLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Brasil", saved[0].homeTeamName)
        assertEquals("França", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(2, saved[0].homeScore)
        assertEquals(1, saved[0].awayScore)

        verify(footballDataClient, times(1)).fetchMatches("WC")
        verifyNoInteractions(apiFootballClient)
    }

    @Test
    fun `should fallback to ApiFootball when primary provider fails`() {
        `when`(footballDataClient.fetchMatches("WC")).thenThrow(RuntimeException("Football-Data is offline"))

        val afFixture = ApiFootballFixtureWrapper(
            fixture = ApiFootballFixture(456L, "2026-06-11T19:00:00Z", ApiFootballStatus("FT")),
            teams = ApiFootballTeams(ApiFootballTeam("Brazil"), ApiFootballTeam("France")),
            goals = ApiFootballGoals(3, 2)
        )
        `when`(apiFootballClient.fetchMatches(1, 2026)).thenReturn(listOf(afFixture))

        syncService.syncMatches(UUID.randomUUID(), worldCupLeagueId)

        val saved = matchRepository.findByLeagueId(worldCupLeagueId)
        assertEquals(1, saved.size)
        assertEquals("Brasil", saved[0].homeTeamName)
        assertEquals("França", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(3, saved[0].homeScore)
        assertEquals(2, saved[0].awayScore)

        verify(footballDataClient, times(1)).fetchMatches("WC")
        verify(apiFootballClient, times(1)).fetchMatches(1, 2026)
    }

    @Test
    fun `should perform upsert safely without deleting existing predictions or changing match UUIDs`() {
        val matchId = UUID.randomUUID()
        val oldMatch = MatchJpaEntity(
            id = matchId,
            sportId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c"),
            leagueId = worldCupLeagueId,
            homeTeamName = "Brasil",
            awayTeamName = "França",
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null
        )
        matchRepository.save(oldMatch)

        val fdMatch = FootballDataMatch(
            id = 123L,
            utcDate = "2026-06-11T19:00:00Z",
            status = "FINISHED",
            stage = "GROUP_STAGE",
            homeTeam = FootballDataTeam(1L, "Brazil", "Brazil"),
            awayTeam = FootballDataTeam(2L, "France", "France"),
            score = FootballDataScore(FootballDataTeamScore(4, 2))
        )
        `when`(footballDataClient.fetchMatches("WC")).thenReturn(listOf(fdMatch))

        syncService.syncMatches(UUID.randomUUID(), worldCupLeagueId)

        val saved = matchRepository.findByLeagueId(worldCupLeagueId)
        assertEquals(1, saved.size)
        assertEquals(matchId, saved[0].id)
        assertEquals("Brasil", saved[0].homeTeamName)
        assertEquals("França", saved[0].awayTeamName)
        assertEquals(MatchStatus.FINISHED, saved[0].status)
        assertEquals(4, saved[0].homeScore)
        assertEquals(2, saved[0].awayScore)
    }

    @Test
    fun `should sync news from NewsAPI and save to Redis under correct key and serve via BFF`() {
        val sportId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
        redisTemplate.delete("news:$sportId")

        val article = NewsApiArticle(
            title = "Seleção Brasileira inicia treinos na Copa",
            url = "https://ge.globo.com/copa/treinos.html",
            urlToImage = "https://ge.globo.com/treino.png"
        )
        `when`(newsApiClient.fetchNews()).thenReturn(listOf(article))

        syncService.syncNews(sportId)

        val cached = redisTemplate.opsForValue().get("news:$sportId")
        assertNotNull(cached)
        assertTrue(cached!!.contains("Seleção Brasileira inicia treinos na Copa"))

        val dashboardResponse = dashboardController.getDashboard(null).join()
        val news = dashboardResponse.body?.news
        assertNotNull(news)
        assertEquals(1, news!!.size)
        assertEquals("Seleção Brasileira inicia treinos na Copa", news[0].title)
    }

    @Test
    fun `should fallback to previous cache and avoid error when NewsAPI is down`() {
        val sportId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
        redisTemplate.delete("news:$sportId")

        redisTemplate.opsForValue().set("news:$sportId", "[{\"title\":\"Cache Antigo\",\"url\":\"https://site.com\",\"urlToImage\":\"https://site.com/img.png\"}]")

        `when`(newsApiClient.fetchNews()).thenThrow(RuntimeException("NewsAPI Limit Exceeded"))

        assertDoesNotThrow {
            syncService.syncNews(sportId)
        }

        val cached = redisTemplate.opsForValue().get("news:$sportId")
        assertNotNull(cached)
        assertTrue(cached!!.contains("Cache Antigo"))
    }
}
