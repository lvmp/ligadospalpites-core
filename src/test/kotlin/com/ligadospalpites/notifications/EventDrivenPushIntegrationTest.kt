package com.ligadospalpites.notifications

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.groups.infrastructure.persistence.GroupJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.GroupMemberJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupRepository
import com.ligadospalpites.notifications.domain.ports.Device
import com.ligadospalpites.notifications.domain.ports.DeviceRepository
import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.predictions.infrastructure.persistence.PredictionJpaEntity
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent
import com.ligadospalpites.sportsfeed.domain.events.MatchGoalEvent
import com.ligadospalpites.sportsfeed.domain.events.MatchStartedEvent
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.persistence.FootballWorldCupSyncService
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

@Component
class TestEventCapturer {
    val matchStartedEvents = Collections.synchronizedList(mutableListOf<MatchStartedEvent>())
    val matchGoalEvents = Collections.synchronizedList(mutableListOf<MatchGoalEvent>())
    val matchFinishedEvents = Collections.synchronizedList(mutableListOf<MatchFinishedEvent>())
    val predictionsProcessedEvents = Collections.synchronizedList(mutableListOf<PredictionsProcessedEvent>())

    fun clear() {
        matchStartedEvents.clear()
        matchGoalEvents.clear()
        matchFinishedEvents.clear()
        predictionsProcessedEvents.clear()
    }

    @EventListener
    fun captureStarted(event: MatchStartedEvent) {
        matchStartedEvents.add(event)
    }

    @EventListener
    fun captureGoal(event: MatchGoalEvent) {
        matchGoalEvents.add(event)
    }

    @EventListener
    fun captureFinished(event: MatchFinishedEvent) {
        matchFinishedEvents.add(event)
    }

    @EventListener
    fun captureProcessed(event: PredictionsProcessedEvent) {
        predictionsProcessedEvents.add(event)
    }
}

class EventDrivenPushIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var deviceRepository: DeviceRepository

    @Autowired
    private lateinit var groupRepository: SpringDataGroupRepository

    @Autowired
    private lateinit var groupMemberRepository: SpringDataGroupMemberRepository

    @Autowired
    private lateinit var matchRepository: SpringDataMatchRepository

    @Autowired
    private lateinit var predictionRepository: SpringDataPredictionRepository

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var testEventCapturer: TestEventCapturer

    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val worldCupLeagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")
    private val worldCupSeasonId = UUID.fromString("50c22998-33b2-4d9a-ba02-4be71a1be992")

    @BeforeEach
    fun setUp() {
        testEventCapturer.clear()

        predictionRepository.deleteAll()
        matchRepository.deleteAll()
        groupMemberRepository.deleteAll()
        groupRepository.deleteAll()

        deviceRepository.findAll().forEach { deviceRepository.delete(it) }
    }

    @Test
    fun `should calculate prediction points on MatchFinishedEvent and publish PredictionsProcessedEvent`() {
        // 1. Create a user, device and prediction
        val user = userRepository.save(User(UUID.randomUUID(), "fuid-event-1", "event1@test.com", "Event User 1"))
        deviceRepository.save(Device(id = UUID.randomUUID(), userId = user.id, deviceId = UUID.randomUUID(), fcmToken = "fcm-event-1", deviceType = "ANDROID"))

        val match = matchRepository.save(MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = footballId,
            leagueId = worldCupLeagueId,
            seasonId = worldCupSeasonId,
            homeTeamName = "Brasil",
            awayTeamName = "França",
            kickoffTime = Instant.now().minusSeconds(3600),
            status = MatchStatus.LIVE
        ))

        // Create a prediction with 2 x 1 (Brazil wins)
        val prediction = predictionRepository.save(PredictionJpaEntity(
            id = UUID.randomUUID(),
            userId = user.id,
            matchId = match.id,
            leagueId = match.leagueId,
            predictedHomeScore = 2,
            predictedAwayScore = 1,
            pointsAwarded = 0,
            isProcessed = false,
            createdAt = Instant.now()
        ))

        // 2. Publish MatchFinishedEvent with final score 2 x 1 (Brazil wins) - EXACT MATCH (25 points)
        eventPublisher.publishEvent(MatchFinishedEvent(
            matchId = match.id,
            homeTeamName = "Brasil",
            awayTeamName = "França",
            homeScore = 2,
            awayScore = 1,
            sportId = footballId,
            leagueId = worldCupLeagueId
        ))

        // Wait a brief moment for any async/transaction listener execution
        Thread.sleep(300)

        // 3. Verify prediction calculation
        val updatedPrediction = predictionRepository.findById(prediction.id).orElseThrow()
        assertTrue(updatedPrediction.isProcessed)
        assertEquals(25, updatedPrediction.pointsAwarded)
        assertNotNull(updatedPrediction.calculatedAt)

        // 4. Verify PredictionsProcessedEvent was captured
        assertEquals(1, testEventCapturer.predictionsProcessedEvents.size)
        val processedEvent = testEventCapturer.predictionsProcessedEvents[0]
        assertEquals(worldCupLeagueId, processedEvent.leagueId)
        assertEquals(1, processedEvent.scores.size)
        assertEquals(user.id, processedEvent.scores[0].userId)
        assertEquals(25, processedEvent.scores[0].pointsGained)
    }

    @Test
    fun `should publish correct events during performUpsert based on match status and score differences`() {
        // Manually construct the sync service to test change detection
        val mockFootballDataClient = mock(com.ligadospalpites.sportsfeed.infrastructure.client.FootballDataClient::class.java)
        val mockApiFootballClient = mock(com.ligadospalpites.sportsfeed.infrastructure.client.ApiFootballClient::class.java)
        val mockNewsApiClient = mock(com.ligadospalpites.sportsfeed.infrastructure.client.NewsApiClient::class.java)
        val mockSeasonRepository = mock(com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataSeasonRepository::class.java)

        val syncService = FootballWorldCupSyncService(
            matchRepository = matchRepository,
            footballDataClient = mockFootballDataClient,
            apiFootballClient = mockApiFootballClient,
            newsApiClient = mockNewsApiClient,
            redisTemplate = redisTemplate,
            seasonRepository = mockSeasonRepository,
            eventPublisher = eventPublisher
        )

        val matchId = UUID.randomUUID()
        val existingMatch = MatchJpaEntity(
            id = matchId,
            sportId = footballId,
            leagueId = worldCupLeagueId,
            seasonId = worldCupSeasonId,
            homeTeamName = "Brasil",
            awayTeamName = "Gana",
            kickoffTime = Instant.now().plusSeconds(7200),
            status = MatchStatus.SCHEDULED,
            homeScore = null,
            awayScore = null
        )
        matchRepository.save(existingMatch)

        // Scenario 1: Transition SCHEDULED -> LIVE (Match Started)
        val incomingLive = MatchJpaEntity(
            id = existingMatch.id,
            sportId = existingMatch.sportId,
            leagueId = existingMatch.leagueId,
            seasonId = existingMatch.seasonId,
            homeTeamName = existingMatch.homeTeamName,
            awayTeamName = existingMatch.awayTeamName,
            kickoffTime = existingMatch.kickoffTime,
            status = MatchStatus.LIVE,
            homeScore = existingMatch.homeScore,
            awayScore = existingMatch.awayScore,
            phase = existingMatch.phase,
            updatedAt = Instant.now()
        )
        syncService.performUpsert(listOf(incomingLive))

        assertEquals(1, testEventCapturer.matchStartedEvents.size)
        val startEvent = testEventCapturer.matchStartedEvents[0]
        assertEquals(matchId, startEvent.matchId)
        assertEquals("Brasil", startEvent.homeTeamName)
        assertEquals("Gana", startEvent.awayTeamName)

        testEventCapturer.clear()

        // Scenario 2: Score changes from 0-0 to 1-0 while LIVE (Match Goal)
        val matchInDbLive = matchRepository.findById(matchId).get()
        assertEquals(MatchStatus.LIVE, matchInDbLive.status)

        val incomingGoal = MatchJpaEntity(
            id = matchInDbLive.id,
            sportId = matchInDbLive.sportId,
            leagueId = matchInDbLive.leagueId,
            seasonId = matchInDbLive.seasonId,
            homeTeamName = matchInDbLive.homeTeamName,
            awayTeamName = matchInDbLive.awayTeamName,
            kickoffTime = matchInDbLive.kickoffTime,
            status = matchInDbLive.status,
            homeScore = 1,
            awayScore = 0,
            phase = matchInDbLive.phase,
            updatedAt = Instant.now()
        )
        syncService.performUpsert(listOf(incomingGoal))

        assertEquals(1, testEventCapturer.matchGoalEvents.size)
        val goalEvent = testEventCapturer.matchGoalEvents[0]
        assertEquals(matchId, goalEvent.matchId)
        assertEquals(1, goalEvent.homeScore)
        assertEquals(0, goalEvent.awayScore)
        assertEquals("HOME", goalEvent.scoringTeam)

        testEventCapturer.clear()

        // Scenario 3: Transition LIVE -> FINISHED (Match Ended)
        val matchInDbWithGoal = matchRepository.findById(matchId).get()
        val incomingFinished = MatchJpaEntity(
            id = matchInDbWithGoal.id,
            sportId = matchInDbWithGoal.sportId,
            leagueId = matchInDbWithGoal.leagueId,
            seasonId = matchInDbWithGoal.seasonId,
            homeTeamName = matchInDbWithGoal.homeTeamName,
            awayTeamName = matchInDbWithGoal.awayTeamName,
            kickoffTime = matchInDbWithGoal.kickoffTime,
            status = MatchStatus.FINISHED,
            homeScore = matchInDbWithGoal.homeScore,
            awayScore = matchInDbWithGoal.awayScore,
            phase = matchInDbWithGoal.phase,
            updatedAt = Instant.now()
        )
        syncService.performUpsert(listOf(incomingFinished))

        assertEquals(1, testEventCapturer.matchFinishedEvents.size)
        val finishedEvent = testEventCapturer.matchFinishedEvents[0]
        assertEquals(matchId, finishedEvent.matchId)
        assertEquals(1, finishedEvent.homeScore)
        assertEquals(0, finishedEvent.awayScore)
    }
}
