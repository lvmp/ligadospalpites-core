package com.ligadospalpites.sportsfeed.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataMatchEventRepository : JpaRepository<MatchEventJpaEntity, UUID> {
    fun findByMatchIdOrderByMinuteDescCreatedAtDesc(matchId: UUID): List<MatchEventJpaEntity>
    fun existsByMatchId(matchId: UUID): Boolean
    fun deleteByMatchId(matchId: UUID)
}
