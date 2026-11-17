package com.ligadospalpites.predictions.application.usecases

import com.ligadospalpites.predictions.domain.exceptions.MatchNotFoundException
import com.ligadospalpites.predictions.domain.models.MatchPredictionScore
import com.ligadospalpites.predictions.domain.models.Prediction
import com.ligadospalpites.predictions.domain.ports.PredictionRepository
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.sportsfeed.domain.ports.MatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SubmitPredictionUseCase(
    private val predictionRepository: PredictionRepository,
    private val matchRepository: MatchRepository
) {
    data class Command(
        val userId: UserId,
        val matchId: MatchId,
        val predictedHomeScore: Int,
        val predictedAwayScore: Int
    )

    @Transactional
    fun execute(command: Command): Prediction {
        val match = matchRepository.findById(command.matchId.value)
            ?: throw MatchNotFoundException("Partida não encontrada.")

        val score = MatchPredictionScore.of(command.predictedHomeScore, command.predictedAwayScore)
        val now = Instant.now()

        val existing = predictionRepository.findByUserIdAndMatchId(command.userId, command.matchId)

        val predictionToSave = if (existing != null) {
            existing.updateScore(score, match.kickoffTime, now)
        } else {
            Prediction.create(
                userId = command.userId,
                matchId = command.matchId,
                leagueId = LeagueId(match.leagueId),
                score = score,
                kickoffTime = match.kickoffTime,
                now = now
            )
        }

        return predictionRepository.save(predictionToSave)
    }
}
