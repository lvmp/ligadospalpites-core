package com.ligadospalpites.predictions.infrastructure.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_special_predictions")
class SpecialPredictionJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Column(name = "league_id", nullable = false)
    val leagueId: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 50)
    val type: String = "",

    @Column(name = "prediction_value", nullable = false, length = 150)
    val predictionValue: String = "",

    @Column(name = "points_awarded", nullable = false)
    val pointsAwarded: Int = 0,

    @Column(name = "is_processed", nullable = false)
    val isProcessed: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
