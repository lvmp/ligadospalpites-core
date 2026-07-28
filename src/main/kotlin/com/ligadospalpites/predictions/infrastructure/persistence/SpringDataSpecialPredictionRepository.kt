package com.ligadospalpites.predictions.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataSpecialPredictionRepository : JpaRepository<SpecialPredictionJpaEntity, UUID> {
    fun findByUserIdAndLeagueIdAndType(userId: UUID, leagueId: UUID, type: String): SpecialPredictionJpaEntity?
    fun findByUserId(userId: UUID): List<SpecialPredictionJpaEntity>

    @Query("SELECT COALESCE(SUM(s.pointsAwarded), 0) FROM SpecialPredictionJpaEntity s WHERE s.userId = :userId AND s.leagueId = :leagueId")
    fun sumPointsByUserIdAndLeagueId(@Param("userId") userId: UUID, @Param("leagueId") leagueId: UUID): Int
}
