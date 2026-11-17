package com.ligadospalpites.predictions.domain.models

import com.ligadospalpites.predictions.domain.exceptions.PredictionLockedException
import com.ligadospalpites.predictions.domain.exceptions.PredictionProcessedException
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.shared.domain.models.UserId
import java.time.Instant

data class Prediction(
    val id: PredictionId = PredictionId.random(),
    val userId: UserId,
    val matchId: MatchId,
    val leagueId: LeagueId,
    val score: MatchPredictionScore,
    val pointsAwarded: Int = 0,
    val calculatedAt: Instant? = null,
    val isProcessed: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    val predictedHomeScore: Int get() = score.home.value
    val predictedAwayScore: Int get() = score.away.value

    fun updateScore(newScore: MatchPredictionScore, kickoffTime: Instant, now: Instant = Instant.now()): Prediction {
        if (isProcessed) {
            throw PredictionProcessedException()
        }
        if (now.isAfter(kickoffTime) || now == kickoffTime) {
            throw PredictionLockedException()
        }
        return copy(
            score = newScore,
            updatedAt = now
        )
    }

    fun awardPoints(points: Int, calculatedAt: Instant = Instant.now()): Prediction {
        return copy(
            pointsAwarded = points,
            calculatedAt = calculatedAt,
            isProcessed = true,
            updatedAt = calculatedAt
        )
    }

    companion object {
        fun create(
            userId: UserId,
            matchId: MatchId,
            leagueId: LeagueId,
            score: MatchPredictionScore,
            kickoffTime: Instant,
            now: Instant = Instant.now()
        ): Prediction {
            if (now.isAfter(kickoffTime) || now == kickoffTime) {
                throw PredictionLockedException()
            }
            return Prediction(
                id = PredictionId.random(),
                userId = userId,
                matchId = matchId,
                leagueId = leagueId,
                score = score,
                pointsAwarded = 0,
                calculatedAt = null,
                isProcessed = false,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
