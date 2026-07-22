package com.ligadospalpites.predictions.application.usecases

import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.predictions.domain.events.UserScoreUpdateDto
import com.ligadospalpites.predictions.domain.services.ScoringEngine
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class PredictionsProcessorService(
    private val predictionRepository: SpringDataPredictionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(PredictionsProcessorService::class.java)

    @Transactional
    @EventListener
    fun onMatchFinished(event: MatchFinishedEvent) {
        log.info("Processing predictions for match: ${event.matchId} (${event.homeTeamName} x ${event.awayTeamName})")
        val predictions = predictionRepository.findByMatchId(event.matchId)

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
                    isFinal = false // Default to standard rule matches
                )

                log.debug("User ${pred.userId} earned $points points for match ${event.matchId}")
                scoreUpdates.add(UserScoreUpdateDto(pred.userId, points))

                // Copy with points awarded and set isProcessed to true
                // In Kotlin/JPA JpaEntity can be updated by creating a new entity with same ID
                com.ligadospalpites.predictions.infrastructure.persistence.PredictionJpaEntity(
                    id = pred.id,
                    userId = pred.userId,
                    matchId = pred.matchId,
                    leagueId = pred.leagueId,
                    predictedHomeScore = pred.predictedHomeScore,
                    predictedAwayScore = pred.predictedAwayScore,
                    pointsAwarded = points,
                    calculatedAt = Instant.now(),
                    isProcessed = true,
                    createdAt = pred.createdAt
                )
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
