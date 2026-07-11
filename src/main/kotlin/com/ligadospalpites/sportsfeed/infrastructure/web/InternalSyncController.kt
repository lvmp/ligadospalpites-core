package com.ligadospalpites.sportsfeed.infrastructure.web

import com.ligadospalpites.sportsfeed.application.usecases.SyncOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal")
class InternalSyncController(private val syncOrchestrator: SyncOrchestrator) {

    @PostMapping("/news/sync")
    fun syncNews(@RequestParam sportId: UUID): ResponseEntity<Map<String, String>> {
        syncOrchestrator.syncNews(sportId)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "News synced for sport $sportId"))
    }

    @PostMapping("/scheduler/process")
    fun processScheduler(
        @RequestParam sportId: UUID,
        @RequestParam leagueId: UUID
    ): ResponseEntity<Map<String, String>> {
        syncOrchestrator.syncMatches(sportId, leagueId)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "Matches synced for league $leagueId"))
    }
}
