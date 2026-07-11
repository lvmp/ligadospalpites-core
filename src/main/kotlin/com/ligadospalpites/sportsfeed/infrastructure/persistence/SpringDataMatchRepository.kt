package com.ligadospalpites.sportsfeed.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataMatchRepository : JpaRepository<MatchJpaEntity, UUID> {
    fun findByLeagueId(leagueId: UUID): List<MatchJpaEntity>
}
