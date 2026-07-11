package com.ligadospalpites.predictions.domain.models

import java.time.Instant
import java.util.UUID

data class Prediction(
    val id: UUID,
    val userId: UUID,
    val matchId: UUID,
    val leagueId: UUID,
    val predictedHomeScore: Int,
    val predictedAwayScore: Int,
    val pointsAwarded: Int = 0,
    val calculatedAt: Instant? = null,
    val isProcessed: Boolean = false,
    val createdAt: Instant = Instant.now()
)
