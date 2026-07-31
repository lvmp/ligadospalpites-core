package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.application.usecases.LeagueSyncService
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.domain.events.MatchStartedEvent
import com.ligadospalpites.sportsfeed.domain.events.MatchGoalEvent
import com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent
import com.ligadospalpites.sportsfeed.infrastructure.client.ApiFootballClient
import com.ligadospalpites.sportsfeed.infrastructure.client.EspnSoccerClient
import com.ligadospalpites.sportsfeed.infrastructure.client.FootballDataClient
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class FootballLeagueMetadata(
    val id: UUID,
    val footballDataCode: String?,
    val apiFootballId: Int,
    val defaultName: String,
    val logoUrl: String? = null,
    val isLibertadores: Boolean = false,
    val isCopaDoBrasil: Boolean = false
)

@Service
@Profile("!integration")
class FootballGenericSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val footballDataClient: FootballDataClient,
    private val apiFootballClient: ApiFootballClient,
    private val espnSoccerClient: EspnSoccerClient,
    private val seasonRepository: SpringDataSeasonRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val leagueRepository: SpringDataLeagueRepository? = null
) : LeagueSyncService {

    private val logger = LoggerFactory.getLogger(FootballGenericSyncService::class.java)

    @Autowired
    @org.springframework.context.annotation.Lazy
    private lateinit var self: FootballGenericSyncService

    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")

    private val leaguesMetadata = mapOf(
        UUID.fromString("3dbd8422-9e22-4411-b0db-b06d0421da6a") to FootballLeagueMetadata(
            id = UUID.fromString("3dbd8422-9e22-4411-b0db-b06d0421da6a"),
            footballDataCode = "BSA",
            apiFootballId = 71,
            defaultName = "Campeonato Brasileiro",
            logoUrl = "https://media.api-sports.io/football/leagues/71.png"
        ),
        UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = null,
            apiFootballId = 13,
            defaultName = "Copa Libertadores",
            logoUrl = "https://media.api-sports.io/football/leagues/13.png",
            isLibertadores = true
        ),
        UUID.fromString("9284ca51-bb54-47c1-841f-81ab28120fa2") to FootballLeagueMetadata(
            id = UUID.fromString("9284ca51-bb54-47c1-841f-81ab28120fa2"),
            footballDataCode = "PD",
            apiFootballId = 140,
            defaultName = "Campeonato Espanhol",
            logoUrl = "https://media.api-sports.io/football/leagues/140.png"
        ),
        UUID.fromString("827d043c-62c2-402c-b011-3ba2849e7b23") to FootballLeagueMetadata(
            id = UUID.fromString("827d043c-62c2-402c-b011-3ba2849e7b23"),
            footballDataCode = "PL",
            apiFootballId = 39,
            defaultName = "Campeonato Inglês",
            logoUrl = "https://media.api-sports.io/football/leagues/39.png"
        ),
        UUID.fromString("e2d03a11-b9db-44ab-ba02-411a0c0bcf14") to FootballLeagueMetadata(
            id = UUID.fromString("e2d03a11-b9db-44ab-ba02-411a0c0bcf14"),
            footballDataCode = "CL",
            apiFootballId = 2,
            defaultName = "UEFA Champions League",
            logoUrl = "https://media.api-sports.io/football/leagues/2.png"
        ),
        UUID.fromString("5acdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("5acdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = "ELC",
            apiFootballId = 40,
            defaultName = "Championship",
            logoUrl = "https://media.api-sports.io/football/leagues/40.png"
        ),
        UUID.fromString("6acdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("6acdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = "EC",
            apiFootballId = 4,
            defaultName = "European Championship",
            logoUrl = "https://media.api-sports.io/football/leagues/4.png"
        ),
        UUID.fromString("7acdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("7acdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = "FL1",
            apiFootballId = 61,
            defaultName = "Ligue 1",
            logoUrl = "https://media.api-sports.io/football/leagues/61.png"
        ),
        UUID.fromString("8acdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("8acdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = "BL1",
            apiFootballId = 78,
            defaultName = "Bundesliga",
            logoUrl = "https://media.api-sports.io/football/leagues/78.png"
        ),
        UUID.fromString("9acdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("9acdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = "SA",
            apiFootballId = 135,
            defaultName = "Serie A",
            logoUrl = "https://media.api-sports.io/football/leagues/135.png"
        ),
        UUID.fromString("aacdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("aacdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = "DED",
            apiFootballId = 88,
            defaultName = "Eredivisie",
            logoUrl = "https://media.api-sports.io/football/leagues/88.png"
        ),
        UUID.fromString("bacdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("bacdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = "PPL",
            apiFootballId = 94,
            defaultName = "Primeira Liga",
            logoUrl = "https://media.api-sports.io/football/leagues/94.png"
        ),
        UUID.fromString("b3cdf011-fbde-4122-83bc-c46b1ba847de") to FootballLeagueMetadata(
            id = UUID.fromString("b3cdf011-fbde-4122-83bc-c46b1ba847de"),
            footballDataCode = null,
            apiFootballId = 73,
            defaultName = "Copa do Brasil",
            logoUrl = "https://media.api-sports.io/football/leagues/73.png",
            isCopaDoBrasil = true
        )
    )

    private val teamNameTranslations = mapOf(
        "real madrid" to "Real Madrid",
        "barcelona" to "Barcelona",
        "atletico madrid" to "Atlético de Madrid",
        "manchester city" to "Manchester City",
        "manchester united" to "Manchester United",
        "liverpool" to "Liverpool",
        "chelsea" to "Chelsea",
        "arsenal" to "Arsenal",
        "bayern munich" to "Bayern de Munique",
        "paris saint germain" to "PSG",
        "juventus" to "Juventus",
        "inter" to "Inter de Milão",
        "milan" to "Milan",
        "flamengo" to "Flamengo",
        "palmeiras" to "Palmeiras",
        "sao paulo" to "São Paulo",
        "corinthians" to "Corinthians",
        "gremio" to "Grêmio",
        "internacional" to "Internacional",
        "atletico mineiro" to "Atlético-MG",
        "atletico paranaense" to "Athletico-PR",
        "fluminense" to "Fluminense",
        "botafogo" to "Botafogo",
        "vasco da gama" to "Vasco da Gama",
        "cruzeiro" to "Cruzeiro",
        "santos" to "Santos",
        "bahia" to "Bahia",
        "fortaleza" to "Fortaleza",
        "bragantino" to "Red Bull Bragantino",
        "america mineiro" to "América-MG"
    )

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == footballId && leaguesMetadata.containsKey(leagueId)
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val metadata = leaguesMetadata[leagueId] ?: return
        logger.info("Starting matches sync for football league: ${metadata.defaultName}")
        ensureLeagueLogo(leagueId, metadata.logoUrl)

        val incomingMatches = try {
            if (metadata.footballDataCode != null) {
                self.fetchFromFootballData(sportId, leagueId)
            } else {
                logger.info("Ingesting matches via API-Football for league ${metadata.defaultName} (ID: ${metadata.apiFootballId})")
                self.fetchFromApiFootball(sportId, leagueId, IllegalStateException("Primary provider for ${metadata.defaultName} is API-Football"))
            }
        } catch (e: Exception) {
            logger.error("Failed to sync matches after trying external providers: ${e.message}")
            throw RuntimeException("Football sync failed for league ${metadata.defaultName}.", e)
        }

        if (incomingMatches.isNotEmpty()) {
            performUpsert(leagueId, incomingMatches)
        } else {
            logger.warn("No matches retrieved for league ${metadata.defaultName}. Local database unchanged.")
        }
    }

    @CircuitBreaker(name = "espnSoccerApi", fallbackMethod = "fetchFromApiFootball")
    @Retry(name = "espnSoccerApi")
    fun fetchFromEspnLibertadores(sportId: UUID, leagueId: UUID): List<MatchJpaEntity> {
        val metadata = leaguesMetadata[leagueId] ?: throw IllegalArgumentException("Invalid league ID: $leagueId")
        logger.info("Trying primary provider (ESPN API) for Libertadores league: ${metadata.defaultName}")
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: throw IllegalStateException("No active season found for league: $leagueId")

        val events = espnSoccerClient.fetchLibertadoresMatches()
        return events.mapNotNull { event ->
            val comp = event.competitions.firstOrNull() ?: return@mapNotNull null
            val homeComp = comp.competitors.find { it.homeAway == "home" } ?: return@mapNotNull null
            val awayComp = comp.competitors.find { it.homeAway == "away" } ?: return@mapNotNull null

            val homeName = translateTeamName(homeComp.team.displayName ?: homeComp.team.name ?: "A definir")
            val awayName = translateTeamName(awayComp.team.displayName ?: awayComp.team.name ?: "A definir")

            val statusState = comp.status?.type?.state ?: "pre"
            val status = mapEspnStatus(statusState)
            val kickoff = try { Instant.parse(event.date) } catch (e: Exception) { Instant.now() }

            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = leagueId,
                seasonId = targetSeasonId,
                homeTeamName = homeName,
                awayTeamName = awayName,
                homeTeamLogoUrl = homeComp.team.logo,
                awayTeamLogoUrl = awayComp.team.logo,
                kickoffTime = kickoff,
                status = status,
                homeScore = homeComp.score?.toIntOrNull(),
                awayScore = awayComp.score?.toIntOrNull(),
                phase = comp.status?.type?.description ?: "Fase de Grupos",
                updatedAt = Instant.now()
            )
        }
    }

    @CircuitBreaker(name = "footballDataApi", fallbackMethod = "fetchFromApiFootball")
    @Retry(name = "footballDataApi")
    fun fetchFromFootballData(sportId: UUID, leagueId: UUID): List<MatchJpaEntity> {
        val metadata = leaguesMetadata[leagueId] ?: throw IllegalArgumentException("Invalid league ID: $leagueId")
        val code = metadata.footballDataCode ?: throw UnsupportedOperationException("Football-Data does not support league ${metadata.defaultName}")

        logger.info("Trying primary provider (Football-Data API) for league: ${metadata.defaultName}")
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: throw IllegalStateException("No active season found for league: $leagueId")

        val externalMatches = footballDataClient.fetchMatches(code)
        return externalMatches.map { match ->
            val homeTranslated = translateTeamName(match.homeTeam.shortName ?: match.homeTeam.name ?: "A definir")
            val awayTranslated = translateTeamName(match.awayTeam.shortName ?: match.awayTeam.name ?: "A definir")
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = leagueId,
                seasonId = targetSeasonId,
                homeTeamName = homeTranslated,
                awayTeamName = awayTranslated,
                homeTeamLogoUrl = match.homeTeam.crest,
                awayTeamLogoUrl = match.awayTeam.crest,
                kickoffTime = Instant.parse(match.utcDate),
                status = mapFootballDataStatus(match.status),
                homeScore = match.score?.fullTime?.home,
                awayScore = match.score?.fullTime?.away,
                phase = translateStage(match.stage, match.matchday),
                updatedAt = Instant.now()
            )
        }
    }

    @CircuitBreaker(name = "apiFootballApi", fallbackMethod = "fetchMatchesLocalFallback")
    @Retry(name = "apiFootballApi")
    fun fetchFromApiFootball(sportId: UUID, leagueId: UUID, exception: Throwable): List<MatchJpaEntity> {
        val metadata = leaguesMetadata[leagueId] ?: throw IllegalArgumentException("Invalid league ID: $leagueId")
        logger.warn("Primary provider (Football-Data) failed or is unsupported. Error: ${exception.message}. Falling back to secondary provider: API-Football")

        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: throw IllegalStateException("No active season found for league: $leagueId")
        val seasonYear = activeSeason.externalSeasonCode

        val externalFixtures = apiFootballClient.fetchMatches(leagueId = metadata.apiFootballId, season = seasonYear)
        return externalFixtures.map { wrapper ->
            val homeTranslated = translateTeamName(wrapper.teams.home.name)
            val awayTranslated = translateTeamName(wrapper.teams.away.name)
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = leagueId,
                seasonId = targetSeasonId,
                homeTeamName = homeTranslated,
                awayTeamName = awayTranslated,
                homeTeamLogoUrl = wrapper.teams.home.logo,
                awayTeamLogoUrl = wrapper.teams.away.logo,
                kickoffTime = Instant.parse(wrapper.fixture.date),
                status = mapApiFootballStatus(wrapper.fixture.status.short),
                homeScore = wrapper.goals.home,
                awayScore = wrapper.goals.away,
                phase = translateStage(wrapper.league?.round, null),
                updatedAt = Instant.now()
            )
        }
    }

    fun fetchMatchesLocalFallback(sportId: UUID, leagueId: UUID, exception: Throwable): List<MatchJpaEntity> {
        logger.error("Secondary provider (API-Football) also failed. Both APIs are unavailable. Error: ${exception.message}", exception)
        throw exception
    }

    override fun syncNews(sportId: UUID) {
        // News logic not required for generic leagues
    }

    internal fun performUpsert(leagueId: UUID, incoming: List<MatchJpaEntity>) {
        logger.info("Performing intelligent upsert on ${incoming.size} matches to protect user predictions for league: $leagueId")
        val existing = matchRepository.findByLeagueId(leagueId)

        val toSave = incoming.map { inc ->
            val matchMatch = existing.find { ext ->
                ext.homeTeamName.lowercase() == inc.homeTeamName.lowercase() &&
                ext.awayTeamName.lowercase() == inc.awayTeamName.lowercase()
            }

            if (matchMatch != null) {
                // Publish events based on state transitions
                if (matchMatch.status == MatchStatus.SCHEDULED && inc.status == MatchStatus.LIVE) {
                    logger.info("Match started event published for match ${matchMatch.id}: ${inc.homeTeamName} x ${inc.awayTeamName}")
                    eventPublisher.publishEvent(MatchStartedEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, inc.sportId, inc.leagueId))
                }

                if (matchMatch.status == MatchStatus.LIVE && inc.status == MatchStatus.LIVE) {
                    val oldHome = matchMatch.homeScore ?: 0
                    val oldAway = matchMatch.awayScore ?: 0
                    val newHome = inc.homeScore ?: 0
                    val newAway = inc.awayScore ?: 0
                    if (newHome > oldHome) {
                        logger.info("Match goal event published (Home team scored) for match ${matchMatch.id}")
                        eventPublisher.publishEvent(MatchGoalEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, newHome, newAway, "HOME", inc.sportId, inc.leagueId))
                    } else if (newAway > oldAway) {
                        logger.info("Match goal event published (Away team scored) for match ${matchMatch.id}")
                        eventPublisher.publishEvent(MatchGoalEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, newHome, newAway, "AWAY", inc.sportId, inc.leagueId))
                    }
                }

                if (matchMatch.status != MatchStatus.FINISHED && inc.status == MatchStatus.FINISHED) {
                    logger.info("Match finished event published for match ${matchMatch.id}: ${inc.homeTeamName} x ${inc.awayTeamName}")
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
        logger.info("Successfully updated/inserted ${toSave.size} matches for league $leagueId without deleting user predictions.")
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

    private fun translateStage(stage: String?, matchday: Int? = null): String {
        val stageUpper = stage?.uppercase()?.trim() ?: ""
        return when {
            stageUpper.startsWith("GROUP") || stageUpper.contains("FASE DE GRUPOS") || stageUpper == "GROUPS" -> "Fase de Grupos"
            stageUpper.contains("ROUND OF 32") || stageUpper.contains("LAST_32") || stageUpper.contains("16TH") -> "Dezesseis-avos de Final"
            stageUpper.contains("ROUND OF 16") || stageUpper.contains("LAST_16") || stageUpper.contains("8TH") || stageUpper.contains("OITAVAS") -> "Oitavas de Final"
            stageUpper.contains("QUARTER") || stageUpper.contains("QUARTAS") -> "Quartas de Final"
            stageUpper.contains("SEMI") -> "Semifinal"
            stageUpper.contains("THIRD") || stageUpper.contains("3º") -> "Disputa do 3º Lugar"
            stageUpper.contains("FINAL") -> "Grande Final"
            stageUpper.contains("REGULAR") || stageUpper.contains("TURNO") || stageUpper.contains("RODADA") -> {
                determineTurno(stage, matchday)
            }
            else -> {
                if (matchday != null) {
                    determineTurno(stage, matchday)
                } else if (stageUpper.isNotBlank()) {
                    stage!!.trim()
                } else {
                    "1º Turno"
                }
            }
        }
    }

    private fun determineTurno(stage: String?, matchday: Int?): String {
        if (matchday != null) {
            return if (matchday <= 19) "1º Turno" else "2º Turno"
        }
        if (!stage.isNullOrBlank()) {
            val upper = stage.uppercase()
            if (upper.contains("2º") || upper.contains("2_TURNO") || upper.contains("TURNO 2") || upper.endsWith("_2") || upper.endsWith("- 2")) {
                return "2º Turno"
            }
            if (upper.contains("1º") || upper.contains("1_TURNO") || upper.contains("TURNO 1") || upper.endsWith("_1") || upper.endsWith("- 1")) {
                return "1º Turno"
            }
            val matchNumber = Regex("\\d+").find(stage)?.value?.toIntOrNull()
            if (matchNumber != null) {
                return if (matchNumber <= 19) "1º Turno" else "2º Turno"
            }
        }
        return "1º Turno"
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

    private fun mapEspnStatus(state: String): MatchStatus {
        return when (state.lowercase()) {
            "pre" -> MatchStatus.SCHEDULED
            "in" -> MatchStatus.LIVE
            "post" -> MatchStatus.FINISHED
            else -> MatchStatus.SCHEDULED
        }
    }

    private fun ensureLeagueLogo(leagueId: UUID, logoUrl: String?) {
        if (logoUrl.isNullOrBlank() || leagueRepository == null) return
        leagueRepository.findById(leagueId).ifPresent { league ->
            if (league.logoUrl != logoUrl) {
                val updated = LeagueJpaEntity(
                    id = league.id,
                    name = league.name,
                    sportId = league.sportId,
                    isActive = league.isActive,
                    logoUrl = logoUrl,
                    createdAt = league.createdAt
                )
                leagueRepository.save(updated)
                logger.info("Auto-updated logoUrl for league '${league.name}' (ID: $leagueId) to $logoUrl")
            }
        }
    }
}
