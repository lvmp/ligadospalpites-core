package com.ligadospalpites.predictions.application.usecases

import com.ligadospalpites.predictions.domain.models.Prediction
import com.ligadospalpites.predictions.domain.ports.PredictionRepository
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.sportsfeed.domain.ports.LeagueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetPredictionsUseCase(
    private val predictionRepository: PredictionRepository,
    private val leagueRepository: LeagueRepository
) {
    @Transactional(readOnly = true)
    fun execute(userId: UserId, matchId: MatchId?, leagueId: LeagueId?): List<Prediction> {
        return when {
            matchId != null -> {
                val prediction = predictionRepository.findByUserIdAndMatchId(userId, matchId)
                prediction?.let { listOf(it) } ?: emptyList()
            }
            leagueId != null -> {
                predictionRepository.findByUserIdAndLeagueId(userId, leagueId)
            }
            else -> {
                val activeLeagueIds = leagueRepository.findByIsActiveTrue().map { LeagueId(it.id) }.toSet()
                if (activeLeagueIds.isNotEmpty()) {
                    predictionRepository.findByUserIdAndLeagueIdIn(userId, activeLeagueIds)
                } else {
                    emptyList()
                }
            }
        }
    }
}
