package com.ligadospalpites.sportsfeed.application.usecases

import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

interface LeagueSyncService {
    fun supports(sportId: UUID, leagueId: UUID): Boolean
    fun syncMatches(sportId: UUID, leagueId: UUID)
    fun syncNews(sportId: UUID)
}

@Service
class SyncOrchestrator(
    private val syncServices: List<LeagueSyncService>,
    private val leagueRepository: SpringDataLeagueRepository,
    private val matchRepository: SpringDataMatchRepository
) {
    private val logger = LoggerFactory.getLogger(SyncOrchestrator::class.java)

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

    fun syncAllActiveLeagues(force: Boolean): List<Map<String, Any>> {
        logger.info("Starting sync process for all active leagues. Force mode: $force")
        val activeLeagues = leagueRepository.findByIsActiveTrue()
        val now = Instant.now()
        val results = mutableListOf<Map<String, Any>>()

        for (league in activeLeagues) {
            val sportId = league.sportId
            val leagueId = league.id
            val leagueName = league.name

            val service = syncServices.find { it.supports(sportId, leagueId) }
            if (service == null) {
                logger.warn("No sync service found for active league $leagueName (ID: $leagueId, Sport: $sportId)")
                results.add(mapOf(
                    "leagueId" to leagueId,
                    "leagueName" to leagueName,
                    "status" to "SKIPPED",
                    "reason" to "No support service"
                ))
                continue
            }

            // Se NÃO for force, aplicamos o filtro inteligente de janela de atividade
            if (!force) {
                val matches = matchRepository.findByLeagueId(leagueId)
                val hasActiveMatch = matches.any { match ->
                    val isLive = match.status == MatchStatus.LIVE
                    val isNearKickoff = match.kickoffTime?.let { kickoff ->
                        val startWindow = now.minus(15, ChronoUnit.MINUTES)
                        val endWindow = now.plus(3, ChronoUnit.HOURS)
                        kickoff.isAfter(startWindow) && kickoff.isBefore(endWindow)
                    } ?: false

                    isLive || isNearKickoff
                }

                if (!hasActiveMatch) {
                    logger.info("Skipping real-time sync for league $leagueName (ID: $leagueId) - No live or upcoming matches in the active window.")
                    results.add(mapOf(
                        "leagueId" to leagueId,
                        "leagueName" to leagueName,
                        "status" to "SKIPPED",
                        "reason" to "No active window matches"
                    ))
                    continue
                }
            }

            logger.info("Executing sync for league $leagueName (ID: $leagueId)")
            try {
                service.syncMatches(sportId, leagueId)
                results.add(mapOf(
                    "leagueId" to leagueId,
                    "leagueName" to leagueName,
                    "status" to "SUCCESS"
                ))
            } catch (e: Exception) {
                logger.error("Error syncing league $leagueName (ID: $leagueId): ${e.message}", e)
                results.add(mapOf(
                    "leagueId" to leagueId,
                    "leagueName" to leagueName,
                    "status" to "FAILED",
                    "error" to (e.message ?: "Unknown error")
                ))
            }
        }

        logger.info("Completed sync process for all active leagues. Results: $results")
        return results
    }
}

