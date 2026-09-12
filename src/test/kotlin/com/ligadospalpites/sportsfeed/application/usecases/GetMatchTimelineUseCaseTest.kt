package com.ligadospalpites.sportsfeed.application.usecases

import com.fasterxml.jackson.databind.ObjectMapper
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.domain.models.MatchTimelineEvent
import com.ligadospalpites.sportsfeed.domain.models.TimelineEventType
import com.ligadospalpites.sportsfeed.infrastructure.client.EspnMatchSummaryResponse
import com.ligadospalpites.sportsfeed.infrastructure.client.EspnSoccerClient
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchEventJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchEventRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import com.ligadospalpites.users.infrastructure.persistence.UserEntitlementJpaEntity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.security.access.AccessDeniedException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class GetMatchTimelineUseCaseTest {

    private val matchRepository: SpringDataMatchRepository = mock(SpringDataMatchRepository::class.java)
    private val matchEventRepository: SpringDataMatchEventRepository = mock(SpringDataMatchEventRepository::class.java)
    private val entitlementRepository: SpringDataUserEntitlementRepository = mock(SpringDataUserEntitlementRepository::class.java)
    private val espnSoccerClient: EspnSoccerClient = mock(EspnSoccerClient::class.java)
    private val redisTemplate: StringRedisTemplate = mock(StringRedisTemplate::class.java)
    private val valueOps: ValueOperations<String, String> = mock()

    private val objectMapper = ObjectMapper()
    private lateinit var useCase: GetMatchTimelineUseCase

    private val matchId = UUID.randomUUID()
    private val sportId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val leagueId = UUID.fromString("3dbd8422-9e22-4411-b0db-b06d0421da6a")
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)

        useCase = GetMatchTimelineUseCase(
            matchRepository = matchRepository,
            matchEventRepository = matchEventRepository,
            entitlementRepository = entitlementRepository,
            espnSoccerClient = espnSoccerClient,
            redisTemplate = redisTemplate,
            objectMapper = objectMapper
        )
    }

    @Test
    fun `should throw AccessDeniedException when user has no active entitlement`() {
        val match = MatchJpaEntity(
            id = matchId,
            sportId = sportId,
            leagueId = leagueId,
            seasonId = UUID.randomUUID(),
            homeTeamName = "Flamengo",
            awayTeamName = "Palmeiras",
            status = MatchStatus.LIVE
        )
        `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(match))
        `when`(entitlementRepository.findByUserId(userId)).thenReturn(emptyList())

        val exception = assertThrows<AccessDeniedException> {
            useCase.execute(matchId, userId)
        }
        assertEquals("PREMIUM_REQUIRED", exception.message)
    }

    @Test
    fun `should allow access when user has valid PREMIUM entitlement`() {
        val match = MatchJpaEntity(
            id = matchId,
            sportId = sportId,
            leagueId = leagueId,
            seasonId = UUID.randomUUID(),
            homeTeamName = "Flamengo",
            awayTeamName = "Palmeiras",
            status = MatchStatus.LIVE,
            homeScore = 1,
            awayScore = 0
        )
        val entitlement = UserEntitlementJpaEntity(
            id = UUID.randomUUID(),
            userId = userId,
            entitlementType = EntitlementType.PREMIUM,
            expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
        )

        `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(match))
        `when`(entitlementRepository.findByUserId(userId)).thenReturn(listOf(entitlement))
        `when`(valueOps.get("match:$matchId:timeline")).thenReturn(null)
        `when`(espnSoccerClient.fetchMatchSummary(anyString(), anyString())).thenReturn(null)

        val timeline = useCase.execute(matchId, userId)
        assertFalse(timeline.isEmpty())
        assertTrue(timeline.any { it.eventType == TimelineEventType.GOAL })
    }

    @Test
    fun `should return persisted database events for FINISHED match without calling external API`() {
        val match = MatchJpaEntity(
            id = matchId,
            sportId = sportId,
            leagueId = leagueId,
            seasonId = UUID.randomUUID(),
            homeTeamName = "Flamengo",
            awayTeamName = "Fluminense",
            status = MatchStatus.FINISHED
        )
        val entitlement = UserEntitlementJpaEntity(
            id = UUID.randomUUID(),
            userId = userId,
            entitlementType = EntitlementType.PREMIUM,
            expiresAt = null
        )
        val dbEvent = MatchEventJpaEntity(
            id = UUID.randomUUID(),
            matchId = matchId,
            minute = 90,
            eventType = TimelineEventType.PERIOD_END,
            description = "Fim de jogo histórico!",
            isImportant = true
        )

        `when`(matchRepository.findById(matchId)).thenReturn(Optional.of(match))
        `when`(entitlementRepository.findByUserId(userId)).thenReturn(listOf(entitlement))
        `when`(matchEventRepository.findByMatchIdOrderByMinuteDescCreatedAtDesc(matchId)).thenReturn(listOf(dbEvent))

        val timeline = useCase.execute(matchId, userId)
        assertEquals(1, timeline.size)
        assertEquals(90, timeline[0].minute)
        assertEquals("Fim de jogo histórico!", timeline[0].description)

        verifyNoInteractions(espnSoccerClient)
    }
}
