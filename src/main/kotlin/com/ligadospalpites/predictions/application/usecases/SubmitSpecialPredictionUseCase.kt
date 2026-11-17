package com.ligadospalpites.predictions.application.usecases

import com.ligadospalpites.predictions.domain.exceptions.SpecialPredictionLockedException
import com.ligadospalpites.predictions.domain.models.SpecialPrediction
import com.ligadospalpites.predictions.domain.models.SpecialPredictionType
import com.ligadospalpites.predictions.domain.ports.SpecialPredictionRepository
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.sportsfeed.domain.ports.MatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SubmitSpecialPredictionUseCase(
    private val specialPredictionRepository: SpecialPredictionRepository,
    private val matchRepository: MatchRepository
) {
    data class Command(
        val userId: UserId,
        val leagueId: LeagueId,
        val type: SpecialPredictionType,
        val predictionValue: String
    )

    @Transactional
    fun execute(command: Command): SpecialPrediction {
        val matches = matchRepository.findByLeagueId(command.leagueId.value)
        val now = Instant.now()
        if (matches.isNotEmpty()) {
            val firstMatch = matches.minByOrNull { it.kickoffTime }!!
            if (now.isAfter(firstMatch.kickoffTime) || now == firstMatch.kickoffTime) {
                throw SpecialPredictionLockedException("O torneio já começou. Palpites de longo prazo trancados!")
            }
        }

        val existing = specialPredictionRepository.findByUserIdAndLeagueIdAndType(
            userId = command.userId,
            leagueId = command.leagueId,
            type = command.type
        )

        val toSave = if (existing != null) {
            existing.copy(
                predictionValue = command.predictionValue
            )
        } else {
            SpecialPrediction.create(
                userId = command.userId,
                leagueId = command.leagueId,
                type = command.type,
                predictionValue = command.predictionValue,
                createdAt = now
            )
        }

        return specialPredictionRepository.save(toSave)
    }
}
