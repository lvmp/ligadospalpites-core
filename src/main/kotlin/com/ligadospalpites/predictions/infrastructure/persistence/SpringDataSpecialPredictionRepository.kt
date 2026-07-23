package com.ligadospalpites.predictions.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataSpecialPredictionRepository : JpaRepository<SpecialPredictionJpaEntity, UUID> {
    fun findByUserIdAndLeagueIdAndType(userId: UUID, leagueId: UUID, type: String): SpecialPredictionJpaEntity?
    fun findByUserId(userId: UUID): List<SpecialPredictionJpaEntity>
}
