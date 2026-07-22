package com.ligadospalpites.notifications.infrastructure.adapters

import com.ligadospalpites.notifications.application.usecases.NotificationDispatcherService
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.NotificationTarget
import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.sportsfeed.domain.events.MatchGoalEvent
import com.ligadospalpites.sportsfeed.domain.events.MatchStartedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class MatchNotificationListeners(
    private val dispatcherService: NotificationDispatcherService,
    private val predictionRepository: SpringDataPredictionRepository
) {

    private val log = LoggerFactory.getLogger(MatchNotificationListeners::class.java)

    @Async
    @EventListener
    fun onMatchStarted(event: MatchStartedEvent) {
        log.info("Processing MatchStartedEvent push notifications for match ${event.matchId}")
        val predictions = predictionRepository.findByMatchId(event.matchId)

        predictions.forEach { prediction ->
            try {
                dispatcherService.dispatch(
                    target = NotificationTarget.USER,
                    targetId = prediction.userId,
                    title = "⚽ JOGO INICIADO: ${event.homeTeamName} x ${event.awayTeamName}",
                    content = "A bola está rolando pela Copa do Mundo! Fique ligado no seu palpite.",
                    channels = listOf(NotificationChannel.PUSH)
                )
            } catch (e: Exception) {
                log.error("Failed to dispatch match started notification to user ${prediction.userId}", e)
            }
        }
    }

    @Async
    @EventListener
    fun onMatchGoal(event: MatchGoalEvent) {
        log.info("Processing MatchGoalEvent push notification for match ${event.matchId}")
        val scoreText = "${event.homeScore} x ${event.awayScore}"
        val scoringTeamName = if (event.scoringTeam == "HOME") event.homeTeamName else event.awayTeamName

        dispatcherService.dispatch(
            target = NotificationTarget.SPORT,
            targetId = event.sportId,
            title = "⚽ GOOOL DO $scoringTeamName! ($scoreText)",
            content = "Placar atualizado: ${event.homeTeamName} ${event.homeScore} x ${event.awayScore} ${event.awayTeamName}.",
            channels = listOf(NotificationChannel.PUSH)
        )
    }

    @Async
    @EventListener
    fun onPredictionsProcessed(event: PredictionsProcessedEvent) {
        log.info("Processing PredictionsProcessedEvent push notifications for league ${event.leagueId}")

        event.scores.forEach { update ->
            try {
                dispatcherService.dispatch(
                    target = NotificationTarget.USER,
                    targetId = update.userId,
                    title = "🏁 JOGO ENCERRADO & PONTOS CALCULADOS!",
                    content = "Fim de jogo! Você conquistou ${update.pointsGained} pontos com o seu palpite.",
                    channels = listOf(NotificationChannel.PUSH)
                )
            } catch (e: Exception) {
                log.error("Failed to dispatch prediction points notification to user ${update.userId}", e)
            }
        }
    }
}
