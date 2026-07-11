package com.ligadospalpites.sportsfeed.application.usecases

import org.springframework.stereotype.Service
import java.util.UUID

interface LeagueSyncService {
    fun supports(sportId: UUID, leagueId: UUID): Boolean
    fun syncMatches(sportId: UUID, leagueId: UUID)
    fun syncNews(sportId: UUID)
}

@Service
class SyncOrchestrator(private val syncServices: List<LeagueSyncService>) {

    fun syncMatches(sportId: UUID, leagueId: UUID) {
        val service = syncServices.find { it.supports(sportId, leagueId) }
            ?: throw IllegalArgumentException("No sync service found for sport $sportId and league $leagueId")
        service.syncMatches(sportId, leagueId)
    }

    fun syncNews(sportId: UUID) {
        val service = syncServices.find { it.supports(sportId, UUID.randomUUID()) }
            ?: throw IllegalArgumentException("No sync service found for sport $sportId")
        service.syncNews(sportId)
    }
}
