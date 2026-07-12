package com.ligadospalpites.predictions.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataPredictionRepository : JpaRepository<PredictionJpaEntity, UUID> {
    fun findByMatchId(matchId: UUID): List<PredictionJpaEntity>
    fun findByUserIdAndMatchId(userId: UUID, matchId: UUID): PredictionJpaEntity?
}
