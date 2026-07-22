package com.ligadospalpites.predictions.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataPredictionRepository : JpaRepository<PredictionJpaEntity, UUID> {
    fun findByMatchId(matchId: UUID): List<PredictionJpaEntity>
    fun findByUserIdAndMatchId(userId: UUID, matchId: UUID): PredictionJpaEntity?

    @Query("""
        SELECT DISTINCT p.userId 
        FROM PredictionJpaEntity p 
        WHERE p.matchId IN (
            SELECT m.id FROM MatchJpaEntity m WHERE m.sportId = :sportId
        )
    """)
    fun findUserIdsBySportId(@Param("sportId") sportId: UUID): List<UUID>
}
