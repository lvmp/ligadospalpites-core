package com.ligadospalpites.predictions.domain.events

import java.util.UUID

data class UserScoreUpdateDto(val userId: UUID, val pointsGained: Int)

data class PredictionsProcessedEvent(
    val leagueId: UUID,
    val scores: List<UserScoreUpdateDto>
)
