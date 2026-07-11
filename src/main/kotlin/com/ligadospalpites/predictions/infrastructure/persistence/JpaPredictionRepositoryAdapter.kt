package com.ligadospalpites.predictions.infrastructure.persistence

import com.ligadospalpites.predictions.domain.models.Prediction
import com.ligadospalpites.predictions.domain.ports.PredictionRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JpaPredictionRepositoryAdapter(
    private val springDataRepository: SpringDataPredictionRepository
) : PredictionRepository {

    override fun findById(id: UUID): Prediction? {
        return springDataRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override fun findByMatchId(matchId: UUID): List<Prediction> {
        return springDataRepository.findByMatchId(matchId).map { it.toDomain() }
    }

    override fun save(prediction: Prediction): Prediction {
        val entity = PredictionJpaEntity.fromDomain(prediction)
        return springDataRepository.save(entity).toDomain()
    }
}
