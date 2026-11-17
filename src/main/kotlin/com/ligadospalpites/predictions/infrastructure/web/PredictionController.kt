package com.ligadospalpites.predictions.infrastructure.web

import com.ligadospalpites.predictions.application.usecases.*
import com.ligadospalpites.predictions.domain.models.SpecialPredictionType
import com.ligadospalpites.predictions.infrastructure.web.dtos.*
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.shared.identity.UserResolver
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class PredictionController(
    private val submitPredictionUseCase: SubmitPredictionUseCase,
    private val submitSpecialPredictionUseCase: SubmitSpecialPredictionUseCase,
    private val getPredictionsUseCase: GetPredictionsUseCase,
    private val getSpecialPredictionsUseCase: GetSpecialPredictionsUseCase,
    private val evaluateSpecialPredictionsUseCase: EvaluateSpecialPredictionsUseCase,
    private val userResolver: UserResolver
) {

    // 1. Submit match prediction
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @PostMapping("/predictions")
    fun submitPrediction(
        @RequestBody @Valid request: MatchPredictionRequest,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<PredictionResponse> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)
        val prediction = submitPredictionUseCase.execute(
            SubmitPredictionUseCase.Command(
                userId = UserId(userUUID),
                matchId = MatchId(request.matchId),
                predictedHomeScore = request.predictedHomeScore,
                predictedAwayScore = request.predictedAwayScore
            )
        )
        return ResponseEntity.ok(PredictionResponse.fromDomain(prediction))
    }

    // 2. Submit special prediction (CHAMPION, TOP_SCORER, etc.)
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @PostMapping("/special-predictions")
    fun submitSpecialPrediction(
        @RequestBody @Valid request: SpecialPredictionRequest,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<SpecialPredictionResponse> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)
        val specialPrediction = submitSpecialPredictionUseCase.execute(
            SubmitSpecialPredictionUseCase.Command(
                userId = UserId(userUUID),
                leagueId = LeagueId(request.leagueId),
                type = SpecialPredictionType.of(request.type),
                predictionValue = request.predictionValue
            )
        )
        return ResponseEntity.ok(SpecialPredictionResponse.fromDomain(specialPrediction))
    }

    // 3. GET Match Predictions for user
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @GetMapping("/predictions")
    fun getPredictions(
        @RequestParam(required = false) matchId: UUID?,
        @RequestParam(required = false) leagueId: UUID?,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<List<PredictionResponse>> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)
        val predictions = getPredictionsUseCase.execute(
            userId = UserId(userUUID),
            matchId = matchId?.let { MatchId(it) },
            leagueId = leagueId?.let { LeagueId(it) }
        ).map { PredictionResponse.fromDomain(it) }

        return ResponseEntity.ok(predictions)
    }

    // 4. GET Special Predictions for user
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @GetMapping("/special-predictions")
    fun getSpecialPredictions(
        @RequestParam(required = false) leagueId: UUID?,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<List<SpecialPredictionResponse>> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)
        val predictions = getSpecialPredictionsUseCase.execute(
            userId = UserId(userUUID),
            leagueId = leagueId?.let { LeagueId(it) }
        ).map { SpecialPredictionResponse.fromDomain(it) }

        return ResponseEntity.ok(predictions)
    }

    // 5. POST Admin Endpoint to calculate points for Special Predictions
    @PostMapping("/internal/special-predictions/evaluate")
    fun evaluateSpecialPredictions(
        @RequestBody @Valid request: SpecialPredictionEvaluationRequest
    ): ResponseEntity<SpecialPredictionEvaluationResponse> {
        val result = evaluateSpecialPredictionsUseCase.execute(
            EvaluateSpecialPredictionsUseCase.Command(
                leagueId = LeagueId(request.leagueId),
                championTeamId = request.championTeamId,
                secondPlaceTeamId = request.secondPlaceTeamId,
                thirdPlaceTeamId = request.thirdPlaceTeamId,
                fourthPlaceTeamId = request.fourthPlaceTeamId
            )
        )

        val message = if (result.predictionsProcessed == 0) {
            "Nenhum palpite especial pendente para processar nesta liga."
        } else {
            "Cálculo de palpites especiais concluído com sucesso!"
        }

        return ResponseEntity.ok(
            SpecialPredictionEvaluationResponse(
                status = "SUCCESS",
                message = message,
                predictionsProcessed = result.predictionsProcessed,
                usersAwarded = result.usersAwarded
            )
        )
    }
}
