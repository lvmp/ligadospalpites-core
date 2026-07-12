package com.ligadospalpites.sportsfeed.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataLeagueRepository : JpaRepository<LeagueJpaEntity, UUID> {
    fun findByIsActiveTrue(): List<LeagueJpaEntity>
}
