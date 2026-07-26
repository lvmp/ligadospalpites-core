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
        @RequestParam(required = false) sportId: UUID?,
        @RequestParam(required = false) leagueId: UUID?,
        @RequestParam(required = false, defaultValue = "false") force: Boolean
    ): ResponseEntity<Map<String, Any>> {
        return if (sportId != null && leagueId != null) {
            syncOrchestrator.syncMatches(sportId, leagueId)
            ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "Matches synced for league $leagueId"))
        } else {
            val results = syncOrchestrator.syncAllActiveLeagues(force)
            ResponseEntity.ok(mapOf(
                "status" to "SUCCESS",
                "message" to "All active leagues sync completed",
                "results" to results
            ))
        }
    }

    @PostMapping("/scheduler/news/process")
    fun processNewsScheduler(): ResponseEntity<Map<String, Any>> {
        val results = syncOrchestrator.syncAllActiveLeaguesNews()
        return ResponseEntity.ok(mapOf(
            "status" to "SUCCESS",
            "message" to "All active leagues news sync completed",
            "results" to results
        ))
    }
}

