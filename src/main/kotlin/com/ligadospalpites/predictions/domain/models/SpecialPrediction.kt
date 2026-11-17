package com.ligadospalpites.predictions.domain.models

import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.UserId
import java.time.Instant

data class SpecialPrediction(
    val id: SpecialPredictionId = SpecialPredictionId.random(),
    val userId: UserId,
    val leagueId: LeagueId,
    val type: SpecialPredictionType,
    val predictionValue: String,
    val pointsAwarded: Int = 0,
    val isProcessed: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    fun awardPoints(points: Int): SpecialPrediction = copy(
        pointsAwarded = points,
        isProcessed = true
    )

    companion object {
        fun create(
            userId: UserId,
            leagueId: LeagueId,
            type: SpecialPredictionType,
            predictionValue: String,
            createdAt: Instant = Instant.now()
        ): SpecialPrediction {
            return SpecialPrediction(
                id = SpecialPredictionId.random(),
                userId = userId,
                leagueId = leagueId,
                type = type,
                predictionValue = predictionValue,
                pointsAwarded = 0,
                isProcessed = false,
                createdAt = createdAt
            )
        }
    }
}
