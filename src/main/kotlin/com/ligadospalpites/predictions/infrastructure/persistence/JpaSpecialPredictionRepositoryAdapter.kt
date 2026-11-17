package com.ligadospalpites.predictions.infrastructure.persistence

import com.ligadospalpites.predictions.domain.models.SpecialPrediction
import com.ligadospalpites.predictions.domain.models.SpecialPredictionType
import com.ligadospalpites.predictions.domain.ports.SpecialPredictionRepository
import com.ligadospalpites.shared.domain.models.LeagueId
import com.ligadospalpites.shared.domain.models.UserId
import org.springframework.stereotype.Component

@Component
class JpaSpecialPredictionRepositoryAdapter(
    private val springDataSpecialPredictionRepository: SpringDataSpecialPredictionRepository
) : SpecialPredictionRepository {

    override fun findByUserIdAndLeagueIdAndType(
        userId: UserId,
        leagueId: LeagueId,
        type: SpecialPredictionType
    ): SpecialPrediction? {
        return springDataSpecialPredictionRepository
            .findByUserIdAndLeagueIdAndType(userId.value, leagueId.value, type.value)
            ?.toDomain()
    }

    override fun findByUserIdAndLeagueId(userId: UserId, leagueId: LeagueId): List<SpecialPrediction> {
        return springDataSpecialPredictionRepository
            .findByUserIdAndLeagueId(userId.value, leagueId.value)
            .map { it.toDomain() }
    }

    override fun findByUserIdAndLeagueIdIn(userId: UserId, leagueIds: Set<LeagueId>): List<SpecialPrediction> {
        if (leagueIds.isEmpty()) return emptyList()
        val rawIds = leagueIds.map { it.value }
        return springDataSpecialPredictionRepository
            .findByUserIdAndLeagueIdIn(userId.value, rawIds)
            .map { it.toDomain() }
    }

    override fun findByLeagueIdAndIsProcessedFalse(leagueId: LeagueId): List<SpecialPrediction> {
        return springDataSpecialPredictionRepository
            .findByLeagueIdAndIsProcessedFalse(leagueId.value)
            .map { it.toDomain() }
    }

    override fun save(specialPrediction: SpecialPrediction): SpecialPrediction {
        val entity = SpecialPredictionJpaEntity.fromDomain(specialPrediction)
        return springDataSpecialPredictionRepository.save(entity).toDomain()
    }

    override fun saveAll(predictions: List<SpecialPrediction>): List<SpecialPrediction> {
        val entities = predictions.map { SpecialPredictionJpaEntity.fromDomain(it) }
        return springDataSpecialPredictionRepository.saveAll(entities).map { it.toDomain() }
    }
}
