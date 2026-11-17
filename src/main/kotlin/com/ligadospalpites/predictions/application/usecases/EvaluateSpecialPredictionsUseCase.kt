package com.ligadospalpites.predictions.application.usecases

import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.predictions.domain.events.UserScoreUpdateDto
import com.ligadospalpites.predictions.domain.ports.SpecialPredictionRepository
import com.ligadospalpites.predictions.domain.services.SpecialPredictionScoringService
import com.ligadospalpites.shared.domain.models.LeagueId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EvaluateSpecialPredictionsUseCase(
    private val specialPredictionRepository: SpecialPredictionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    data class Command(
        val leagueId: LeagueId,
        val championTeamId: String,
        val secondPlaceTeamId: String,
        val thirdPlaceTeamId: String,
        val fourthPlaceTeamId: String
    )

    data class Result(
        val predictionsProcessed: Int,
        val usersAwarded: Int
    )

    @Transactional
    fun execute(command: Command): Result {
        val uncompleted = specialPredictionRepository.findByLeagueIdAndIsProcessedFalse(command.leagueId)
        if (uncompleted.isEmpty()) {
            return Result(predictionsProcessed = 0, usersAwarded = 0)
        }

        val correctValues = mapOf(
            "CHAMPION" to command.championTeamId,
            "SECOND_PLACE" to command.secondPlaceTeamId,
            "THIRD_PLACE" to command.thirdPlaceTeamId,
            "FOURTH_PLACE" to command.fourthPlaceTeamId
        )

        val scoreUpdates = mutableListOf<UserScoreUpdateDto>()
        val updatedPredictions = uncompleted.map { pred ->
            val earnedPoints = SpecialPredictionScoringService.calculateEarnedPoints(
                type = pred.type.value,
                predictionValue = pred.predictionValue,
                correctValues = correctValues
            )

            if (earnedPoints > 0) {
                scoreUpdates.add(UserScoreUpdateDto(pred.userId.value, earnedPoints))
            }

            pred.awardPoints(earnedPoints)
        }

        specialPredictionRepository.saveAll(updatedPredictions)

        if (scoreUpdates.isNotEmpty()) {
            val aggregatedScores = scoreUpdates.groupBy { it.userId }.map { (userId, updates) ->
                UserScoreUpdateDto(userId, updates.sumOf { it.pointsGained })
            }
            eventPublisher.publishEvent(PredictionsProcessedEvent(command.leagueId.value, aggregatedScores))
        }

        val uniqueUsersAwarded = scoreUpdates.map { it.userId }.distinct().size
        return Result(
            predictionsProcessed = updatedPredictions.size,
            usersAwarded = uniqueUsersAwarded
        )
    }
}
