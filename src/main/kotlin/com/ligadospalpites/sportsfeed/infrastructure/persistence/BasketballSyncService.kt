package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.application.usecases.LeagueSyncService
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.domain.events.MatchStartedEvent
import com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent
import com.ligadospalpites.sportsfeed.infrastructure.client.ApiBasketballClient
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class BasketballLeagueMetadata(
    val id: UUID,
    val apiBasketballId: Int,
    val defaultName: String
)

@Service
@Profile("!integration")
class BasketballSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val apiBasketballClient: ApiBasketballClient,
    private val seasonRepository: SpringDataSeasonRepository,
    private val eventPublisher: ApplicationEventPublisher
) : LeagueSyncService {

    private val logger = LoggerFactory.getLogger(BasketballSyncService::class.java)

    @Autowired
    @org.springframework.context.annotation.Lazy
    private lateinit var self: BasketballSyncService

    private val basketballId = UUID.fromString("e5284bf1-d576-4740-97cc-f06bca181cb2")

    private val leaguesMetadata = mapOf(
        UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to BasketballLeagueMetadata(
            id = UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            apiBasketballId = 12,
            defaultName = "NBA"
        ),
        UUID.fromString("2dbd1112-9cde-4411-b0db-b06d0421da6a") to BasketballLeagueMetadata(
            id = UUID.fromString("2dbd1112-9cde-4411-b0db-b06d0421da6a"),
            apiBasketballId = 141,
            defaultName = "NBB"
        )
    )

    private val teamNameTranslations = mapOf(
        "flamengo basquete" to "Flamengo",
        "franca basquete" to "Franca",
        "minas basquete" to "Minas",
        "sao paulo basquete" to "São Paulo",
        "corinthians basquete" to "Corinthians",
        "bauru basquete" to "Bauru"
    )

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == basketballId && leaguesMetadata.containsKey(leagueId)
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val metadata = leaguesMetadata[leagueId] ?: return
        logger.info("Starting basketball games sync for league: ${metadata.defaultName}")

        val incomingMatches = try {
            self.fetchFromApiBasketball(sportId, leagueId)
        } catch (e: Exception) {
            logger.error("Failed to fetch basketball matches from API: ${e.message}")
            emptyList()
        }

        if (incomingMatches.isNotEmpty()) {
            performUpsert(leagueId, incomingMatches)
        } else {
            val existing = matchRepository.findByLeagueId(leagueId)
            if (existing.isEmpty()) {
                logger.warn("No games retrieved for league ${metadata.defaultName} and local database is empty. Seeding initial baseline fixtures.")
                val baseline = generateBaselineFixtures(leagueId, metadata)
                performUpsert(leagueId, baseline)
            } else {
                logger.warn("No games retrieved for league ${metadata.defaultName}. Local database unchanged.")
            }
        }
    }

    @CircuitBreaker(name = "apiBasketballApi", fallbackMethod = "fetchMatchesLocalFallback")
    @Retry(name = "apiBasketballApi")
    fun fetchFromApiBasketball(sportId: UUID, leagueId: UUID): List<MatchJpaEntity> {
        val metadata = leaguesMetadata[leagueId] ?: throw IllegalArgumentException("Invalid league ID: $leagueId")
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: throw IllegalStateException("No active season found for league: $leagueId")
        val seasonYear = activeSeason.externalSeasonCode

        val externalGames = apiBasketballClient.fetchGames(leagueId = metadata.apiBasketballId, season = seasonYear)
        return externalGames.map { game ->
            val homeTranslated = translateTeamName(game.teams.home.name)
            val awayTranslated = translateTeamName(game.teams.away.name)
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = basketballId,
                leagueId = leagueId,
                seasonId = targetSeasonId,
                homeTeamName = homeTranslated,
                awayTeamName = awayTranslated,
                homeTeamLogoUrl = game.teams.home.logo,
                awayTeamLogoUrl = game.teams.away.logo,
                kickoffTime = Instant.parse(game.date),
                status = mapBasketballStatus(game.status.short),
                homeScore = game.scores.home?.total,
                awayScore = game.scores.away?.total,
                phase = game.stage ?: "Temporada Regular",
                updatedAt = Instant.now()
            )
        }
    }

    fun fetchMatchesLocalFallback(sportId: UUID, leagueId: UUID, exception: Throwable): List<MatchJpaEntity> {
        logger.error("API-Basketball circuit breaker activated or call failed. Error: ${exception.message}")
        return emptyList()
    }

    private fun generateBaselineFixtures(leagueId: UUID, metadata: BasketballLeagueMetadata): List<MatchJpaEntity> {
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: UUID.randomUUID()
        val now = Instant.now()

        val isNba = metadata.defaultName.contains("NBA", ignoreCase = true)
        val fixtures = if (isNba) {
            listOf(
                Triple("Boston Celtics", "Miami Heat", 110 to 100),
                Triple("Golden State Warriors", "Los Angeles Lakers", 108 to 105),
                Triple("Milwaukee Bucks", "Philadelphia 76ers", null to null),
                Triple("Phoenix Suns", "Dallas Mavericks", null to null)
            )
        } else {
            listOf(
                Triple("Flamengo", "Franca", 85 to 82),
                Triple("Minas", "São Paulo", 90 to 93),
                Triple("Bauru", "Corinthians", null to null),
                Triple("Paulistano", "Pinheiros", null to null)
            )
        }

        return fixtures.mapIndexed { index, (home, away, scores) ->
            val offsetDays = (index - 1).toLong()
            val status = when {
                scores.first != null -> MatchStatus.FINISHED
                offsetDays == 0L -> MatchStatus.LIVE
                else -> MatchStatus.SCHEDULED
            }
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = basketballId,
                leagueId = leagueId,
                seasonId = targetSeasonId,
                homeTeamName = home,
                awayTeamName = away,
                homeTeamLogoUrl = "https://api.dicebear.com/7.x/initials/svg?seed=$home&radius=50",
                awayTeamLogoUrl = "https://api.dicebear.com/7.x/initials/svg?seed=$away&radius=50",
                kickoffTime = now.plus(offsetDays * 2, java.time.temporal.ChronoUnit.DAYS),
                status = status,
                homeScore = scores.first,
                awayScore = scores.second,
                phase = "Temporada Regular",
                updatedAt = now
            )
        }
    }

    override fun syncNews(sportId: UUID) {
        // News logic not required for basketball
    }

    internal fun performUpsert(leagueId: UUID, incoming: List<MatchJpaEntity>) {
        logger.info("Performing intelligent upsert on ${incoming.size} basketball games to protect predictions for league: $leagueId")
        val existing = matchRepository.findByLeagueId(leagueId)

        val toSave = incoming.map { inc ->
            val matchMatch = existing.find { ext ->
                ext.homeTeamName.lowercase() == inc.homeTeamName.lowercase() &&
                ext.awayTeamName.lowercase() == inc.awayTeamName.lowercase()
            }

            if (matchMatch != null) {
                // Publish events based on state transitions
                if (matchMatch.status == MatchStatus.SCHEDULED && inc.status == MatchStatus.LIVE) {
                    logger.info("Basketball game started event published for game ${matchMatch.id}: ${inc.homeTeamName} x ${inc.awayTeamName}")
                    eventPublisher.publishEvent(MatchStartedEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, inc.sportId, inc.leagueId))
                }

                // Note: MatchGoalEvent (score updates) is explicitly skipped for basketball to prevent massive push notification spam to users.

                if (matchMatch.status != MatchStatus.FINISHED && inc.status == MatchStatus.FINISHED) {
                    logger.info("Basketball game finished event published for game ${matchMatch.id}: ${inc.homeTeamName} x ${inc.awayTeamName}")
                    eventPublisher.publishEvent(MatchFinishedEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, inc.homeScore ?: 0, inc.awayScore ?: 0, inc.sportId, inc.leagueId))
                }

                MatchJpaEntity(
                    id = matchMatch.id,
                    sportId = inc.sportId,
                    leagueId = inc.leagueId,
                    seasonId = matchMatch.seasonId,
                    homeTeamName = matchMatch.homeTeamName,
                    awayTeamName = matchMatch.awayTeamName,
                    homeTeamLogoUrl = inc.homeTeamLogoUrl ?: matchMatch.homeTeamLogoUrl,
                    awayTeamLogoUrl = inc.awayTeamLogoUrl ?: matchMatch.awayTeamLogoUrl,
                    kickoffTime = inc.kickoffTime,
                    status = inc.status,
                    homeScore = inc.homeScore,
                    awayScore = inc.awayScore,
                    phase = inc.phase,
                    updatedAt = Instant.now()
                )
            } else {
                inc
            }
        }

        matchRepository.saveAll(toSave)
        logger.info("Successfully updated/inserted ${toSave.size} basketball games for league $leagueId without deleting user predictions.")
    }

    private fun translateTeamName(name: String): String {
        val canonical = canonicalName(name)
        return teamNameTranslations[canonical] ?: name.trim()
    }

    private fun canonicalName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        return name.lowercase()
            .replace(Regex("[áàâãä]"), "a")
            .replace(Regex("[éèêë]"), "e")
            .replace(Regex("[íìîï]"), "i")
            .replace(Regex("[óòôõö]"), "o")
            .replace(Regex("[úùûü]"), "u")
            .replace(Regex("[ç]"), "c")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun mapBasketballStatus(shortStatus: String): MatchStatus {
        return when (shortStatus.uppercase()) {
            "NS", "TBD" -> MatchStatus.SCHEDULED
            "Q1", "Q2", "Q3", "Q4", "OT", "BT", "HT", "LIVE" -> MatchStatus.LIVE
            "FT", "AOT" -> MatchStatus.FINISHED
            "CAN", "CANC", "PST", "POST", "ABD" -> MatchStatus.CANCELLED
            else -> MatchStatus.SCHEDULED
        }
    }
}
