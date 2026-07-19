package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.application.usecases.LeagueSyncService
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.client.ApiFootballClient
import com.ligadospalpites.sportsfeed.infrastructure.client.FootballDataClient
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
@Profile("!integration")
class FootballWorldCupSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val footballDataClient: FootballDataClient,
    private val apiFootballClient: ApiFootballClient,
    private val newsApiClient: com.ligadospalpites.sportsfeed.infrastructure.client.NewsApiClient,
    private val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate,
    private val seasonRepository: SpringDataSeasonRepository
) : LeagueSyncService {

    private val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

    private val logger = LoggerFactory.getLogger(FootballWorldCupSyncService::class.java)

    @Autowired
    @org.springframework.context.annotation.Lazy
    private lateinit var self: FootballWorldCupSyncService

    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val worldCupLeagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")
    private val worldCupSeasonId = UUID.fromString("50c22998-33b2-4d9a-ba02-4be71a1be992")

    private val teamNameTranslations = mapOf(
        "mexico" to "México",
        "sweden" to "Suécia",
        "united states" to "Estados Unidos",
        "usa" to "Estados Unidos",
        "bolivia" to "Bolívia",
        "cameroon" to "Camarões",
        "japan" to "Japão",
        "argentina" to "Argentina",
        "australia" to "Austrália",
        "france" to "França",
        "brazil" to "Brasil",
        "south korea" to "Coreia do Sul",
        "korea republic" to "Coreia do Sul",
        "portugal" to "Portugal",
        "ghana" to "Gana",
        "italy" to "Itália",
        "nigeria" to "Nigéria",
        "germany" to "Alemanha",
        "england" to "Inglaterra",
        "senegal" to "Senegal",
        "colombia" to "Colômbia",
        "netherlands" to "Holanda",
        "morocco" to "Marrocos",
        "spain" to "Espanha",
        "canada" to "Canadá"
    )

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == footballId
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        logger.info("Starting World Cup matches sync with resilience mode active.")
        val incomingMatches = try {
            self.fetchFromFootballData(sportId, leagueId)
        } catch (e: Exception) {
            logger.error("Failed to sync matches after trying all external providers: ${e.message}")
            throw RuntimeException("World Cup sync failed. Both external APIs are currently down.", e)
        }

        if (incomingMatches.isNotEmpty()) {
            performUpsert(incomingMatches)
        } else {
            logger.warn("No matches retrieved from any provider. Local database remains unchanged.")
        }
    }

    private fun translateStage(stage: String?): String {
        return when (stage?.uppercase()) {
            "GROUP_STAGE" -> "Fase de Grupos"
            "LAST_32", "ROUND_OF_32" -> "Dezesseis-avos de Final"
            "LAST_16", "ROUND_OF_16" -> "Oitavas de Final"
            "QUARTER_FINALS" -> "Quartas de Final"
            "SEMI_FINALS" -> "Semifinal"
            "THIRD_PLACE" -> "Disputa do 3º Lugar"
            "FINAL" -> "Grande Final"
            else -> stage ?: "Fase de Grupos"
        }
    }

    @CircuitBreaker(name = "footballDataApi", fallbackMethod = "fetchFromApiFootball")
    @Retry(name = "footballDataApi")
    fun fetchFromFootballData(sportId: UUID, leagueId: UUID): List<MatchJpaEntity> {
        logger.info("Trying primary provider: Football-Data API")
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(worldCupLeagueId)
        val targetSeasonId = activeSeason?.id ?: worldCupSeasonId
        val externalMatches = footballDataClient.fetchMatches("WC")
        return externalMatches.map { match ->
            val homeTranslated = translateTeamName(match.homeTeam.shortName ?: match.homeTeam.name ?: "A definir")
            val awayTranslated = translateTeamName(match.awayTeam.shortName ?: match.awayTeam.name ?: "A definir")
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = worldCupLeagueId,
                seasonId = targetSeasonId,
                homeTeamName = homeTranslated,
                awayTeamName = awayTranslated,
                kickoffTime = Instant.parse(match.utcDate),
                status = mapFootballDataStatus(match.status),
                homeScore = match.score?.fullTime?.home,
                awayScore = match.score?.fullTime?.away,
                phase = translateStage(match.stage),
                updatedAt = Instant.now()
            )
        }
    }

    @CircuitBreaker(name = "apiFootballApi", fallbackMethod = "fetchMatchesLocalFallback")
    @Retry(name = "apiFootballApi")
    fun fetchFromApiFootball(sportId: UUID, leagueId: UUID, exception: Throwable): List<MatchJpaEntity> {
        logger.warn("Primary provider (Football-Data) failed. Error: ${exception.message}. Falling back to secondary provider: API-Football")
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(worldCupLeagueId)
        val targetSeasonId = activeSeason?.id ?: worldCupSeasonId
        val seasonYear = activeSeason?.externalSeasonCode ?: 2026
        
        val externalFixtures = apiFootballClient.fetchMatches(leagueId = 1, season = seasonYear)
        return externalFixtures.map { wrapper ->
            val homeTranslated = translateTeamName(wrapper.teams.home.name)
            val awayTranslated = translateTeamName(wrapper.teams.away.name)
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = worldCupLeagueId,
                seasonId = targetSeasonId,
                homeTeamName = homeTranslated,
                awayTeamName = awayTranslated,
                kickoffTime = Instant.parse(wrapper.fixture.date),
                status = mapApiFootballStatus(wrapper.fixture.status.short),
                homeScore = wrapper.goals.home,
                awayScore = wrapper.goals.away,
                phase = "Fase de Grupos",
                updatedAt = Instant.now()
            )
        }
    }

    fun fetchMatchesLocalFallback(sportId: UUID, leagueId: UUID, exception: Throwable): List<MatchJpaEntity> {
        logger.error("Secondary provider (API-Football) also failed. Both APIs are unavailable. Error: ${exception.message}", exception)
        throw exception
    }

    fun fetchMatchesLocalFallback(sportId: UUID, leagueId: UUID, exception: Throwable, fallbackException: Throwable): List<MatchJpaEntity> {
        logger.error("Secondary provider (API-Football) also failed. Both APIs are unavailable. Errors: [Primary: ${exception.message}, Secondary: ${fallbackException.message}]", fallbackException)
        throw fallbackException
    }

    override fun syncNews(sportId: UUID) {
        logger.info("Starting World Cup news sync.")
        val incomingNews = try {
            self.fetchNewsFromApi()
        } catch (e: Exception) {
            logger.error("Failed to sync news from NewsAPI: ${e.message}")
            // Mantém o cache atual em vez de estourar erro ou limpar, mantendo resiliência total!
            return
        }

        if (incomingNews.isNotEmpty()) {
            cacheNewsInRedis(sportId, incomingNews)
        } else {
            logger.warn("No news articles retrieved from provider. Cache remains unchanged.")
        }
    }

    @CircuitBreaker(name = "newsApi")
    @Retry(name = "newsApi")
    fun fetchNewsFromApi(): List<com.ligadospalpites.sportsfeed.infrastructure.client.NewsApiArticle> {
        logger.info("Calling NewsAPI client...")
        return newsApiClient.fetchNews()
    }

    private fun cacheNewsInRedis(sportId: UUID, articles: List<com.ligadospalpites.sportsfeed.infrastructure.client.NewsApiArticle>) {
        try {
            logger.info("Caching ${articles.size} news articles in Redis for sport: $sportId")
            val topArticles = articles
                .filter { !it.title.isNullOrBlank() }
                .distinctBy { it.title.lowercase().trim() }
                .take(10)
                .map { art ->
                    mapOf(
                        "title" to art.title,
                        "url" to art.url,
                        "urlToImage" to (art.urlToImage ?: "https://ge.globo.com/image_default.png"),
                        "author" to (art.author ?: "Liga dos Palpites"),
                        "description" to (art.description ?: art.content ?: "Matéria completa disponível no link abaixo."),
                        "category" to "Copa do Mundo"
                    )
                }
            val json = objectMapper.writeValueAsString(topArticles)
            redisTemplate.opsForValue().set("news:$sportId", json)
            logger.info("News cached in Redis successfully under key 'news:$sportId'.")
        } catch (e: Exception) {
            logger.error("Error caching news in Redis: ${e.message}", e)
        }
    }

    private fun performUpsert(incoming: List<MatchJpaEntity>) {
        logger.info("Performing intelligent upsert on ${incoming.size} matches to protect user predictions.")
        val existing = matchRepository.findByLeagueId(worldCupLeagueId)

        val toSave = incoming.map { inc ->
            val matchMatch = existing.find { ext ->
                ext.homeTeamName.lowercase() == inc.homeTeamName.lowercase() &&
                ext.awayTeamName.lowercase() == inc.awayTeamName.lowercase()
            }

            if (matchMatch != null) {
                MatchJpaEntity(
                    id = matchMatch.id,
                    sportId = inc.sportId,
                    leagueId = inc.leagueId,
                    seasonId = matchMatch.seasonId,
                    homeTeamName = matchMatch.homeTeamName,
                    awayTeamName = matchMatch.awayTeamName,
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
        logger.info("Successfully updated/inserted ${toSave.size} matches without deleting user predictions.")
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

    private fun mapFootballDataStatus(status: String): MatchStatus {
        return when (status.uppercase()) {
            "TIMED", "SCHEDULED", "CALENDAR" -> MatchStatus.SCHEDULED
            "IN_PLAY", "PAUSED" -> MatchStatus.LIVE
            "FINISHED" -> MatchStatus.FINISHED
            "CANCELLED", "SUSPENDED" -> MatchStatus.CANCELLED
            else -> MatchStatus.SCHEDULED
        }
    }

    private fun mapApiFootballStatus(shortStatus: String): MatchStatus {
        return when (shortStatus.uppercase()) {
            "NS", "TBD" -> MatchStatus.SCHEDULED
            "1H", "2H", "HT", "ET", "BT", "P", "INT" -> MatchStatus.LIVE
            "FT", "AET", "PEN" -> MatchStatus.FINISHED
            "CAN", "PST", "ABD" -> MatchStatus.CANCELLED
            else -> MatchStatus.SCHEDULED
        }
    }
}
