package com.ligadospalpites.sportsfeed.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataSeasonRepository : JpaRepository<SeasonJpaEntity, UUID> {
    fun findByLeagueId(leagueId: UUID): List<SeasonJpaEntity>
    fun findByLeagueIdAndIsActiveTrue(leagueId: UUID): SeasonJpaEntity?
}
