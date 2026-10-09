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

        `when`(pandaScoreClient.fetchMatches(leagueIdOrSlug = "cblol", searchTerm = "CBLOL", videogameSlug = "league-of-legends"))
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

        `when`(pandaScoreClient.fetchMatches(leagueIdOrSlug = "cblol", searchTerm = "CBLOL", videogameSlug = "league-of-legends"))
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

    @Test
    fun `should reconcile teams with esports suffixes like FURIA and FURIA Esports`() {
        val existingMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "FURIA",
            awayTeamName = "LOUD",
            kickoffTime = Instant.parse("2026-07-02T16:00:00Z"),
            status = MatchStatus.LIVE,
            homeScore = null,
            awayScore = null
        )
        `when`(matchRepository.findByLeagueId(cblolLeagueId)).thenReturn(listOf(existingMatch))

        val incomingFinished = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "FURIA Esports",
            awayTeamName = "LOUD Esports",
            kickoffTime = Instant.parse("2026-07-02T16:00:00Z"),
            status = MatchStatus.FINISHED,
            homeScore = 2,
            awayScore = 0
        )

        syncService.performUpsert(cblolLeagueId, listOf(incomingFinished))

        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MatchJpaEntity>>
        verify(matchRepository, atLeastOnce()).saveAll(captor.capture())

        val savedList = captor.value
        assertEquals(1, savedList.size)
        val updated = savedList.first()

        assertEquals(existingMatch.id, updated.id, "Must reconcile and update existing match ID even with suffix variation")
        assertEquals(MatchStatus.FINISHED, updated.status)
        assertEquals(2, updated.homeScore)
        assertEquals(0, updated.awayScore)
    }

    @Test
    fun `should actively query provider for pending LIVE match and conclude it if provider confirms finished`() {
        val liveMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "FURIA",
            awayTeamName = "paiN Gaming",
            kickoffTime = Instant.parse("2026-07-03T16:00:00Z"),
            status = MatchStatus.LIVE,
            homeScore = null,
            awayScore = null
        )
        `when`(matchRepository.findByLeagueId(cblolLeagueId)).thenReturn(listOf(liveMatch))

        val providerFinishedGame = PandaScoreMatchResponse(
            id = 555666,
            name = "FURIA vs paiN Gaming",
            begin_at = "2026-07-03T16:00:00Z",
            status = "finished",
            opponents = listOf(
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 10, name = "FURIA")),
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 20, name = "paiN Gaming"))
            ),
            results = listOf(
                PandaScoreResult(team_id = 10, score = 2),
                PandaScoreResult(team_id = 20, score = 1)
            )
        )

        `when`(pandaScoreClient.searchMatchesByTeam("FURIA", "league-of-legends"))
            .thenReturn(listOf(providerFinishedGame))

        // performUpsert com lista incoming vazia (simulando que o jogo não veio na listagem geral)
        syncService.performUpsert(cblolLeagueId, emptyList())

        val singleCaptor = ArgumentCaptor.forClass(MatchJpaEntity::class.java)
        verify(matchRepository).save(singleCaptor.capture())

        val concludedMatch = singleCaptor.value
        assertEquals(liveMatch.id, concludedMatch.id)
        assertEquals(MatchStatus.FINISHED, concludedMatch.status)
        assertEquals(2, concludedMatch.homeScore)
        assertEquals(1, concludedMatch.awayScore)
        verify(eventPublisher).publishEvent(any(com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent::class.java))
    }

    @Test
    fun `should purge orphaned LIVE duplicate if FINISHED match already exists for same teams`() {
        val finishedMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "FURIA",
            awayTeamName = "LOUD",
            kickoffTime = Instant.parse("2026-07-04T16:00:00Z"),
            status = MatchStatus.FINISHED,
            homeScore = 2,
            awayScore = 1
        )
        val orphanedLiveDuplicate = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "FURIA Esports",
            awayTeamName = "LOUD Esports",
            kickoffTime = Instant.parse("2026-07-04T16:00:00Z"),
            status = MatchStatus.LIVE,
            homeScore = null,
            awayScore = null
        )
        `when`(matchRepository.findByLeagueId(cblolLeagueId)).thenReturn(listOf(finishedMatch, orphanedLiveDuplicate))

        syncService.performUpsert(cblolLeagueId, emptyList())

        val deleteCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MatchJpaEntity>>
        verify(matchRepository).deleteAll(deleteCaptor.capture())

        val deletedList = deleteCaptor.value
        assertTrue(deletedList.any { it.id == orphanedLiveDuplicate.id }, "Orphaned LIVE duplicate must be purged")
    }

    @Test
    fun `should auto-conclude stale LIVE match when running for more than 6 hours with a score`() {
        val eightHoursAgo = Instant.now().minus(8, ChronoUnit.HOURS)
        val staleLiveMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "LOUD",
            awayTeamName = "paiN Gaming",
            kickoffTime = eightHoursAgo,
            status = MatchStatus.LIVE,
            homeScore = 2,
            awayScore = 1
        )
        `when`(matchRepository.findByLeagueId(cblolLeagueId)).thenReturn(listOf(staleLiveMatch))
        `when`(pandaScoreClient.searchMatchesByTeam("LOUD", "league-of-legends")).thenReturn(emptyList())
        `when`(pandaScoreClient.searchMatchesByTeam("paiN Gaming", "league-of-legends")).thenReturn(emptyList())

        syncService.performUpsert(cblolLeagueId, emptyList())

        val saveCaptor = ArgumentCaptor.forClass(MatchJpaEntity::class.java)
        verify(matchRepository).save(saveCaptor.capture())

        val saved = saveCaptor.value
        assertEquals(staleLiveMatch.id, saved.id)
        assertEquals(MatchStatus.FINISHED, saved.status)
        assertEquals(2, saved.homeScore)
        assertEquals(1, saved.awayScore)
        verify(eventPublisher).publishEvent(any(com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent::class.java))
    }

    @Test
    fun `should purge out-of-season matches regardless of seasonId`() {
        val outOfSeasonMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = UUID.randomUUID(), // Temporada antiga
            homeTeamName = "LOUD",
            awayTeamName = "paiN Gaming",
            kickoffTime = Instant.parse("2024-05-10T16:00:00Z"), // 2024
            status = MatchStatus.FINISHED,
            homeScore = 2,
            awayScore = 0
        )
        `when`(matchRepository.findByLeagueId(cblolLeagueId)).thenReturn(listOf(outOfSeasonMatch))

        syncService.performUpsert(cblolLeagueId, emptyList())

        val deleteCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MatchJpaEntity>>
        verify(matchRepository).deleteAll(deleteCaptor.capture())

        val deletedList = deleteCaptor.value
        assertTrue(deletedList.any { it.id == outOfSeasonMatch.id }, "Matches outside active season window must be purged")
    }

    @Test
    fun `should mark match with 0x0 as CANCELLED instead of FINISHED in eSports`() {
        val gameFinishedZeroScore = PandaScoreMatchResponse(
            id = 201,
            name = "LEC vs FAR",
            begin_at = "2026-06-15T16:00:00Z",
            status = "finished",
            opponents = listOf(
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 50, name = "CBLOL Team A")),
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 51, name = "CBLOL Team B"))
            ),
            results = listOf(PandaScoreResult(team_id = 50, score = 0), PandaScoreResult(team_id = 51, score = 0)),
            serie = PandaScoreSerie(full_name = "CBLOL 2026")
        )

        `when`(pandaScoreClient.fetchMatches(leagueIdOrSlug = "cblol", searchTerm = "CBLOL", videogameSlug = "league-of-legends"))
            .thenReturn(listOf(gameFinishedZeroScore))

        val result = syncService.fetchFromPandaScore(syncService.esportsId, cblolLeagueId)

        assertEquals(1, result.size)
        assertEquals(MatchStatus.CANCELLED, result.first().status, "0x0 in eSports must be treated as CANCELLED")
        assertNull(result.first().homeScore)
        assertNull(result.first().awayScore)
    }

    @Test
    fun `should reject matches from previous splits like Split 2 2025`() {
        val gameFrom2025Split = PandaScoreMatchResponse(
            id = 202,
            name = "Team A vs Team B",
            begin_at = "2026-02-10T16:00:00Z",
            status = "finished",
            opponents = listOf(
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 50, name = "CBLOL Team A")),
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 51, name = "CBLOL Team B"))
            ),
            results = listOf(PandaScoreResult(team_id = 50, score = 2), PandaScoreResult(team_id = 51, score = 1)),
            serie = PandaScoreSerie(full_name = "Split 2 2025")
        )

        `when`(pandaScoreClient.fetchMatches(leagueIdOrSlug = "cblol", searchTerm = "CBLOL", videogameSlug = "league-of-legends"))
            .thenReturn(listOf(gameFrom2025Split))

        val result = syncService.fetchFromPandaScore(syncService.esportsId, cblolLeagueId)

        assertTrue(result.isEmpty(), "Matches explicitly referring to 2025 split must be rejected")
    }

    @Test
    fun `should auto-cancel scheduled match that passed more than 24 hours ago`() {
        val threeDaysAgo = Instant.now().minus(3, ChronoUnit.DAYS)
        val staleScheduledMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = syncService.esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "CUP",
            awayTeamName = "DPL",
            kickoffTime = threeDaysAgo,
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null
        )

        `when`(matchRepository.findByLeagueId(cblolLeagueId)).thenReturn(listOf(staleScheduledMatch))

        syncService.performUpsert(cblolLeagueId, emptyList())

        val saveCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MatchJpaEntity>>
        verify(matchRepository).saveAll(saveCaptor.capture())

        val savedList = saveCaptor.value
        val cancelled = savedList.find { it.id == staleScheduledMatch.id }
        assertNotNull(cancelled)
        assertEquals(MatchStatus.CANCELLED, cancelled?.status, "Stale SCHEDULED match must be cancelled")
    }

    @Test
    fun `should reject games belonging to foreign leagues like DPL or LEC`() {
        val foreignGame = PandaScoreMatchResponse(
            id = 205,
            name = "CUP vs DPL",
            begin_at = "2026-06-15T16:00:00Z",
            status = "not_started",
            opponents = listOf(
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 80, name = "CUP")),
                PandaScoreOpponentWrapper(PandaScoreTeam(id = 81, name = "DPL"))
            ),
            league = PandaScoreLeague(id = 999, name = "Dota Professional League", slug = "dpl")
        )

        `when`(pandaScoreClient.fetchMatches(leagueIdOrSlug = "cblol", searchTerm = "CBLOL", videogameSlug = "league-of-legends"))
            .thenReturn(listOf(foreignGame))

        val result = syncService.fetchFromPandaScore(syncService.esportsId, cblolLeagueId)

        assertTrue(result.isEmpty(), "Games belonging to a foreign league must be rejected")
    }
}
