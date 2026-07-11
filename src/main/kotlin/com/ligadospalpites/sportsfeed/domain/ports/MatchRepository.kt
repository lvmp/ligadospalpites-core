package com.ligadospalpites.sportsfeed.domain.ports

import com.ligadospalpites.sportsfeed.domain.models.Match
import java.util.UUID

interface MatchRepository {
    fun findById(id: UUID): Match?
    fun findByLeagueId(leagueId: UUID): List<Match>
    fun save(match: Match): Match
}
