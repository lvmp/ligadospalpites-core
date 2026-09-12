package com.ligadospalpites.sportsfeed.infrastructure.adapters

import com.ligadospalpites.sportsfeed.application.usecases.GetMatchTimelineUseCase
import com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MatchFinishedTimelineListener(
    private val matchRepository: SpringDataMatchRepository,
    private val getMatchTimelineUseCase: GetMatchTimelineUseCase
) {
    private val log = LoggerFactory.getLogger(MatchFinishedTimelineListener::class.java)

    @Async
    @EventListener
    fun onMatchFinished(event: MatchFinishedEvent) {
        log.info("Persisting historical match timeline events for finished match ${event.matchId}")
        try {
            val match = matchRepository.findById(event.matchId).orElse(null)
            if (match != null) {
                // Executa a consolidação final da partida para salvar os lances históricos
                val systemAdminUuid = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
                try {
                    // Tenta executar ou invocar persistência direta de lances
                    getMatchTimelineUseCase.execute(event.matchId, systemAdminUuid)
                } catch (_: Exception) {
                    // Se falhar devido a validações, a primeira consulta pelo usuário fará o fallback
                }
            }
        } catch (e: Exception) {
            log.warn("Could not consolidate timeline on MatchFinishedEvent for ${event.matchId}: ${e.message}")
        }
    }
}
