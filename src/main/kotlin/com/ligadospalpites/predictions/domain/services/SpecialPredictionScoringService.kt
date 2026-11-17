package com.ligadospalpites.predictions.domain.services

import com.ligadospalpites.predictions.domain.models.SpecialPredictionType

object SpecialPredictionScoringService {

    val DEFAULT_POINTS_BY_TYPE: Map<String, Int> = mapOf(
        SpecialPredictionType.CHAMPION.value to 50,
        SpecialPredictionType.SECOND_PLACE.value to 50,
        SpecialPredictionType.THIRD_PLACE.value to 50,
        SpecialPredictionType.FOURTH_PLACE.value to 50
    )

    fun calculateEarnedPoints(
        type: String,
        predictionValue: String,
        correctValues: Map<String, String>,
        pointsTable: Map<String, Int> = DEFAULT_POINTS_BY_TYPE
    ): Int {
        val correctTarget = correctValues[type]
        return if (correctTarget != null && predictionValue.equals(correctTarget, ignoreCase = true)) {
            pointsTable[type] ?: 0
        } else {
            0
        }
    }
}
