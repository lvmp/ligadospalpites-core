package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.client.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class PandaScoreSyncServiceTest {

    private val matchRepository: SpringDataMatchRepository = mock(SpringDataMatchRepository::class.java)
    private val pandaScoreClient: PandaScoreClient = mock(PandaScoreClient::class.java)
    private val seasonRepository: SpringDataSeasonRepository = mock(SpringDataSeasonRepository::class.java)
    private val eventPublisher: ApplicationEventPublisher = mock(ApplicationEventPublisher::class.java)
    private val leagueRepository: SpringDataLeagueRepository = mock(SpringDataLeagueRepository::class.java)

    private lateinit var syncService: PandaScoreSyncService

    private val cblolLeagueId = UUID.fromString("7c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    private val season2026Id = UUID.randomUUID()
    private val seasonStartDate = Instant.parse("2026-01-15T00:00:00Z")
    private val seasonEndDate = Instant.parse("2026-10-31T23:59:59Z")

    @BeforeEach
    fun setUp() {
        syncService = PandaScoreSyncService(
            matchRepository = matchRepository,
            pandaScoreClient = pandaScoreClient,
            seasonRepository = seasonRepository,
            eventPublisher = eventPublisher,
            leagueRepository = leagueRepository
        )

        val activeSeason = SeasonJpaEntity(
            id = season2026Id,
            leagueId = cblolLeagueId,
            name = "2026",
            startDate = seasonStartDate,
            endDate = seasonEndDate,
            isActive = true,
            externalSeasonCode = 2026
        )
        `when`(seasonRepository.findByLeagueIdAndIsActiveTrue(cblolLeagueId)).thenReturn(activeSeason)
    }

    @Test
    fun `should filter out matches from previous years and only keep active season matches`() {
        val gameFrom2024 = PandaScoreMatchResponse(
            id = 101,
            name = "LOUD vs paiN (2024)",
            begin_at = "2024-05-10T16:00:00Z",
            status = "finished",
            opponents = listOf(
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 1, name = "LOUD")),
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 2, name = "paiN Gaming"))
            ),
            results = listOf(PandaScoreResult(team_id = 1, score = 2), PandaScoreResult(team_id = 2, score = 1))
        )

        val gameFrom2026 = PandaScoreMatchResponse(
            id = 102,
            name = "LOUD vs paiN (2026)",
            begin_at = "2026-06-15T16:00:00Z",
            status = "finished",
            opponents = listOf(
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 1, name = "LOUD")),
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 2, name = "paiN Gaming"))
            ),
            results = listOf(PandaScoreResult(team_id = 1, score = 3), PandaScoreResult(team_id = 2, score = 0))
        )

        `when`(pandaScoreClient.fetchMatches(searchTerm = "CBLOL", videogameSlug = "league-of-legends"))
            .thenReturn(listOf(gameFrom2024, gameFrom2026))

        val result = syncService.fetchFromPandaScore(syncService.esportsId, cblolLeagueId)

        assertEquals(1, result.size, "Should only return matches inside the active season window")
        assertEquals("2026-06-15T16:00:00Z", result.first().kickoffTime.toString())
        assertEquals(3, result.first().homeScore)
        assertEquals(0, result.first().awayScore)
    }

    @Test
    fun `should sanitize future scheduled matches so homeScore and awayScore are null even if API returns 0`() {
        val futureGameWithZeroResults = PandaScoreMatchResponse(
            id = 103,
            name = "FURIA vs RED Canids",
            begin_at = "2026-08-20T16:00:00Z",
            status = "not_started",
            opponents = listOf(
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 10, name = "FURIA")),
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 20, name = "RED Canids"))
            ),
            results = listOf(PandaScoreResult(team_id = 10, score = 0), PandaScoreResult(team_id = 20, score = 0))
        )

        `when`(pandaScoreClient.fetchMatches(searchTerm = "CBLOL", videogameSlug = "league-of-legends"))
            .thenReturn(listOf(futureGameWithZeroResults))

        val result = syncService.fetchFromPandaScore(syncService.esportsId, cblolLeagueId)

        assertEquals(1, result.size)
        val match = result.first()
        assertEquals(MatchStatus.SCHEDULED, match.status)
        assertNull(match.homeScore, "Future scheduled match MUST have null homeScore, not 0")
        assertNull(match.awayScore, "Future scheduled match MUST have null awayScore, not 0")
    }

    @Test
    fun `should perform bidirectional upsert when team order is reversed by external API`() {
        val existingMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "LOUD",
            awayTeamName = "paiN Gaming",
            kickoffTime = Instant.parse("2026-07-01T16:00:00Z"),
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null
        )
        `when`(matchRepository.findByLeagueId(cblolLeagueId)).thenReturn(listOf(existingMatch))

        // API retorna o jogo finalizado, mas com a ordem inversa dos oponentes: paiN (home) x LOUD (away)
        val incomingFinished = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "paiN Gaming",
            awayTeamName = "LOUD",
            kickoffTime = Instant.parse("2026-07-01T16:00:00Z"),
            status = MatchStatus.FINISHED,
            homeScore = 1, // paiN fez 1
            awayScore = 2  // LOUD fez 2
        )

        syncService.performUpsert(cblolLeagueId, listOf(incomingFinished))

        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MatchJpaEntity>>
        verify(matchRepository).saveAll(captor.capture())

        val savedList = captor.value
        assertEquals(1, savedList.size)
        val updated = savedList.first()

        assertEquals(existingMatch.id, updated.id, "Must update the existing match ID")
        assertEquals("LOUD", updated.homeTeamName)
        assertEquals("paiN Gaming", updated.awayTeamName)
        assertEquals(MatchStatus.FINISHED, updated.status)
        assertEquals(2, updated.homeScore, "LOUD was local homeTeam, so homeScore must be LOUD's score (2)")
        assertEquals(1, updated.awayScore, "paiN was local awayTeam, so awayScore must be paiN's score (1)")
    }
}
