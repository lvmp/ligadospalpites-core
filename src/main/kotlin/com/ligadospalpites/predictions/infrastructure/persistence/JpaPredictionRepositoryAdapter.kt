package com.ligadospalpites.predictions.infrastructure.persistence

import com.ligadospalpites.predictions.domain.models.Prediction
import com.ligadospalpites.predictions.domain.models.PredictionId
import com.ligadospalpites.predictions.domain.ports.PredictionRepository
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.MatchId
import com.ligadospalpites.shared.domain.models.UserId
import org.springframework.stereotype.Component

@Component
class JpaPredictionRepositoryAdapter(
    private val springDataRepository: SpringDataPredictionRepository
) : PredictionRepository {

    override fun findById(id: PredictionId): Prediction? {
        return springDataRepository.findById(id.value).map { it.toDomain() }.orElse(null)
    }

    override fun findByMatchId(matchId: MatchId): List<Prediction> {
        return springDataRepository.findByMatchId(matchId.value).map { it.toDomain() }
    }

    override fun findByUserIdAndMatchId(userId: UserId, matchId: MatchId): Prediction? {
        return springDataRepository.findByUserIdAndMatchId(userId.value, matchId.value)?.toDomain()
    }

    override fun findByUserIdAndLeagueId(userId: UserId, leagueId: LeagueId): List<Prediction> {
        return springDataRepository.findByUserIdAndLeagueId(userId.value, leagueId.value).map { it.toDomain() }
    }

    override fun findByUserIdAndLeagueIdIn(userId: UserId, leagueIds: Set<LeagueId>): List<Prediction> {
        if (leagueIds.isEmpty()) return emptyList()
        val rawIds = leagueIds.map { it.value }
        return springDataRepository.findByUserIdAndLeagueIdIn(userId.value, rawIds).map { it.toDomain() }
    }

    override fun save(prediction: Prediction): Prediction {
        val entity = PredictionJpaEntity.fromDomain(prediction)
        return springDataRepository.save(entity).toDomain()
    }

    override fun saveAll(predictions: List<Prediction>): List<Prediction> {
        val entities = predictions.map { PredictionJpaEntity.fromDomain(it) }
        return springDataRepository.saveAll(entities).map { it.toDomain() }
    }
}
