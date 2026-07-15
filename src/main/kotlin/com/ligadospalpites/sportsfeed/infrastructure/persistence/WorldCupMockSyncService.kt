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
    private val matchRepository: SpringDataMatchRepository,
    private val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate
) : LeagueSyncService {

    private val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val worldCupLeagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == footballId
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        // Clear any previous matches to avoid duplicates
        val existing = matchRepository.findByLeagueId(worldCupLeagueId)
        matchRepository.deleteAll(existing)

        val fixtures = mutableListOf<MatchJpaEntity>()

        // 1. Fase de Grupos - Concluídos (FINISHED)
        fixtures.add(createMatch("México", "Suécia", -20, 2, 1, MatchStatus.FINISHED, "Fase de Grupos"))
        fixtures.add(createMatch("Estados Unidos", "Bolívia", -19, 3, 1, MatchStatus.FINISHED, "Fase de Grupos"))
        fixtures.add(createMatch("Canadá", "Camarões", -19, 1, 1, MatchStatus.FINISHED, "Fase de Grupos"))
        fixtures.add(createMatch("Argentina", "Japão", -18, 2, 0, MatchStatus.FINISHED, "Fase de Grupos"))
        fixtures.add(createMatch("França", "Austrália", -18, 4, 1, MatchStatus.FINISHED, "Fase de Grupos"))
        fixtures.add(createMatch("Brasil", "Coreia do Sul", -17, 3, 0, MatchStatus.FINISHED, "Fase de Grupos"))
        fixtures.add(createMatch("Portugal", "Gana", -17, 3, 2, MatchStatus.FINISHED, "Fase de Grupos"))

        // 1.5. Dezesseis-avos de Final - Concluídos (FINISHED)
        fixtures.add(createMatch("México", "Suécia", -15, 2, 1, MatchStatus.FINISHED, "Dezesseis-avos de Final"))
        fixtures.add(createMatch("Estados Unidos", "Camarões", -14, 2, 0, MatchStatus.FINISHED, "Dezesseis-avos de Final"))

        // 2. Oitavas de Final - Concluídos (FINISHED)
        fixtures.add(createMatch("Estados Unidos", "Itália", -12, 2, 1, MatchStatus.FINISHED, "Oitavas de Final"))
        fixtures.add(createMatch("Argentina", "Nigéria", -12, 2, 0, MatchStatus.FINISHED, "Oitavas de Final"))
        fixtures.add(createMatch("França", "Alemanha", -11, 3, 2, MatchStatus.FINISHED, "Oitavas de Final"))
        fixtures.add(createMatch("Inglaterra", "Senegal", -11, 3, 0, MatchStatus.FINISHED, "Oitavas de Final"))
        fixtures.add(createMatch("Brasil", "Colômbia", -10, 3, 1, MatchStatus.FINISHED, "Oitavas de Final"))

        // 3. Quartas de Final - Concluídos (FINISHED)
        fixtures.add(createMatch("Brasil", "Holanda", -6, 2, 1, MatchStatus.FINISHED, "Quartas de Final"))
        fixtures.add(createMatch("Argentina", "Inglaterra", -6, 1, 0, MatchStatus.FINISHED, "Quartas de Final"))
        fixtures.add(createMatch("Marrocos", "Portugal", -5, 1, 0, MatchStatus.FINISHED, "Quartas de Final"))
        fixtures.add(createMatch("Espanha", "França", -5, 1, 2, MatchStatus.FINISHED, "Quartas de Final"))

        // 4. Semifinais - Concluídos (FINISHED)
        fixtures.add(createMatch("Brasil", "França", -2, 1, 2, MatchStatus.FINISHED, "Semifinal"))
        fixtures.add(createMatch("Argentina", "Marrocos", -2, 2, 0, MatchStatus.FINISHED, "Semifinal"))

        // 5. Decisão de Terceiro Lugar e Grande Final - Futuros (SCHEDULED)
        // Decisão do Terceiro Lugar agendada para amanhã (Mantém "Brasil" para aprovação do teste integrado do Dashboard)
        fixtures.add(createMatch("Brasil", "Marrocos", 1, null, null, MatchStatus.SCHEDULED, "Disputa do 3º Lugar"))
        // Grande Final da Copa 2026 agendada para daqui a 2 dias (Com lock ativo para palpites)
        fixtures.add(createMatch("Argentina", "França", 2, null, null, MatchStatus.SCHEDULED, "Grande Final"))

        matchRepository.saveAll(fixtures)
    }

    override fun syncNews(sportId: UUID) {
        val mockArticles = listOf(
            mapOf(
                "title" to "Brasil se prepara para enfrentar a França na final da Copa",
                "url" to "https://ge.globo.com/copa/news1.html",
                "urlToImage" to "https://ge.globo.com/image1.png",
                "author" to "Globo Esporte",
                "description" to "A seleção brasileira realizou seu último treino tático antes da grande final contra a França.",
                "category" to "Copa do Mundo"
            )
        )
        try {
            val json = objectMapper.writeValueAsString(mockArticles)
            redisTemplate.opsForValue().set("news:$sportId", json)
        } catch (e: Exception) {
            // Silently ignore during tests
        }
    }

    private fun createMatch(
        home: String,
        away: String,
        daysOffset: Long,
        homeScore: Int?,
        awayScore: Int?,
        status: MatchStatus,
        phase: String? = null
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
            status = status,
            phase = phase
        )
    }
}
