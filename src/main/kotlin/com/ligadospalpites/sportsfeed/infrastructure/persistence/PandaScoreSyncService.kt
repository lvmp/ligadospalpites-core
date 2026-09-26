package com.ligadospalpites.sportsfeed.infrastructure.persistence

import com.ligadospalpites.sportsfeed.application.usecases.LeagueSyncService
import com.ligadospalpites.sportsfeed.domain.events.MatchFinishedEvent
import com.ligadospalpites.sportsfeed.domain.events.MatchStartedEvent
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.client.PandaScoreClient
import com.ligadospalpites.sportsfeed.infrastructure.client.PandaScoreMatchResponse
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class EsportsLeagueMetadata(
    val id: UUID,
    val defaultName: String,
    val searchTerm: String,
    val videogameSlug: String,
    val logoUrl: String? = null
)

@Service
@Profile("!integration")
class PandaScoreSyncService(
    private val matchRepository: SpringDataMatchRepository,
    private val pandaScoreClient: PandaScoreClient,
    private val seasonRepository: SpringDataSeasonRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val leagueRepository: SpringDataLeagueRepository
) : LeagueSyncService {

    private val logger = LoggerFactory.getLogger(PandaScoreSyncService::class.java)

    @Autowired
    @org.springframework.context.annotation.Lazy
    private lateinit var self: PandaScoreSyncService

    val esportsId: UUID = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")

    private val leaguesMetadata = mapOf(
        UUID.fromString("7c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("7c1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "League of Legends - CBLOL",
            searchTerm = "CBLOL",
            videogameSlug = "league-of-legends",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/league-of-legends.png"
        ),
        UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Valorant - VCT Americas",
            searchTerm = "VCT Americas",
            videogameSlug = "valorant",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/valorant.png"
        ),
        UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Counter-Strike 2 - Major",
            searchTerm = "Major",
            videogameSlug = "csgo",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/csgo.png"
        ),
        UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "League of Legends - Worlds",
            searchTerm = "World Championship",
            videogameSlug = "league-of-legends",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/league-of-legends.png"
        ),
        UUID.fromString("bc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("bc1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Counter-Strike 2 - ESL Pro League",
            searchTerm = "ESL Pro League",
            videogameSlug = "csgo",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/csgo.png"
        ),
        UUID.fromString("cc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("cc1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Counter-Strike 2 - BLAST Premier",
            searchTerm = "BLAST Premier",
            videogameSlug = "csgo",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/csgo.png"
        ),
        UUID.fromString("dc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("dc1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Valorant - VCT Champions",
            searchTerm = "VCT Champions",
            videogameSlug = "valorant",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/valorant.png"
        )
    )

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == esportsId && leaguesMetadata.containsKey(leagueId)
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val metadata = leaguesMetadata[leagueId] ?: return
        logger.info("Starting eSports games sync for league: ${metadata.defaultName}")
        ensureLeagueLogo(leagueId, metadata.logoUrl)

        // Always purge synthetic baseline placeholder matches
        purgeSyntheticBaselineMatches(leagueId)

        val incomingMatches = try {
            self.fetchFromPandaScore(sportId, leagueId)
        } catch (e: Exception) {
            logger.error("Failed to fetch eSports matches from PandaScore API: ${e.message}")
            emptyList()
        }

        if (incomingMatches.isNotEmpty()) {
            performUpsert(leagueId, incomingMatches)
        } else {
            logger.info("No active or historical games retrieved for eSports league ${metadata.defaultName}. League is currently off-season.")
        }
    }

    @CircuitBreaker(name = "pandaScoreApi", fallbackMethod = "fetchMatchesLocalFallback")
    @Retry(name = "pandaScoreApi")
    fun fetchFromPandaScore(sportId: UUID, leagueId: UUID): List<MatchJpaEntity> {
        val metadata = leaguesMetadata[leagueId] ?: throw IllegalArgumentException("Invalid league ID: $leagueId")
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: throw IllegalStateException("No active season found for eSports league: $leagueId")

        val externalGames = pandaScoreClient.fetchMatches(
            searchTerm = metadata.searchTerm,
            videogameSlug = metadata.videogameSlug
        )

        // If returned from videogame-wide fallback, filter relevant games
        val relevantGames = externalGames.filter { game ->
            val leagueMatches = game.league?.name?.contains(metadata.searchTerm, ignoreCase = true) == true
            val serieMatches = game.serie?.full_name?.contains(metadata.searchTerm, ignoreCase = true) == true
            val nameMatches = game.name?.contains(metadata.searchTerm, ignoreCase = true) == true
            leagueMatches || serieMatches || nameMatches
        }
        val targetGames = if (relevantGames.isNotEmpty()) relevantGames else externalGames

        val seasonStart = activeSeason.startDate.minus(14, ChronoUnit.DAYS)
        val seasonEnd = activeSeason.endDate.plus(14, ChronoUnit.DAYS)

        return targetGames.mapNotNull { game ->
            if (game.opponents.size < 2) return@mapNotNull null
            val homeOpponent = game.opponents[0].opponent ?: return@mapNotNull null
            val awayOpponent = game.opponents[1].opponent ?: return@mapNotNull null

            val kickoffInstant = parseIsoInstant(game.begin_at)
            // Filtra rigorosamente por partidas pertencentes à janela da temporada ativa
            if (kickoffInstant.isBefore(seasonStart) || kickoffInstant.isAfter(seasonEnd)) {
                return@mapNotNull null
            }

            val mappedStatus = mapPandaScoreStatus(game.status)
            val homeScore = if (mappedStatus == MatchStatus.SCHEDULED) null else game.results.find { it.team_id == homeOpponent.id }?.score
            val awayScore = if (mappedStatus == MatchStatus.SCHEDULED) null else game.results.find { it.team_id == awayOpponent.id }?.score

            val streamUrl = game.streams_list.firstOrNull { it.language?.startsWith("pt", ignoreCase = true) == true }?.raw_url
                ?: game.streams_list.firstOrNull { it.main }?.raw_url
                ?: game.streams_list.firstOrNull()?.raw_url

            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = esportsId,
                leagueId = leagueId,
                seasonId = targetSeasonId,
                homeTeamName = homeOpponent.name,
                awayTeamName = awayOpponent.name,
                homeTeamLogoUrl = homeOpponent.image_url,
                awayTeamLogoUrl = awayOpponent.image_url,
                kickoffTime = kickoffInstant,
                status = mappedStatus,
                homeScore = homeScore,
                awayScore = awayScore,
                phase = game.serie?.full_name ?: "Fase Principal",
                numberOfGames = game.number_of_games ?: 1,
                streamUrl = streamUrl,
                updatedAt = Instant.now()
            )
        }
    }

    fun fetchMatchesLocalFallback(sportId: UUID, leagueId: UUID, exception: Throwable): List<MatchJpaEntity> {
        logger.error("PandaScore circuit breaker activated or call failed. Error: ${exception.message}")
        return emptyList()
    }

    override fun syncNews(sportId: UUID) {
        // News sync not required for eSports
    }

    internal fun performUpsert(leagueId: UUID, incoming: List<MatchJpaEntity>) {
        logger.info("Performing intelligent upsert on ${incoming.size} eSports matches for league: $leagueId")
        purgeSyntheticBaselineMatches(leagueId)
        val cleanExisting = matchRepository.findByLeagueId(leagueId)

        val toSave = incoming.map { inc ->
            var isReversed = false
            val matchMatch = cleanExisting.find { ext ->
                val direct = ext.homeTeamName.equals(inc.homeTeamName, ignoreCase = true) &&
                             ext.awayTeamName.equals(inc.awayTeamName, ignoreCase = true)
                if (direct) {
                    val hoursDiff = java.time.Duration.between(ext.kickoffTime, inc.kickoffTime).abs().toHours()
                    if (hoursDiff < 72) return@find true
                }

                val reversed = ext.homeTeamName.equals(inc.awayTeamName, ignoreCase = true) &&
                               ext.awayTeamName.equals(inc.homeTeamName, ignoreCase = true)
                if (reversed) {
                    val hoursDiff = java.time.Duration.between(ext.kickoffTime, inc.kickoffTime).abs().toHours()
                    if (hoursDiff < 72) {
                        isReversed = true
                        return@find true
                    }
                }
                false
            }

            if (matchMatch != null) {
                if (matchMatch.status == MatchStatus.SCHEDULED && inc.status == MatchStatus.LIVE) {
                    logger.info("eSports match started event published: ${matchMatch.id} (${inc.homeTeamName} x ${inc.awayTeamName})")
                    eventPublisher.publishEvent(MatchStartedEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, inc.sportId, inc.leagueId))
                }

                val targetHomeScore = if (inc.status == MatchStatus.SCHEDULED) null else if (isReversed) inc.awayScore else inc.homeScore
                val targetAwayScore = if (inc.status == MatchStatus.SCHEDULED) null else if (isReversed) inc.homeScore else inc.awayScore

                if (matchMatch.status != MatchStatus.FINISHED && inc.status == MatchStatus.FINISHED) {
                    logger.info("eSports match finished event published: ${matchMatch.id} (${inc.homeTeamName} x ${inc.awayTeamName})")
                    eventPublisher.publishEvent(MatchFinishedEvent(matchMatch.id, matchMatch.homeTeamName, matchMatch.awayTeamName, targetHomeScore ?: 0, targetAwayScore ?: 0, inc.sportId, inc.leagueId))
                }

                MatchJpaEntity(
                    id = matchMatch.id,
                    sportId = inc.sportId,
                    leagueId = inc.leagueId,
                    seasonId = matchMatch.seasonId,
                    homeTeamName = matchMatch.homeTeamName,
                    awayTeamName = matchMatch.awayTeamName,
                    homeTeamLogoUrl = (if (isReversed) inc.awayTeamLogoUrl else inc.homeTeamLogoUrl) ?: matchMatch.homeTeamLogoUrl,
                    awayTeamLogoUrl = (if (isReversed) inc.homeTeamLogoUrl else inc.awayTeamLogoUrl) ?: matchMatch.awayTeamLogoUrl,
                    kickoffTime = inc.kickoffTime,
                    status = inc.status,
                    homeScore = targetHomeScore,
                    awayScore = targetAwayScore,
                    phase = inc.phase,
                    numberOfGames = inc.numberOfGames ?: matchMatch.numberOfGames,
                    streamUrl = inc.streamUrl ?: matchMatch.streamUrl,
                    updatedAt = Instant.now()
                )
            } else {
                inc
            }
        }

        matchRepository.saveAll(toSave)
        logger.info("Successfully saved ${toSave.size} eSports matches for league $leagueId")
    }

    private fun purgeSyntheticBaselineMatches(leagueId: UUID) {
        val existing = matchRepository.findByLeagueId(leagueId)
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val toDelete = mutableListOf<MatchJpaEntity>()
        val toUpdate = mutableListOf<MatchJpaEntity>()

        for (match in existing) {
            val isSynthetic = match.homeTeamLogoUrl?.contains("dicebear") == true || match.awayTeamLogoUrl?.contains("dicebear") == true
            val isOutOfSeason = if (activeSeason != null && match.seasonId == activeSeason.id) {
                match.kickoffTime.isBefore(activeSeason.startDate.minus(14, ChronoUnit.DAYS)) ||
                match.kickoffTime.isAfter(activeSeason.endDate.plus(14, ChronoUnit.DAYS))
            } else false

            if (isSynthetic || isOutOfSeason) {
                toDelete.add(match)
            } else if (match.status == MatchStatus.SCHEDULED && (match.homeScore != null || match.awayScore != null)) {
                toUpdate.add(
                    MatchJpaEntity(
                        id = match.id,
                        sportId = match.sportId,
                        leagueId = match.leagueId,
                        seasonId = match.seasonId,
                        homeTeamName = match.homeTeamName,
                        awayTeamName = match.awayTeamName,
                        homeTeamLogoUrl = match.homeTeamLogoUrl,
                        awayTeamLogoUrl = match.awayTeamLogoUrl,
                        kickoffTime = match.kickoffTime,
                        status = match.status,
                        homeScore = null,
                        awayScore = null,
                        phase = match.phase,
                        periodScoresJson = match.periodScoresJson,
                        numberOfGames = match.numberOfGames,
                        streamUrl = match.streamUrl,
                        updatedAt = Instant.now()
                    )
                )
            }
        }

        if (toDelete.isNotEmpty()) {
            logger.info("Purging ${toDelete.size} synthetic or out-of-season matches for league: $leagueId")
            matchRepository.deleteAll(toDelete)
        }
        if (toUpdate.isNotEmpty()) {
            logger.info("Sanitizing ${toUpdate.size} scheduled matches with non-null scores for league: $leagueId")
            matchRepository.saveAll(toUpdate)
        }
    }

    private fun mapPandaScoreStatus(status: String?): MatchStatus {
        return when (status?.lowercase()) {
            "not_started" -> MatchStatus.SCHEDULED
            "running" -> MatchStatus.LIVE
            "finished" -> MatchStatus.FINISHED
            "canceled", "postponed" -> MatchStatus.CANCELLED
            else -> MatchStatus.SCHEDULED
        }
    }

    private fun parseIsoInstant(dateStr: String?): Instant {
        if (dateStr.isNullOrBlank()) return Instant.now()
        return try {
            Instant.parse(dateStr)
        } catch (e: Exception) {
            Instant.now()
        }
    }

    private fun ensureLeagueLogo(leagueId: UUID, logoUrl: String?) {
        if (logoUrl.isNullOrBlank()) return
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
            }
        }
    }
}
