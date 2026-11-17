package com.ligadospalpites.predictions.domain.ports

import com.ligadospalpites.predictions.domain.models.SpecialPrediction
import com.ligadospalpites.predictions.domain.models.SpecialPredictionType
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.UserId

interface SpecialPredictionRepository {
    fun findByUserIdAndLeagueIdAndType(userId: UserId, leagueId: LeagueId, type: SpecialPredictionType): SpecialPrediction?
    fun findByUserIdAndLeagueId(userId: UserId, leagueId: LeagueId): List<SpecialPrediction>
    fun findByUserIdAndLeagueIdIn(userId: UserId, leagueIds: Set<LeagueId>): List<SpecialPrediction>
    fun findByLeagueIdAndIsProcessedFalse(leagueId: LeagueId): List<SpecialPrediction>
    fun save(specialPrediction: SpecialPrediction): SpecialPrediction
    fun saveAll(predictions: List<SpecialPrediction>): List<SpecialPrediction>
}
