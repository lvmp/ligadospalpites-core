package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.application.usecases.LeagueSyncService
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Profile("integration")
class WorldCupMockSyncService(
    private val matchRepository: SpringDataMatchRepository
) : LeagueSyncService {

    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val worldCupLeagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == footballId && leagueId == worldCupLeagueId
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        // Clear any previous matches to avoid duplicates
        val existing = matchRepository.findByLeagueId(worldCupLeagueId)
        matchRepository.deleteAll(existing)

        val fixtures = mutableListOf<MatchJpaEntity>()

        // 1. Fase de Grupos - Concluídos (FINISHED)
        fixtures.add(createMatch("México", "Suécia", -20, 2, 1, MatchStatus.FINISHED))
        fixtures.add(createMatch("Estados Unidos", "Bolívia", -19, 3, 1, MatchStatus.FINISHED))
        fixtures.add(createMatch("Canadá", "Camarões", -19, 1, 1, MatchStatus.FINISHED))
        fixtures.add(createMatch("Argentina", "Japão", -18, 2, 0, MatchStatus.FINISHED))
        fixtures.add(createMatch("França", "Austrália", -18, 4, 1, MatchStatus.FINISHED))
        fixtures.add(createMatch("Brasil", "Coreia do Sul", -17, 3, 0, MatchStatus.FINISHED))
        fixtures.add(createMatch("Portugal", "Gana", -17, 3, 2, MatchStatus.FINISHED))

        // 2. Oitavas de Final - Concluídos (FINISHED)
        fixtures.add(createMatch("Estados Unidos", "Itália", -12, 2, 1, MatchStatus.FINISHED))
        fixtures.add(createMatch("Argentina", "Nigéria", -12, 2, 0, MatchStatus.FINISHED))
        fixtures.add(createMatch("França", "Alemanha", -11, 3, 2, MatchStatus.FINISHED))
        fixtures.add(createMatch("Inglaterra", "Senegal", -11, 3, 0, MatchStatus.FINISHED))
        fixtures.add(createMatch("Brasil", "Colômbia", -10, 3, 1, MatchStatus.FINISHED))

        // 3. Quartas de Final - Concluídos (FINISHED)
        fixtures.add(createMatch("Brasil", "Holanda", -6, 2, 1, MatchStatus.FINISHED))
        fixtures.add(createMatch("Argentina", "Inglaterra", -6, 1, 0, MatchStatus.FINISHED))
        fixtures.add(createMatch("Marrocos", "Portugal", -5, 1, 0, MatchStatus.FINISHED))
        fixtures.add(createMatch("Espanha", "França", -5, 1, 2, MatchStatus.FINISHED))

        // 4. Semifinais - Concluídos (FINISHED)
        fixtures.add(createMatch("Brasil", "França", -2, 1, 2, MatchStatus.FINISHED))
        fixtures.add(createMatch("Argentina", "Marrocos", -2, 2, 0, MatchStatus.FINISHED))

        // 5. Decisão de Terceiro Lugar e Grande Final - Futuros (SCHEDULED)
        // Decisão do Terceiro Lugar agendada para amanhã (Mantém "Brasil" para aprovação do teste integrado do Dashboard)
        fixtures.add(createMatch("Brasil", "Marrocos", 1, null, null, MatchStatus.SCHEDULED))
        // Grande Final da Copa 2026 agendada para daqui a 2 dias (Com lock ativo para palpites)
        fixtures.add(createMatch("Argentina", "França", 2, null, null, MatchStatus.SCHEDULED))

        matchRepository.saveAll(fixtures)
    }

    override fun syncNews(sportId: UUID) {
        // No-op for this mock service
    }

    private fun createMatch(
        home: String,
        away: String,
        daysOffset: Long,
        homeScore: Int?,
        awayScore: Int?,
        status: MatchStatus
    ): MatchJpaEntity {
        return MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = footballId,
            leagueId = worldCupLeagueId,
            homeTeamName = home,
            awayTeamName = away,
            homeScore = homeScore,
            awayScore = awayScore,
            kickoffTime = Instant.now().plus(daysOffset, ChronoUnit.DAYS),
            status = status
        )
    }
}
