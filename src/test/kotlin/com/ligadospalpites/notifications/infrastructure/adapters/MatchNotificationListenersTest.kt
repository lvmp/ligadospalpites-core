package com.ligadospalpites.notifications.infrastructure.adapters

import com.ligadospalpites.notifications.application.usecases.NotificationDispatcherService
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.NotificationTarget
import com.ligadospalpites.predictions.infrastructure.persistence.PredictionJpaEntity
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.sportsfeed.domain.events.MatchStartedEvent
import com.ligadospalpites.sportsfeed.infrastructure.persistence.LeagueJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional
import java.util.UUID

class MatchNotificationListenersTest {

    private lateinit var dispatcherService: NotificationDispatcherService
    private lateinit var predictionRepository: SpringDataPredictionRepository
    private lateinit var leagueRepository: SpringDataLeagueRepository
    private lateinit var listener: MatchNotificationListeners

    private val matchId = UUID.randomUUID()
    private val sportId = UUID.randomUUID()
    private val leagueId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        dispatcherService = mock(NotificationDispatcherService::class.java)
        predictionRepository = mock(SpringDataPredictionRepository::class.java)
        leagueRepository = mock(SpringDataLeagueRepository::class.java)

        listener = MatchNotificationListeners(
            dispatcherService = dispatcherService,
            predictionRepository = predictionRepository,
            leagueRepository = leagueRepository
        )

        val predictionEntity = PredictionJpaEntity(
            id = UUID.randomUUID(),
            userId = userId,
            matchId = matchId,
            leagueId = leagueId,
            predictedHomeScore = 2,
            predictedAwayScore = 1
        )
        `when`(predictionRepository.findByMatchId(matchId)).thenReturn(listOf(predictionEntity))
    }

    @Test
    fun `should dispatch notification with pelo Campeonato Brasileiro for Campeonato Brasileiro match`() {
        val leagueEntity = LeagueJpaEntity(id = leagueId, name = "Campeonato Brasileiro", sportId = sportId, isActive = true)
        `when`(leagueRepository.findById(leagueId)).thenReturn(Optional.of(leagueEntity))

        val event = MatchStartedEvent(
            matchId = matchId,
            homeTeamName = "Flamengo",
            awayTeamName = "Palmeiras",
            sportId = sportId,
            leagueId = leagueId
        )

        listener.onMatchStarted(event)

        verify(dispatcherService).dispatch(
            target = NotificationTarget.USER,
            targetId = userId,
            title = "⚽ JOGO INICIADO: Flamengo x Palmeiras",
            content = "A bola está rolando pelo Campeonato Brasileiro! Fique ligado no seu palpite.",
            channels = listOf(NotificationChannel.PUSH)
        )
    }

    @Test
    fun `should dispatch notification with pela Copa Libertadores for Copa Libertadores match`() {
        val leagueEntity = LeagueJpaEntity(id = leagueId, name = "Copa Libertadores", sportId = sportId, isActive = true)
        `when`(leagueRepository.findById(leagueId)).thenReturn(Optional.of(leagueEntity))

        val event = MatchStartedEvent(
            matchId = matchId,
            homeTeamName = "São Paulo",
            awayTeamName = "River Plate",
            sportId = sportId,
            leagueId = leagueId
        )

        listener.onMatchStarted(event)

        verify(dispatcherService).dispatch(
            target = NotificationTarget.USER,
            targetId = userId,
            title = "⚽ JOGO INICIADO: São Paulo x River Plate",
            content = "A bola está rolando pela Copa Libertadores! Fique ligado no seu palpite.",
            channels = listOf(NotificationChannel.PUSH)
        )
    }

    @Test
    fun `should dispatch fallback notification when league is not found`() {
        `when`(leagueRepository.findById(leagueId)).thenReturn(Optional.empty())

        val event = MatchStartedEvent(
            matchId = matchId,
            homeTeamName = "Time A",
            awayTeamName = "Time B",
            sportId = sportId,
            leagueId = leagueId
        )

        listener.onMatchStarted(event)

        verify(dispatcherService).dispatch(
            target = NotificationTarget.USER,
            targetId = userId,
            title = "⚽ JOGO INICIADO: Time A x Time B",
            content = "A bola está rolando! Fique ligado no seu palpite.",
            channels = listOf(NotificationChannel.PUSH)
        )
    }
}
