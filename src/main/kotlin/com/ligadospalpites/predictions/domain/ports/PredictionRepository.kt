package com.ligadospalpites.predictions.domain.ports

import com.ligadospalpites.predictions.domain.models.Prediction
import com.ligadospalpites.predictions.domain.models.PredictionId
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.shared.domain.models.UserId

interface PredictionRepository {
    fun findById(id: PredictionId): Prediction?
    fun findByMatchId(matchId: MatchId): List<Prediction>
    fun findByUserIdAndMatchId(userId: UserId, matchId: MatchId): Prediction?
    fun findByUserIdAndLeagueId(userId: UserId, leagueId: LeagueId): List<Prediction>
    fun findByUserIdAndLeagueIdIn(userId: UserId, leagueIds: Set<LeagueId>): List<Prediction>
    fun save(prediction: Prediction): Prediction
    fun saveAll(predictions: List<Prediction>): List<Prediction>
}
