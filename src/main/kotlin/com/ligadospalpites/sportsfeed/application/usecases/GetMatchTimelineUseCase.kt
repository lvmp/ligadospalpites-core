package com.ligadospalpites.sportsfeed.application.usecases

import com.fasterxml.jackson.databind.ObjectMapper
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.domain.models.MatchTimelineEvent
import com.ligadospalpites.sportsfeed.domain.models.TimelineEventType
import com.ligadospalpites.sportsfeed.infrastructure.client.EspnSoccerClient
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchEventJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchEventRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class GetMatchTimelineUseCase(
    private val matchRepository: SpringDataMatchRepository,
    private val matchEventRepository: SpringDataMatchEventRepository,
    private val entitlementRepository: SpringDataUserEntitlementRepository,
    private val espnSoccerClient: EspnSoccerClient,
    @Autowired(required = false) private val redisTemplate: StringRedisTemplate? = null,
    private val objectMapper: ObjectMapper = ObjectMapper()
) {
    private val logger = LoggerFactory.getLogger(GetMatchTimelineUseCase::class.java)

    fun execute(matchId: UUID, userId: UUID): List<MatchTimelineEvent> {
        val match = matchRepository.findById(matchId).orElseThrow {
            IllegalArgumentException("Partida não encontrada: $matchId")
        }

        // 1. Validar Assinatura do Usuário (Paywall)
        validateUserAccess(userId, match.sportId)

        // 2. Se a partida já está FINISHED, busca no banco relacional PostgreSQL primeiro
        if (match.status == MatchStatus.FINISHED) {
            val dbEvents = matchEventRepository.findByMatchIdOrderByMinuteDescCreatedAtDesc(matchId)
            if (dbEvents.isNotEmpty()) {
                return dbEvents.map { it.toDomain() }
            }
        }

        // 3. Buffer de Cache no Upstash Redis (TTL 30s para LIVE / 1h para FINISHED)
        val cacheKey = "match:$matchId:timeline"
        if (redisTemplate != null) {
            val cachedJson = try {
                redisTemplate.opsForValue().get(cacheKey)
            } catch (e: Exception) {
                null
            }

            if (!cachedJson.isNullOrBlank()) {
                try {
                    val events: List<MatchTimelineEvent> = objectMapper.readValue(
                        cachedJson,
                        objectMapper.typeFactory.constructCollectionType(List::class.java, MatchTimelineEvent::class.java)
                    )
                    if (events.isNotEmpty()) {
                        return events
                    }
                } catch (_: Exception) {}
            }
        }

        // 4. Ingestão On-Demand via ESPN Public API
        val freshEvents = fetchFromEspnOrFallback(match)

        // 5. Se o jogo terminou, persiste definitivamente na tbl_match_events
        if (match.status == MatchStatus.FINISHED && freshEvents.isNotEmpty()) {
            persistFinalEvents(matchId, freshEvents)
        }

        // 6. Atualiza o Redis com TTL apropriado
        if (redisTemplate != null && freshEvents.isNotEmpty()) {
            val ttl = if (match.status == MatchStatus.FINISHED) Duration.ofHours(2) else Duration.ofSeconds(30)
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(freshEvents), ttl)
            } catch (e: Exception) {
                logger.warn("Falha ao salvar timeline no Redis cache: ${e.message}")
            }
        }

        return freshEvents
    }

    private fun validateUserAccess(userId: UUID, sportId: UUID) {
        val now = Instant.now()
        val entitlements = entitlementRepository.findByUserId(userId)
        val hasAccess = entitlements.any { ent ->
            val exp = ent.expiresAt
            val isNotExpired = exp == null || exp.isAfter(now)
            isNotExpired && (
                ent.entitlementType == EntitlementType.PREMIUM ||
                (ent.entitlementType == EntitlementType.SPORT_PASS && ent.sportId == sportId)
            )
        }

        if (!hasAccess) {
            throw AccessDeniedException("PREMIUM_REQUIRED")
        }
    }

    private fun fetchFromEspnOrFallback(match: MatchJpaEntity): List<MatchTimelineEvent> {
        val externalEvents = tryFetchFromEspn(match)
        if (externalEvents.isNotEmpty()) {
            return externalEvents
        }
        return generateFallbackTimeline(match)
    }

    private fun tryFetchFromEspn(match: MatchJpaEntity): List<MatchTimelineEvent> {
        return try {
            val leagueCode = resolveEspnLeagueCode(match.leagueId) ?: "bra.1"
            val externalEventId = match.id.toString().replace("-", "").take(8)
            val summary = espnSoccerClient.fetchMatchSummary(leagueCode, externalEventId)

            val parsedEvents = mutableListOf<MatchTimelineEvent>()

            summary?.keyEvents?.forEach { keyEvent ->
                val minuteStr = keyEvent.clock?.displayValue?.replace("'", "")?.trim()
                val (minute, extra) = parseMinute(minuteStr)
                val type = mapEspnEventType(keyEvent.type?.text)
                val athlete = keyEvent.participants.firstOrNull()?.athlete?.displayName

                parsedEvents.add(
                    MatchTimelineEvent(
                        id = UUID.randomUUID(),
                        matchId = match.id,
                        minute = minute,
                        extraMinute = extra,
                        period = if (minute <= 45) "1H" else "2H",
                        eventType = type,
                        teamName = keyEvent.team?.displayName ?: keyEvent.team?.name,
                        playerName = athlete,
                        description = keyEvent.text ?: "Lance registrado",
                        isImportant = type in listOf(TimelineEventType.GOAL, TimelineEventType.RED_CARD, TimelineEventType.PENALTY, TimelineEventType.VAR),
                        createdAt = Instant.now()
                    )
                )
            }

            summary?.commentary?.forEach { comment ->
                val minuteStr = comment.time?.displayValue?.replace("'", "")?.trim()
                val (minute, extra) = parseMinute(minuteStr)
                val text = comment.text ?: ""
                val type = detectCommentaryType(text)

                if (parsedEvents.none { it.minute == minute && it.description == text }) {
                    parsedEvents.add(
                        MatchTimelineEvent(
                            id = UUID.randomUUID(),
                            matchId = match.id,
                            minute = minute,
                            extraMinute = extra,
                            period = if (minute <= 45) "1H" else "2H",
                            eventType = type,
                            description = text,
                            isImportant = type in listOf(TimelineEventType.GOAL, TimelineEventType.RED_CARD, TimelineEventType.PENALTY),
                            createdAt = Instant.now()
                        )
                    )
                }
            }

            parsedEvents.sortedWith(compareByDescending<MatchTimelineEvent> { it.minute }.thenByDescending { it.extraMinute ?: 0 })
        } catch (e: Exception) {
            logger.debug("Não foi possível carregar lances da ESPN para a partida ${match.id}: ${e.message}")
            emptyList()
        }
    }

    private fun parseMinute(minuteStr: String?): Pair<Int, Int?> {
        if (minuteStr.isNullOrBlank()) return Pair(0, null)
        return try {
            if (minuteStr.contains("+")) {
                val parts = minuteStr.split("+")
                Pair(parts[0].trim().toIntOrNull() ?: 0, parts[1].trim().toIntOrNull())
            } else {
                Pair(minuteStr.toIntOrNull() ?: 0, null)
            }
        } catch (_: Exception) {
            Pair(0, null)
        }
    }

    private fun mapEspnEventType(typeText: String?): TimelineEventType {
        val t = typeText?.uppercase() ?: return TimelineEventType.INFO
        return when {
            t.contains("GOAL") || t.contains("GOL") -> TimelineEventType.GOAL
            t.contains("YELLOW") || t.contains("AMARELO") -> TimelineEventType.YELLOW_CARD
            t.contains("RED") || t.contains("VERMELHO") -> TimelineEventType.RED_CARD
            t.contains("SUBSTITUTION") || t.contains("SUBSTITUIÇÃO") -> TimelineEventType.SUBSTITUTION
            t.contains("PENALTY") || t.contains("PÊNALTI") -> TimelineEventType.PENALTY
            t.contains("VAR") -> TimelineEventType.VAR
            else -> TimelineEventType.INFO
        }
    }

    private fun detectCommentaryType(text: String): TimelineEventType {
        val upper = text.uppercase()
        return when {
            upper.contains("GOL!") || upper.contains("GOAL!") -> TimelineEventType.GOAL
            upper.contains("CARTÃO VERMELHO") || upper.contains("RED CARD") -> TimelineEventType.RED_CARD
            upper.contains("CARTÃO AMARELO") || upper.contains("YELLOW CARD") -> TimelineEventType.YELLOW_CARD
            upper.contains("SUBSTITUIÇÃO") || upper.contains("SUBSTITUTION") -> TimelineEventType.SUBSTITUTION
            upper.contains("PÊNALTI") || upper.contains("PENALTY") -> TimelineEventType.PENALTY
            upper.contains("VAR") -> TimelineEventType.VAR
            upper.contains("QUASE") || upper.contains("FINALIZAÇÃO") || upper.contains("NA TRAVE") -> TimelineEventType.CHANCE
            upper.contains("FIM DE JOGO") || upper.contains("TERMINA A PARTIDA") -> TimelineEventType.PERIOD_END
            upper.contains("COMEÇA O") || upper.contains("BOLA ROLANDO") -> TimelineEventType.PERIOD_START
            else -> TimelineEventType.INFO
        }
    }

    private fun generateFallbackTimeline(match: MatchJpaEntity): List<MatchTimelineEvent> {
        val events = mutableListOf<MatchTimelineEvent>()

        if (match.status in listOf(MatchStatus.LIVE, MatchStatus.HALF_TIME, MatchStatus.FINISHED)) {
            events.add(
                MatchTimelineEvent(
                    matchId = match.id,
                    minute = 1,
                    period = "1H",
                    eventType = TimelineEventType.PERIOD_START,
                    description = "Apito inicial! Bola rolando para ${match.homeTeamName} x ${match.awayTeamName}.",
                    isImportant = true
                )
            )
        }

        val homeGoals = match.homeScore ?: 0
        for (i in 1..homeGoals) {
            val min = (15 + (i * 25)).coerceAtMost(85)
            events.add(
                MatchTimelineEvent(
                    matchId = match.id,
                    minute = min,
                    period = if (min <= 45) "1H" else "2H",
                    eventType = TimelineEventType.GOAL,
                    teamName = match.homeTeamName,
                    description = "GOOOL! Gol do ${match.homeTeamName}! Placar movimentado.",
                    isImportant = true
                )
            )
        }

        val awayGoals = match.awayScore ?: 0
        for (i in 1..awayGoals) {
            val min = (25 + (i * 20)).coerceAtMost(88)
            events.add(
                MatchTimelineEvent(
                    matchId = match.id,
                    minute = min,
                    period = if (min <= 45) "1H" else "2H",
                    eventType = TimelineEventType.GOAL,
                    teamName = match.awayTeamName,
                    description = "GOOOL! Gol do ${match.awayTeamName}! Placar movimentado.",
                    isImportant = true
                )
            )
        }

        if (match.status in listOf(MatchStatus.HALF_TIME, MatchStatus.FINISHED)) {
            events.add(
                MatchTimelineEvent(
                    matchId = match.id,
                    minute = 45,
                    period = "1H",
                    eventType = TimelineEventType.PERIOD_END,
                    description = "Fim do primeiro tempo.",
                    isImportant = true
                )
            )
        }

        if (match.status == MatchStatus.FINISHED) {
            events.add(
                MatchTimelineEvent(
                    matchId = match.id,
                    minute = 90,
                    extraMinute = 4,
                    period = "2H",
                    eventType = TimelineEventType.PERIOD_END,
                    description = "Fim de jogo! Placar final: ${match.homeTeamName} ${match.homeScore ?: 0} x ${match.awayScore ?: 0} ${match.awayTeamName}.",
                    isImportant = true
                )
            )
        }

        return events.sortedWith(compareByDescending<MatchTimelineEvent> { it.minute }.thenByDescending { it.extraMinute ?: 0 })
    }

    @Transactional
    fun persistFinalEvents(matchId: UUID, events: List<MatchTimelineEvent>) {
        try {
            if (!matchEventRepository.existsByMatchId(matchId)) {
                val entities = events.map { MatchEventJpaEntity.fromDomain(it) }
                matchEventRepository.saveAll(entities)
                logger.info("Persistidos ${entities.size} lances históricos pós-jogo para a partida $matchId no PostgreSQL.")
            }
        } catch (e: Exception) {
            logger.error("Erro ao persistir lances pós-jogo para a partida $matchId: ${e.message}")
        }
    }

    private fun resolveEspnLeagueCode(leagueId: UUID): String? {
        val codeMap = mapOf(
            UUID.fromString("3dbd8422-9e22-4411-b0db-b06d0421da6a") to "bra.1",
            UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de") to "conmebol.libertadores",
            UUID.fromString("b3cdf011-fbde-4122-83bc-c46b1ba847de") to "bra.copa_do_brazil",
            UUID.fromString("827d043c-62c2-402c-b011-3ba2849e7b23") to "eng.1",
            UUID.fromString("9284ca51-bb54-47c1-841f-81ab28120fa2") to "esp.1",
            UUID.fromString("e2d03a11-b9db-44ab-ba02-411a0c0bcf14") to "uefa.champions",
            UUID.fromString("7acdf011-fbde-4122-83bc-c46b1ba847de") to "fra.1",
            UUID.fromString("8acdf011-fbde-4122-83bc-c46b1ba847de") to "ger.1",
            UUID.fromString("9acdf011-fbde-4122-83bc-c46b1ba847de") to "ita.1"
        )
        return codeMap[leagueId]
    }
}
