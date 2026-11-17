package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.client.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.*

class PandaScoreSyncServiceUnitTest {

    private val matchRepository = mock(SpringDataMatchRepository::class.java)
    private val pandaScoreClient = mock(PandaScoreClient::class.java)
    private val seasonRepository = mock(SpringDataSeasonRepository::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val leagueRepository = mock(SpringDataLeagueRepository::class.java)

    private lateinit var syncService: PandaScoreSyncService

    private val cblolLeagueId = UUID.fromString("7c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    private val esportsId = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    private val seasonId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        syncService = PandaScoreSyncService(
            matchRepository = matchRepository,
            pandaScoreClient = pandaScoreClient,
            seasonRepository = seasonRepository,
            eventPublisher = eventPublisher,
            leagueRepository = leagueRepository
        )

        val season = SeasonJpaEntity(
            id = seasonId,
            leagueId = cblolLeagueId,
            name = "2026",
            startDate = Instant.now().minusSeconds(86400 * 30),
            endDate = Instant.now().plusSeconds(86400 * 60),
            isActive = true
        )
        `when`(seasonRepository.findByLeagueIdAndIsActiveTrue(cblolLeagueId)).thenReturn(season)
    }

    @Test
    fun `should calculate live map score from sub-games when match is running`() {
        val homeTeam = PandaScoreTeam(id = 100, name = "LOUD")
        val awayTeam = PandaScoreTeam(id = 200, name = "paiN Gaming")
        val nowStr = Instant.now().toString()

        val runningMatch = PandaScoreMatchResponse(
            id = 12345,
            name = "LOUD vs paiN Gaming",
            begin_at = nowStr,
            status = "running",
            number_of_games = 3,
            league = PandaScoreLeague(id = 1, name = "CBLOL", slug = "cblol"),
            serie = PandaScoreSerie(full_name = "Split 1 2026"),
            opponents = listOf(
                PandaScoreOpponentWrapper(homeTeam),
                PandaScoreOpponentWrapper(awayTeam)
            ),
            results = emptyList(), // Results array is typically empty during live game
            games = listOf(
                PandaScoreGame(id = 1, position = 1, status = "finished", winner = PandaScoreWinner(id = 100, type = "Team")),
                PandaScoreGame(id = 2, position = 2, status = "running", winner = null)
            ),
            streams_list = listOf(
                PandaScoreStream(raw_url = "https://twitch.tv/cblol", embed_url = null)
            )
        )

        `when`(pandaScoreClient.fetchMatches(any(), any(), any(), anyInt(), anyInt())).thenReturn(listOf(runningMatch))

        val result = syncService.fetchFromPandaScore(esportsId, cblolLeagueId)

        assertEquals(1, result.size)
        val match = result[0]
        assertEquals("LOUD", match.homeTeamName)
        assertEquals("paiN Gaming", match.awayTeamName)
        assertEquals(MatchStatus.LIVE, match.status)
        assertEquals(1, match.homeScore, "LOUD should have 1 map won from games array")
        assertEquals(0, match.awayScore, "paiN Gaming should have 0 maps won")
        assertEquals("https://twitch.tv/cblol", match.streamUrl)
    }

    @Test
    fun `should preserve finished match status when maps are present even if results array is not populated`() {
        val homeTeam = PandaScoreTeam(id = 100, name = "FURIA")
        val awayTeam = PandaScoreTeam(id = 200, name = "MIBR")
        val nowStr = Instant.now().minusSeconds(1800).toString()

        val finishedMatch = PandaScoreMatchResponse(
            id = 54321,
            name = "FURIA vs MIBR",
            begin_at = nowStr,
            status = "finished",
            number_of_games = 3,
            league = PandaScoreLeague(id = 1, name = "CBLOL", slug = "cblol"),
            serie = PandaScoreSerie(full_name = "Split 1 2026"),
            opponents = listOf(
                PandaScoreOpponentWrapper(homeTeam),
                PandaScoreOpponentWrapper(awayTeam)
            ),
            results = emptyList(),
            games = listOf(
                PandaScoreGame(id = 1, position = 1, status = "finished", winner = PandaScoreWinner(id = 100, type = "Team")),
                PandaScoreGame(id = 2, position = 2, status = "finished", winner = PandaScoreWinner(id = 100, type = "Team"))
            )
        )

        `when`(pandaScoreClient.fetchMatches(any(), any(), any(), anyInt(), anyInt())).thenReturn(listOf(finishedMatch))

        val result = syncService.fetchFromPandaScore(esportsId, cblolLeagueId)

        assertEquals(1, result.size)
        val match = result[0]
        assertEquals(MatchStatus.FINISHED, match.status)
        assertEquals(2, match.homeScore)
        assertEquals(0, match.awayScore)
    }
}
