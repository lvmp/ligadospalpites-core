package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.League
import com.ligadospalpites.sportsfeed.domain.ports.LeagueRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JpaLeagueRepositoryAdapter(
    private val springDataLeagueRepository: SpringDataLeagueRepository
) : LeagueRepository {

    override fun findById(id: UUID): League? {
        return springDataLeagueRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override fun findByIsActiveTrue(): List<League> {
        return springDataLeagueRepository.findByIsActiveTrue().map { it.toDomain() }
    }

    private fun LeagueJpaEntity.toDomain(): League = League(
        id = id,
        name = name,
        sportId = sportId,
        isActive = isActive,
        logoUrl = logoUrl,
        format = format,
        createdAt = createdAt
    )
}
