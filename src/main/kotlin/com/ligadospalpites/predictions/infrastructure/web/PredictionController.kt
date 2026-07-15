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
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class PredictionController(
    private val predictionRepository: SpringDataPredictionRepository,
    private val specialPredictionRepository: SpringDataSpecialPredictionRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val userResolver: UserResolver
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
