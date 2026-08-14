package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.infrastructure.client.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID

class FootballGenericSyncServiceUnitTest {

    private fun <T> anyObj(): T {
        ArgumentMatchers.any<T>()
        return uninitialized()
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    private val matchRepository = mock(SpringDataMatchRepository::class.java)
    private val seasonRepository = mock(SpringDataSeasonRepository::class.java)
    private val leagueRepository = mock(SpringDataLeagueRepository::class.java)
    private val footballDataClient = mock(FootballDataClient::class.java)
    private val apiFootballClient = mock(ApiFootballClient::class.java)
    private val espnSoccerClient = mock(EspnSoccerClient::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)

    private lateinit var syncService: FootballGenericSyncService

    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val premierLeagueId = UUID.fromString("827d043c-62c2-402c-b011-3ba2849e7b23")
    private val laLigaId = UUID.fromString("9284ca51-bb54-47c1-841f-81ab28120fa2")
    private val bundesligaId = UUID.fromString("8acdf011-fbde-4122-83bc-c46b1ba847de")
    private val ligue1Id = UUID.fromString("7acdf011-fbde-4122-83bc-c46b1ba847de")
    private val serieAId = UUID.fromString("9acdf011-fbde-4122-83bc-c46b1ba847de")
    private val eredivisieId = UUID.fromString("aacdf011-fbde-4122-83bc-c46b1ba847de")
    private val primeiraLigaId = UUID.fromString("bacdf011-fbde-4122-83bc-c46b1ba847de")
    private val championshipId = UUID.fromString("5acdf011-fbde-4122-83bc-c46b1ba847de")
    private val eurocopaId = UUID.fromString("6acdf011-fbde-4122-83bc-c46b1ba847de")
    private val championsLeagueId = UUID.fromString("e2d03a11-b9db-44ab-ba02-411a0c0bcf14")
    private val brasileiraoId = UUID.fromString("3dbd8422-9e22-4411-b0db-b06d0421da6a")
    private val libertadoresId = UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de")
    private val copaDoBrasilId = UUID.fromString("b3cdf011-fbde-4122-83bc-c46b1ba847de")

    @BeforeEach
    fun setUp() {
        reset(matchRepository, seasonRepository, leagueRepository, footballDataClient, apiFootballClient, espnSoccerClient)
        syncService = FootballGenericSyncService(
            matchRepository,
            seasonRepository,
            leagueRepository,
            footballDataClient,
            apiFootballClient,
            espnSoccerClient,
            eventPublisher,
            FootballGenericSyncService(
                matchRepository, seasonRepository, leagueRepository, footballDataClient, apiFootballClient, espnSoccerClient, eventPublisher, mock(FootballGenericSyncService::class.java)
            )
        )

        val activeSeason = SeasonJpaEntity(
            id = UUID.randomUUID(),
            leagueId = premierLeagueId,
            name = "2026/2027",
            startDate = java.time.Instant.now(),
            endDate = java.time.Instant.now().plusSeconds(86400 * 300),
            isActive = true,
            externalSeasonCode = 2026
        )
        `when`(seasonRepository.findByLeagueIdAndIsActiveTrue(anyObj())).thenReturn(activeSeason)
        `when`(leagueRepository.findById(anyObj())).thenReturn(Optional.of(LeagueJpaEntity(id = premierLeagueId, name = "Test League", sportId = footballId, isActive = true)))
    }

    @Test
    fun `should route all 11 free football-data org leagues to FootballDataClient`() {
        val testLeagues = listOf(
            premierLeagueId to "PL",
            laLigaId to "PD",
            bundesligaId to "BL1",
            ligue1Id to "FL1",
            serieAId to "SA",
            eredivisieId to "DED",
            primeiraLigaId to "PPL",
            championshipId to "ELC",
            eurocopaId to "EC",
            championsLeagueId to "CL",
            brasileiraoId to "BSA"
        )

        val dummyFdMatch = FootballDataMatch(
            id = 1L,
            utcDate = "2026-08-15T15:00:00Z",
            status = "SCHEDULED",
            stage = "REGULAR_SEASON",
            homeTeam = FootballDataTeam(10L, "Home Team", "Home"),
            awayTeam = FootballDataTeam(20L, "Away Team", "Away")
        )

        for ((leagueId, expectedCode) in testLeagues) {
            `when`(footballDataClient.fetchMatches(expectedCode, 2026)).thenReturn(listOf(dummyFdMatch))
            
            syncService.syncMatches(footballId, leagueId)

            verify(footballDataClient, times(1)).fetchMatches(expectedCode, 2026)
        }

        verifyNoInteractions(espnSoccerClient)
    }

    @Test
    fun `should route Copa Libertadores to EspnSoccerClient`() {
        val espnEvent = EspnSoccerEvent(
            id = "100",
            date = "2026-04-10T22:00:00Z",
            competitions = listOf(
                EspnSoccerCompetition(
                    id = "100",
                    date = "2026-04-10T22:00:00Z",
                    status = EspnSoccerStatus(EspnSoccerStatusType(state = "pre")),
                    competitors = listOf(
                        EspnSoccerCompetitor("1", "home", false, EspnSoccerTeam("1", displayName = "Flamengo")),
                        EspnSoccerCompetitor("2", "away", false, EspnSoccerTeam("2", displayName = "Palmeiras"))
                    )
                )
            )
        )
        `when`(espnSoccerClient.fetchSoccerMatches("conmebol.libertadores", 2026, false)).thenReturn(listOf(espnEvent))

        syncService.syncMatches(footballId, libertadoresId)

        verify(espnSoccerClient, times(1)).fetchSoccerMatches("conmebol.libertadores", 2026, false)
        verifyNoInteractions(footballDataClient)
    }

    @Test
    fun `should route Copa do Brasil to EspnSoccerClient`() {
        val espnEvent = EspnSoccerEvent(
            id = "200",
            date = "2026-05-15T21:30:00Z",
            competitions = listOf(
                EspnSoccerCompetition(
                    id = "200",
                    date = "2026-05-15T21:30:00Z",
                    status = EspnSoccerStatus(EspnSoccerStatusType(state = "pre")),
                    competitors = listOf(
                        EspnSoccerCompetitor("10", "home", false, EspnSoccerTeam("10", displayName = "São Paulo")),
                        EspnSoccerCompetitor("20", "away", false, EspnSoccerTeam("20", displayName = "Corinthians"))
                    )
                )
            )
        )
        `when`(espnSoccerClient.fetchSoccerMatches("bra.copa_do_brazil", 2026, false)).thenReturn(listOf(espnEvent))

        syncService.syncMatches(footballId, copaDoBrasilId)

        verify(espnSoccerClient, times(1)).fetchSoccerMatches("bra.copa_do_brazil", 2026, false)
        verifyNoInteractions(footballDataClient)
    }
}
