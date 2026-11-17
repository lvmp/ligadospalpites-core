package com.ligadospalpites.predictions.application.usecases

import com.ligadospalpites.predictions.domain.models.SpecialPrediction
import com.ligadospalpites.predictions.domain.ports.SpecialPredictionRepository
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.sportsfeed.domain.ports.LeagueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetSpecialPredictionsUseCase(
    private val specialPredictionRepository: SpecialPredictionRepository,
    private val leagueRepository: LeagueRepository
) {
    @Transactional(readOnly = true)
    fun execute(userId: UserId, leagueId: LeagueId?): List<SpecialPrediction> {
        return if (leagueId != null) {
            specialPredictionRepository.findByUserIdAndLeagueId(userId, leagueId)
        } else {
            val activeLeagueIds = leagueRepository.findByIsActiveTrue().map { LeagueId(it.id) }.toSet()
            if (activeLeagueIds.isNotEmpty()) {
                specialPredictionRepository.findByUserIdAndLeagueIdIn(userId, activeLeagueIds)
            } else {
                emptyList()
            }
        }
    }
}
