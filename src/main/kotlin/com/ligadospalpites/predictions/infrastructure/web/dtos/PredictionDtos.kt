package com.ligadospalpites.predictions.infrastructure.web.dtos

import com.ligadospalpites.predictions.domain.models.Prediction
import com.ligadospalpites.predictions.domain.models.SpecialPrediction
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class MatchPredictionRequest(
    @field:NotNull(message = "O ID da partida é obrigatório.")
    val matchId: UUID,

    @field:NotNull(message = "O placar previsto do time mandante é obrigatório.")
    @field:Min(value = 0, message = "O placar mandante não pode ser negativo.")
    @field:Max(value = 99, message = "O placar mandante excede o limite máximo.")
    val predictedHomeScore: Int,

    @field:NotNull(message = "O placar previsto do time visitante é obrigatório.")
    @field:Min(value = 0, message = "O placar visitante não pode ser negativo.")
    @field:Max(value = 99, message = "O placar visitante excede o limite máximo.")
    val predictedAwayScore: Int
)

data class SpecialPredictionRequest(
    @field:NotNull(message = "O ID da liga é obrigatório.")
    val leagueId: UUID,

    @field:NotBlank(message = "O tipo de palpite especial é obrigatório.")
    val type: String,

    @field:NotBlank(message = "O valor do palpite especial é obrigatório.")
    val predictionValue: String
)

data class SpecialPredictionEvaluationRequest(
    @field:NotNull(message = "O ID da liga é obrigatório.")
    val leagueId: UUID,

    @field:NotBlank(message = "O time campeão é obrigatório.")
    val championTeamId: String,

    @field:NotBlank(message = "O time vice-campeão é obrigatório.")
    val secondPlaceTeamId: String,

    @field:NotBlank(message = "O time terceiro colocado é obrigatório.")
    val thirdPlaceTeamId: String,

    @field:NotBlank(message = "O time quarto colocado é obrigatório.")
    val fourthPlaceTeamId: String
)

data class PredictionResponse(
    val id: UUID,
    val userId: UUID,
    val matchId: UUID,
    val leagueId: UUID,
    val predictedHomeScore: Int,
    val predictedAwayScore: Int,
    val pointsAwarded: Int,
    val calculatedAt: Instant?,
    val isProcessed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun fromDomain(domain: Prediction): PredictionResponse = PredictionResponse(
            id = domain.id.value,
            userId = domain.userId.value,
            matchId = domain.matchId.value,
            leagueId = domain.leagueId.value,
            predictedHomeScore = domain.predictedHomeScore,
            predictedAwayScore = domain.predictedAwayScore,
            pointsAwarded = domain.pointsAwarded,
            calculatedAt = domain.calculatedAt,
            isProcessed = domain.isProcessed,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}

data class SpecialPredictionResponse(
    val id: UUID,
    val userId: UUID,
    val leagueId: UUID,
    val type: String,
    val predictionValue: String,
    val pointsAwarded: Int,
    val isProcessed: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun fromDomain(domain: SpecialPrediction): SpecialPredictionResponse = SpecialPredictionResponse(
            id = domain.id.value,
            userId = domain.userId.value,
            leagueId = domain.leagueId.value,
            type = domain.type.value,
            predictionValue = domain.predictionValue,
            pointsAwarded = domain.pointsAwarded,
            isProcessed = domain.isProcessed,
            createdAt = domain.createdAt
        )
    }
}

data class SpecialPredictionEvaluationResponse(
    val status: String = "SUCCESS",
    val message: String,
    val predictionsProcessed: Int,
    val usersAwarded: Int
)
