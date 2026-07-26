package com.ligadospalpites.sportsfeed.application.usecases

import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.persistence.LeagueJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SyncOrchestratorTest {

    private lateinit var leagueRepository: SpringDataLeagueRepository
    private lateinit var matchRepository: SpringDataMatchRepository
    private lateinit var leagueSyncService1: LeagueSyncService
    private lateinit var leagueSyncService2: LeagueSyncService
    private lateinit var orchestrator: SyncOrchestrator

    private val sportId = UUID.randomUUID()
    private val leagueId1 = UUID.randomUUID()
    private val leagueId2 = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        leagueRepository = mock(SpringDataLeagueRepository::class.java)
        matchRepository = mock(SpringDataMatchRepository::class.java)
        leagueSyncService1 = mock(LeagueSyncService::class.java)
        leagueSyncService2 = mock(LeagueSyncService::class.java)

        orchestrator = SyncOrchestrator(
            syncServices = listOf(leagueSyncService1, leagueSyncService2),
            leagueRepository = leagueRepository,
            matchRepository = matchRepository
        )
    }

    @Test
    fun `should delegate syncMatches to supporting service`() {
        `when`(leagueSyncService1.supports(sportId, leagueId1)).thenReturn(true)
        `when`(leagueSyncService2.supports(sportId, leagueId1)).thenReturn(false)

        orchestrator.syncMatches(sportId, leagueId1)

        verify(leagueSyncService1).syncMatches(sportId, leagueId1)
        verify(leagueSyncService2, never()).syncMatches(sportId, leagueId1)
    }

    @Test
    fun `should throw exception when no supporting service is found`() {
        `when`(leagueSyncService1.supports(sportId, leagueId1)).thenReturn(false)
        `when`(leagueSyncService2.supports(sportId, leagueId1)).thenReturn(false)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            orchestrator.syncMatches(sportId, leagueId1)
        }

        assertEquals("No sync service found for sport $sportId and league $leagueId1", exception.message)
    }

    @Test
    fun `should sync all active leagues when force is true`() {
        val activeLeagues = listOf(
            LeagueJpaEntity(id = leagueId1, name = "League 1", sportId = sportId, isActive = true),
            LeagueJpaEntity(id = leagueId2, name = "League 2", sportId = sportId, isActive = true)
        )
        `when`(leagueRepository.findByIsActiveTrue()).thenReturn(activeLeagues)
        `when`(leagueSyncService1.supports(sportId, leagueId1)).thenReturn(true)
        `when`(leagueSyncService2.supports(sportId, leagueId2)).thenReturn(true)

        val results = orchestrator.syncAllActiveLeagues(force = true)

        assertEquals(2, results.size)
        assertEquals("SUCCESS", results[0]["status"])
        assertEquals("SUCCESS", results[1]["status"])

        verify(leagueSyncService1).syncMatches(sportId, leagueId1)
        verify(leagueSyncService2).syncMatches(sportId, leagueId2)
        verifyNoInteractions(matchRepository)
    }

    @Test
    fun `should skip leagues without active or upcoming matches when force is false`() {
        val activeLeagues = listOf(
            LeagueJpaEntity(id = leagueId1, name = "League 1", sportId = sportId, isActive = true),
            LeagueJpaEntity(id = leagueId2, name = "League 2", sportId = sportId, isActive = true)
        )
        `when`(leagueRepository.findByIsActiveTrue()).thenReturn(activeLeagues)
        `when`(leagueSyncService1.supports(sportId, leagueId1)).thenReturn(true)
        `when`(leagueSyncService2.supports(sportId, leagueId2)).thenReturn(true)

        // Liga 1 tem um jogo LIVE
        val matchLive = MatchJpaEntity(
            id = UUID.randomUUID(), sportId = sportId, leagueId = leagueId1, seasonId = UUID.randomUUID(),
            homeTeamName = "A", awayTeamName = "B", kickoffTime = Instant.now().minus(1, ChronoUnit.HOURS),
            status = MatchStatus.LIVE
        )
        `when`(matchRepository.findByLeagueId(leagueId1)).thenReturn(listOf(matchLive))

        // Liga 2 só tem jogos SCHEDULED no futuro distante (ex: amanhã)
        val matchScheduledFar = MatchJpaEntity(
            id = UUID.randomUUID(), sportId = sportId, leagueId = leagueId2, seasonId = UUID.randomUUID(),
            homeTeamName = "C", awayTeamName = "D", kickoffTime = Instant.now().plus(24, ChronoUnit.HOURS),
            status = MatchStatus.SCHEDULED
        )
        `when`(matchRepository.findByLeagueId(leagueId2)).thenReturn(listOf(matchScheduledFar))

        val results = orchestrator.syncAllActiveLeagues(force = false)

        assertEquals(2, results.size)

        val res1 = results.find { it["leagueId"] == leagueId1 }!!
        assertEquals("SUCCESS", res1["status"])

        val res2 = results.find { it["leagueId"] == leagueId2 }!!
        assertEquals("SKIPPED", res2["status"])
        assertEquals("No active window matches", res2["reason"])

        verify(leagueSyncService1).syncMatches(sportId, leagueId1)
        verify(leagueSyncService2, never()).syncMatches(sportId, leagueId2)
    }

    @Test
    fun `should handle individual league sync failure without crashing the whole execution`() {
        val activeLeagues = listOf(
            LeagueJpaEntity(id = leagueId1, name = "League 1", sportId = sportId, isActive = true),
            LeagueJpaEntity(id = leagueId2, name = "League 2", sportId = sportId, isActive = true)
        )
        `when`(leagueRepository.findByIsActiveTrue()).thenReturn(activeLeagues)
        `when`(leagueSyncService1.supports(sportId, leagueId1)).thenReturn(true)
        `when`(leagueSyncService2.supports(sportId, leagueId2)).thenReturn(true)

        // Liga 1 estoura erro
        `doThrow`(RuntimeException("API Down")).`when`(leagueSyncService1).syncMatches(sportId, leagueId1)

        val results = orchestrator.syncAllActiveLeagues(force = true)

        assertEquals(2, results.size)

        val res1 = results.find { it["leagueId"] == leagueId1 }!!
        assertEquals("FAILED", res1["status"])
        assertEquals("API Down", res1["error"])

        val res2 = results.find { it["leagueId"] == leagueId2 }!!
        assertEquals("SUCCESS", res2["status"])

        verify(leagueSyncService1).syncMatches(sportId, leagueId1)
        verify(leagueSyncService2).syncMatches(sportId, leagueId2)
    }
}
