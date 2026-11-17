package com.ligadospalpites.predictions.application.usecases

import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.predictions.domain.events.UserScoreUpdateDto
import com.ligadospalpites.predictions.domain.ports.PredictionRepository
import com.ligadospalpites.predictions.domain.services.ScoringEngine
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PredictionsProcessorService(
    private val predictionRepository: PredictionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(PredictionsProcessorService::class.java)

    @Transactional
    @EventListener
    fun onMatchFinished(event: MatchFinishedEvent) {
        log.info("Processing predictions for match: ${event.matchId} (${event.homeTeamName} x ${event.awayTeamName})")
        val predictions = predictionRepository.findByMatchId(MatchId(event.matchId))

        if (predictions.isEmpty()) {
            log.info("No predictions found for match ${event.matchId}. Skipping scoring computation.")
            return
        }

        val scoreUpdates = mutableListOf<UserScoreUpdateDto>()

        val updatedPredictions = predictions.map { pred ->
            if (!pred.isProcessed) {
                val points = ScoringEngine.calculateMatchPoints(
                    predHome = pred.predictedHomeScore,
                    predAway = pred.predictedAwayScore,
                    realHome = event.homeScore,
                    realAway = event.awayScore,
                    isFinal = false,
                    sportId = event.sportId
                )

                log.debug("User ${pred.userId} earned $points points for match ${event.matchId}")
                scoreUpdates.add(UserScoreUpdateDto(pred.userId.value, points))
                pred.awardPoints(points)
            } else {
                pred
            }
        }

        predictionRepository.saveAll(updatedPredictions)
        log.info("Successfully updated ${scoreUpdates.size} predictions for match ${event.matchId}")

        if (scoreUpdates.isNotEmpty()) {
            log.info("Publishing PredictionsProcessedEvent for league ${event.leagueId} with ${scoreUpdates.size} score updates.")
            eventPublisher.publishEvent(PredictionsProcessedEvent(event.leagueId, scoreUpdates))
        }
    }
}
