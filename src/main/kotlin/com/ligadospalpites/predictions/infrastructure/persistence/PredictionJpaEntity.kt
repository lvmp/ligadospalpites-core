package com.ligadospalpites.predictions.infrastructure.persistence

import com.ligadospalpites.predictions.domain.models.Prediction
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_predictions")
class PredictionJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Column(name = "match_id", nullable = false)
    val matchId: UUID = UUID.randomUUID(),

    @Column(name = "league_id", nullable = false)
    val leagueId: UUID = UUID.randomUUID(),

    @Column(name = "predicted_home_score", nullable = false)
    val predictedHomeScore: Int = 0,

    @Column(name = "predicted_away_score", nullable = false)
    val predictedAwayScore: Int = 0,

    @Column(name = "points_awarded", nullable = false)
    val pointsAwarded: Int = 0,

    @Column(name = "calculated_at")
    val calculatedAt: Instant? = null,

    @Column(name = "is_processed", nullable = false)
    val isProcessed: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): Prediction = Prediction(
        id = id,
        userId = userId,
        matchId = matchId,
        leagueId = leagueId,
        predictedHomeScore = predictedHomeScore,
        predictedAwayScore = predictedAwayScore,
        pointsAwarded = pointsAwarded,
        calculatedAt = calculatedAt,
        isProcessed = isProcessed,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(domain: Prediction): PredictionJpaEntity = PredictionJpaEntity(
            id = domain.id,
            userId = domain.userId,
            matchId = domain.matchId,
            leagueId = domain.leagueId,
            predictedHomeScore = domain.predictedHomeScore,
            predictedAwayScore = domain.predictedAwayScore,
            pointsAwarded = domain.pointsAwarded,
            calculatedAt = domain.calculatedAt,
            isProcessed = domain.isProcessed,
            createdAt = domain.createdAt
        )
    }
}
