package com.ligadospalpites.predictions.infrastructure.web

import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataSpecialPredictionRepository
import com.ligadospalpites.predictions.infrastructure.persistence.PredictionJpaEntity
import com.ligadospalpites.predictions.infrastructure.persistence.SpecialPredictionJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import org.springframework.http.HttpStatus
import com.ligadospalpites.shared.identity.UserResolver
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.context.ApplicationEventPublisher
import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.predictions.domain.events.UserScoreUpdateDto
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class PredictionController(
    private val predictionRepository: SpringDataPredictionRepository,
    private val specialPredictionRepository: SpringDataSpecialPredictionRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val userResolver: UserResolver,
    private val eventPublisher: ApplicationEventPublisher
) {

    // 1. Submit match prediction
    @PostMapping("/predictions")
    fun submitPrediction(
        @RequestBody request: MatchPredictionRequest,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Any> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        val match = matchRepository.findById(request.matchId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "MATCH_NOT_FOUND", "message" to "Partida não encontrada."))

        // CRITICAL VALIDATION: Check kickoff lock
        if (Instant.now().isAfter(match.kickoffTime) || Instant.now() == match.kickoffTime) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("error" to "PREDICTION_LOCKED", "message" to "Este jogo já iniciou. Palpites trancados!"))
        }

        val existing = predictionRepository.findByUserIdAndMatchId(userUUID, request.matchId)
        val entityToSave = if (existing != null) {
            PredictionJpaEntity(
                id = existing.id,
                userId = userUUID,
                matchId = request.matchId,
                leagueId = match.leagueId,
                predictedHomeScore = request.predictedHomeScore,
                predictedAwayScore = request.predictedAwayScore,
                pointsAwarded = existing.pointsAwarded,
                calculatedAt = existing.calculatedAt,
                isProcessed = existing.isProcessed,
                createdAt = existing.createdAt
            )
        } else {
            PredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = userUUID,
                matchId = request.matchId,
                leagueId = match.leagueId,
                predictedHomeScore = request.predictedHomeScore,
                predictedAwayScore = request.predictedAwayScore,
                pointsAwarded = 0,
                calculatedAt = null,
                isProcessed = false,
                createdAt = Instant.now()
            )
        }

        predictionRepository.save(entityToSave)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "Palpite registrado com sucesso!"))
    }

    // 2. Submit special prediction (CHAMPION, TOP_SCORER, etc.)
    @PostMapping("/special-predictions")
    fun submitSpecialPrediction(
        @RequestBody request: SpecialPredictionRequest,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Any> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        // CRITICAL VALIDATION: Find first match in league to determine general lock
        val matches = matchRepository.findByLeagueId(request.leagueId)
        if (matches.isNotEmpty()) {
            val firstMatch = matches.minByOrNull { it.kickoffTime }!!
            if (Instant.now().isAfter(firstMatch.kickoffTime) || Instant.now() == firstMatch.kickoffTime) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "SPECIAL_PREDICTION_LOCKED", "message" to "O torneio já começou. Palpites de longo prazo trancados!"))
            }
        }

        val existing = specialPredictionRepository.findByUserIdAndLeagueIdAndType(userUUID, request.leagueId, request.type)
        val entityToSave = if (existing != null) {
            SpecialPredictionJpaEntity(
                id = existing.id,
                userId = userUUID,
                leagueId = request.leagueId,
                type = request.type,
                predictionValue = request.predictionValue,
                pointsAwarded = existing.pointsAwarded,
                isProcessed = existing.isProcessed,
                createdAt = existing.createdAt
            )
        } else {
            SpecialPredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = userUUID,
                leagueId = request.leagueId,
                type = request.type,
                predictionValue = request.predictionValue,
                pointsAwarded = 0,
                isProcessed = false,
                createdAt = Instant.now()
            )
        }

        specialPredictionRepository.save(entityToSave)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "Palpite especial de ${request.type} registrado!"))
    }

    // 3. GET Match Predictions for user
    @GetMapping("/predictions")
    fun getPredictions(
        @RequestParam(required = false) matchId: UUID?,
        @RequestParam(required = false) leagueId: UUID?,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Any> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        return when {
            matchId != null -> {
                val prediction = predictionRepository.findByUserIdAndMatchId(userUUID, matchId)
                ResponseEntity.ok(prediction?.let { listOf(it) } ?: emptyList<PredictionJpaEntity>())
            }
            leagueId != null -> {
                val predictions = predictionRepository.findByUserId(userUUID).filter { it.leagueId == leagueId }
                ResponseEntity.ok(predictions)
            }
            else -> {
                val predictions = predictionRepository.findByUserId(userUUID)
                ResponseEntity.ok(predictions)
            }
        }
    }

    // 4. GET Special Predictions for user
    @GetMapping("/special-predictions")
    fun getSpecialPredictions(
        @RequestParam(required = false) leagueId: UUID?,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Any> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        val predictions = if (leagueId != null) {
            specialPredictionRepository.findByUserId(userUUID).filter { it.leagueId == leagueId }
        } else {
            specialPredictionRepository.findByUserId(userUUID)
        }
        return ResponseEntity.ok(predictions)
    }

    // 5. POST Admin Endpoint to calculate points for Special Predictions
    @Transactional
    @PostMapping("/internal/special-predictions/evaluate")
    fun evaluateSpecialPredictions(
        @RequestBody request: SpecialPredictionEvaluationRequest
    ): ResponseEntity<Any> {
        val uncompleted = specialPredictionRepository.findAll().filter { 
            it.leagueId == request.leagueId && !it.isProcessed 
        }

        if (uncompleted.isEmpty()) {
            return ResponseEntity.ok(mapOf(
                "status" to "COMPLETED",
                "message" to "Nenhum palpite especial pendente para processar nesta liga.",
                "predictionsProcessed" to 0
            ))
        }

        val correctValues = mapOf(
            "CHAMPION" to request.championTeamId,
            "SECOND_PLACE" to request.secondPlaceTeamId,
            "THIRD_PLACE" to request.thirdPlaceTeamId,
            "FOURTH_PLACE" to request.fourthPlaceTeamId
        )

        val pointsPerType = mapOf(
            "CHAMPION" to 50,
            "SECOND_PLACE" to 30,
            "THIRD_PLACE" to 20,
            "FOURTH_PLACE" to 10
        )

        val scoreUpdates = mutableListOf<UserScoreUpdateDto>()
        val updatedEntities = uncompleted.map { pred ->
            val correctTeamId = correctValues[pred.type]
            val earnedPoints = if (correctTeamId != null && pred.predictionValue.equals(correctTeamId, ignoreCase = true)) {
                pointsPerType[pred.type] ?: 0
            } else {
                0
            }

            if (earnedPoints > 0) {
                scoreUpdates.add(UserScoreUpdateDto(pred.userId, earnedPoints))
            }

            SpecialPredictionJpaEntity(
                id = pred.id,
                userId = pred.userId,
                leagueId = pred.leagueId,
                type = pred.type,
                predictionValue = pred.predictionValue,
                pointsAwarded = earnedPoints,
                isProcessed = true,
                createdAt = pred.createdAt
            )
        }

        specialPredictionRepository.saveAll(updatedEntities)

        if (scoreUpdates.isNotEmpty()) {
            val aggregatedScores = scoreUpdates.groupBy { it.userId }.map { (userId, updates) ->
                UserScoreUpdateDto(userId, updates.sumOf { it.pointsGained })
            }
            eventPublisher.publishEvent(PredictionsProcessedEvent(request.leagueId, aggregatedScores))
        }

        return ResponseEntity.ok(mapOf(
            "status" to "SUCCESS",
            "message" to "Cálculo de palpites especiais concluído com sucesso!",
            "predictionsProcessed" to updatedEntities.size,
            "usersAwarded" to scoreUpdates.map { it.userId }.distinct().size
        ))
    }
}

// Request DTOs
data class MatchPredictionRequest(
    val matchId: UUID,
    val predictedHomeScore: Int,
    val predictedAwayScore: Int
)

data class SpecialPredictionRequest(
    val leagueId: UUID,
    val type: String, // CHAMPION, TOP_SCORER, etc.
    val predictionValue: String
)

data class SpecialPredictionEvaluationRequest(
    val leagueId: UUID,
    val championTeamId: String,
    val secondPlaceTeamId: String,
    val thirdPlaceTeamId: String,
    val fourthPlaceTeamId: String
)
