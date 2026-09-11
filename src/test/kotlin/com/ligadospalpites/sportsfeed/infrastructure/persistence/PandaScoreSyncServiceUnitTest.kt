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

    private val esportsId = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    private val cblolLeagueId = UUID.fromString("7c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
    private val season2026Id = UUID.fromString("1d6a4c33-3112-4fb2-a6bc-cd8a0cbf42ef")

    private val seasonStartDate = Instant.parse("2026-01-15T00:00:00Z")
    private val seasonEndDate = Instant.parse("2026-10-31T23:59:59Z")

    @BeforeEach
    fun setUp() {
        reset(matchRepository, pandaScoreClient, seasonRepository, eventPublisher, leagueRepository)
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
    fun `should pass active season startDate and endDate to PandaScoreClient and ignore matches from 2020 while keeping 2026 finished and scheduled matches`() {
        // Given PandaScore returns 3 matches:
        // 1. A 2020 historical match
        // 2. A 2026 finished match (past game of current season)
        // 3. A 2026 scheduled future match
        val mockGames = listOf(
            PandaScoreMatchResponse(
                id = 1001,
                name = "paiN Gaming vs INTZ",
                begin_at = "2020-08-15T16:00:00Z", // Historical 2020
                status = "finished",
                number_of_games = 3,
                opponents = listOf(
                    PandaScoreOpponentWrapper(PandaScoreTeam(1, "paiN Gaming")),
                    PandaScoreOpponentWrapper(PandaScoreTeam(2, "INTZ"))
                ),
                results = listOf(PandaScoreResult(1, 2), PandaScoreResult(2, 0))
            ),
            PandaScoreMatchResponse(
                id = 2001,
                name = "LOUD vs paiN Gaming",
                begin_at = "2026-03-10T16:00:00Z", // 2026 past game of current season
                status = "finished",
                number_of_games = 3,
                opponents = listOf(
                    PandaScoreOpponentWrapper(PandaScoreTeam(10, "LOUD")),
                    PandaScoreOpponentWrapper(PandaScoreTeam(1, "paiN Gaming"))
                ),
                results = listOf(PandaScoreResult(10, 2), PandaScoreResult(1, 1))
            ),
            PandaScoreMatchResponse(
                id = 2002,
                name = "FURIA vs RED Canids",
                begin_at = "2026-09-20T16:00:00Z", // 2026 upcoming game
                status = "not_started",
                number_of_games = 3,
                opponents = listOf(
                    PandaScoreOpponentWrapper(PandaScoreTeam(20, "FURIA")),
                    PandaScoreOpponentWrapper(PandaScoreTeam(30, "RED Canids"))
                ),
                results = emptyList()
            )
        )

        `when`(pandaScoreClient.fetchMatches(
            leagueSlug = "league-of-legends-cblol",
            startDate = seasonStartDate,
            endDate = seasonEndDate
        )).thenReturn(mockGames)

        // When
        val matches = syncService.fetchFromPandaScore(esportsId, cblolLeagueId)

        // Then: verify PandaScoreClient was called with the exact season dates
        verify(pandaScoreClient).fetchMatches(
            leagueSlug = "league-of-legends-cblol",
            startDate = seasonStartDate,
            endDate = seasonEndDate
        )

        // Then: 2020 match must be discarded, but 2026 past game and future game must be preserved
        assertEquals(2, matches.size, "Should contain exactly the 2 matches from 2026, discarding the 2020 match")

        val match2020 = matches.find { it.homeTeamName == "paiN Gaming" && it.awayTeamName == "INTZ" }
        assertNull(match2020, "2020 historical match must NOT be included")

        val finished2026 = matches.find { it.homeTeamName == "LOUD" && it.awayTeamName == "paiN Gaming" }
        assertNotNull(finished2026, "2026 past finished game must be retained")
        assertEquals(MatchStatus.FINISHED, finished2026!!.status)
        assertEquals(2, finished2026.homeScore)
        assertEquals(1, finished2026.awayScore)
        assertEquals(season2026Id, finished2026.seasonId)
        assertEquals(Instant.parse("2026-03-10T16:00:00Z"), finished2026.kickoffTime)

        val scheduled2026 = matches.find { it.homeTeamName == "FURIA" && it.awayTeamName == "RED Canids" }
        assertNotNull(scheduled2026, "2026 upcoming game must be retained")
        assertEquals(MatchStatus.SCHEDULED, scheduled2026!!.status)
        assertEquals(season2026Id, scheduled2026.seasonId)
        assertEquals(Instant.parse("2026-09-20T16:00:00Z"), scheduled2026.kickoffTime)
    }

    @Test
    fun `performUpsert should prevent regression from FINISHED to SCHEDULED`() {
        val existingMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "LOUD",
            awayTeamName = "paiN Gaming",
            kickoffTime = Instant.parse("2026-03-10T16:00:00Z"),
            status = MatchStatus.FINISHED,
            homeScore = 2,
            awayScore = 1,
            phase = "Fase Principal"
        )
        `when`(matchRepository.findByLeagueId(cblolLeagueId)).thenReturn(listOf(existingMatch))

        // Incoming match has SCHEDULED (e.g. stale external feed)
        val incomingMatch = MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = esportsId,
            leagueId = cblolLeagueId,
            seasonId = season2026Id,
            homeTeamName = "LOUD",
            awayTeamName = "paiN Gaming",
            kickoffTime = Instant.parse("2026-03-10T16:00:00Z"),
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null,
            phase = "Fase Principal"
        )

        syncService.performUpsert(cblolLeagueId, listOf(incomingMatch))

        verify(matchRepository).saveAll(argThat<List<MatchJpaEntity>> { savedList ->
            val saved = savedList.firstOrNull()
            saved != null && saved.id == existingMatch.id && saved.status == MatchStatus.FINISHED
        })
    }
}
