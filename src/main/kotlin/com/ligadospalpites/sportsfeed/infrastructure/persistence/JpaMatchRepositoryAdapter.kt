package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.domain.models.Match
import com.ligadospalpites.sportsfeed.domain.ports.MatchRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JpaMatchRepositoryAdapter(
    private val springDataMatchRepository: SpringDataMatchRepository
) : MatchRepository {

    override fun findById(id: UUID): Match? {
        return springDataMatchRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override fun findByLeagueId(leagueId: UUID): List<Match> {
        return springDataMatchRepository.findByLeagueId(leagueId).map { it.toDomain() }
    }

    override fun findBySeasonId(seasonId: UUID): List<Match> {
        return springDataMatchRepository.findBySeasonId(seasonId).map { it.toDomain() }
    }

    override fun save(match: Match): Match {
        val entity = MatchJpaEntity.fromDomain(match)
        return springDataMatchRepository.save(entity).toDomain()
    }
}
