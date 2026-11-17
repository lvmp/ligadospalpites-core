package com.ligadospalpites.sportsfeed.domain.ports

import com.ligadospalpites.sportsfeed.domain.models.League
import java.util.UUID

interface LeagueRepository {
    fun findById(id: UUID): League?
    fun findByIsActiveTrue(): List<League>
}
