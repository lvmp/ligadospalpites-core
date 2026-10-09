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
    val leagueSlug: String? = null,
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
            leagueSlug = "cblol",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/league-of-legends.png"
        ),
        UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Valorant - VCT Americas",
            searchTerm = "VCT Americas",
            videogameSlug = "valorant",
            leagueSlug = "vct-americas",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/valorant.png"
        ),
        UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Counter-Strike 2 - Major",
            searchTerm = "Major",
            videogameSlug = "csgo",
            leagueSlug = "cs-go-major",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/csgo.png"
        ),
        UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "League of Legends - Worlds",
            searchTerm = "World Championship",
            videogameSlug = "league-of-legends",
            leagueSlug = "league-of-legends-world-championship",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/league-of-legends.png"
        ),
        UUID.fromString("bc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("bc1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Counter-Strike 2 - ESL Pro League",
            searchTerm = "ESL Pro League",
            videogameSlug = "csgo",
            leagueSlug = "esl-pro-league",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/csgo.png"
        ),
        UUID.fromString("cc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("cc1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Counter-Strike 2 - BLAST Premier",
            searchTerm = "BLAST Premier",
            videogameSlug = "csgo",
            leagueSlug = "blast-premier",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/csgo.png"
        ),
        UUID.fromString("dc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("dc1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Valorant - VCT Champions",
            searchTerm = "VCT Champions",
            videogameSlug = "valorant",
            leagueSlug = "valorant-champions",
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
            val delegate = if (::self.isInitialized) self else this
            delegate.fetchFromPandaScore(sportId, leagueId)
        } catch (e: Exception) {
            logger.error("Failed to fetch eSports matches from PandaScore API: ${e.message}")
            emptyList()
        }

        if (incomingMatches.isNotEmpty()) {
            performUpsert(leagueId, incomingMatches)
        } else {
            logger.info("No active or historical games retrieved for eSports league ${metadata.defaultName}. League is currently off-season.")
            performUpsert(leagueId, emptyList())
        }
    }

    @CircuitBreaker(name = "pandaScoreApi", fallbackMethod = "fetchMatchesLocalFallback")
    @Retry(name = "pandaScoreApi")
    fun fetchFromPandaScore(sportId: UUID, leagueId: UUID): List<MatchJpaEntity> {
        val metadata = leaguesMetadata[leagueId] ?: throw IllegalArgumentException("Invalid league ID: $leagueId")
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: throw IllegalStateException("No active season found for eSports league: $leagueId")

        val externalGames = pandaScoreClient.fetchMatches(
            leagueIdOrSlug = metadata.leagueSlug,
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

            val dateStr = game.begin_at ?: game.scheduled_at ?: game.original_scheduled_at
            val kickoffInstant = parseIsoInstant(dateStr) ?: return@mapNotNull null
            // Filtra rigorosamente por partidas pertencentes à janela da temporada ativa
            if (kickoffInstant.isBefore(seasonStart) || kickoffInstant.isAfter(seasonEnd)) {
                return@mapNotNull null
            }

            val mappedStatus = mapPandaScoreStatus(game.status)
            val homeScore = if (mappedStatus == MatchStatus.SCHEDULED) null else {
                game.results.find { it.team_id == homeOpponent.id }?.score
                    ?: if (game.results.size >= 2) game.results[0].score else null
            }
            val awayScore = if (mappedStatus == MatchStatus.SCHEDULED) null else {
                game.results.find { it.team_id == awayOpponent.id }?.score
                    ?: if (game.results.size >= 2) game.results[1].score else null
            }

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

    internal fun normalizeEsportsTeamName(name: String): String {
        return name.trim().lowercase()
            .replace(Regex("\\b(esports|gaming|team|club)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    internal fun areTeamNamesMatching(name1: String, name2: String): Boolean {
        if (name1.equals(name2, ignoreCase = true)) return true
        val norm1 = normalizeEsportsTeamName(name1)
        val norm2 = normalizeEsportsTeamName(name2)
        if (norm1.isNotBlank() && norm1 == norm2) return true
        if (norm1.length >= 3 && norm2.length >= 3) {
            if (norm1.contains(norm2) || norm2.contains(norm1)) return true
        }
        return false
    }

    internal fun performUpsert(leagueId: UUID, incoming: List<MatchJpaEntity>) {
        logger.info("Performing intelligent upsert on ${incoming.size} eSports matches for league: $leagueId")
        purgeSyntheticBaselineMatches(leagueId)
        val cleanExisting = matchRepository.findByLeagueId(leagueId)
        val metadata = leaguesMetadata[leagueId]
        val matchedExistingIds = mutableSetOf<UUID>()

        val toSave = incoming.map { inc ->
            var isReversed = false
            val matchMatch = cleanExisting.find { ext ->
                val direct = areTeamNamesMatching(ext.homeTeamName, inc.homeTeamName) &&
                             areTeamNamesMatching(ext.awayTeamName, inc.awayTeamName)
                if (direct) {
                    val hoursDiff = java.time.Duration.between(ext.kickoffTime, inc.kickoffTime).abs().toHours()
                    if (hoursDiff < 72) return@find true
                }

                val reversed = areTeamNamesMatching(ext.homeTeamName, inc.awayTeamName) &&
                               areTeamNamesMatching(ext.awayTeamName, inc.homeTeamName)
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
                matchedExistingIds.add(matchMatch.id)
                if (matchMatch.status == MatchStatus.SCHEDULED && inc.status == MatchStatus.LIVE) {
                    logger.info("eSports match started event published: ${matchMatch.id} (${inc.homeTeamName} x ${inc.awayTeamName})")
                    eventPublisher.publishEvent(MatchStartedEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, inc.sportId, inc.leagueId))
                }

                val incomingHomeScore = if (isReversed) inc.awayScore else inc.homeScore
                val incomingAwayScore = if (isReversed) inc.homeScore else inc.awayScore

                val targetHomeScore = if (inc.status == MatchStatus.SCHEDULED) null else incomingHomeScore ?: matchMatch.homeScore
                val targetAwayScore = if (inc.status == MatchStatus.SCHEDULED) null else incomingAwayScore ?: matchMatch.awayScore

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

        if (toSave.isNotEmpty()) {
            matchRepository.saveAll(toSave)
            logger.info("Successfully saved ${toSave.size} eSports matches for league $leagueId")
        }

        // Active validation with provider for any remaining LIVE matches or FINISHED matches missing scores
        val remainingMatchesToVerify = cleanExisting.filter { ext ->
            (ext.status == MatchStatus.LIVE || (ext.status == MatchStatus.FINISHED && (ext.homeScore == null || ext.awayScore == null))) &&
            !matchedExistingIds.contains(ext.id)
        }
        if (remainingMatchesToVerify.isNotEmpty()) {
            logger.info("Found ${remainingMatchesToVerify.size} match(es) needing provider verification for league: $leagueId")
            for (matchToVerify in remainingMatchesToVerify) {
                verifyAndUpdateLiveMatchWithProvider(matchToVerify, metadata?.videogameSlug)
            }
        }
    }

    private fun verifyAndUpdateLiveMatchWithProvider(liveMatch: MatchJpaEntity, videogameSlug: String?) {
        logger.info("Validating match ${liveMatch.id} (${liveMatch.homeTeamName} x ${liveMatch.awayTeamName}, status=${liveMatch.status}) with PandaScore provider...")

        val now = Instant.now()
        val hoursSinceKickoff = java.time.Duration.between(liveMatch.kickoffTime, now).toHours()

        var candidates = pandaScoreClient.searchMatchesByTeam(liveMatch.homeTeamName, videogameSlug)
        if (candidates.isEmpty()) {
            candidates = pandaScoreClient.searchMatchesByTeam(liveMatch.awayTeamName, videogameSlug)
        }

        val matchedGame = if (candidates.isNotEmpty()) {
            candidates.find { game ->
                if (game.opponents.size < 2) return@find false
                val homeOpp = game.opponents[0].opponent ?: return@find false
                val awayOpp = game.opponents[1].opponent ?: return@find false

                val direct = areTeamNamesMatching(liveMatch.homeTeamName, homeOpp.name) &&
                             areTeamNamesMatching(liveMatch.awayTeamName, awayOpp.name)
                val reversed = areTeamNamesMatching(liveMatch.homeTeamName, awayOpp.name) &&
                               areTeamNamesMatching(liveMatch.awayTeamName, homeOpp.name)

                if (!direct && !reversed) return@find false

                val gameDateStr = game.begin_at ?: game.scheduled_at ?: game.original_scheduled_at
                val gameTime = parseIsoInstant(gameDateStr) ?: return@find false
                val hoursDiff = java.time.Duration.between(liveMatch.kickoffTime, gameTime).abs().toHours()
                hoursDiff < 168
            }
        } else null

        if (matchedGame == null) {
            logger.warn("Could not correlate LIVE match ${liveMatch.id} with any match returned from PandaScore")
            // Auto-resolução para partidas presas como LIVE há mais de 6 horas
            if (hoursSinceKickoff >= 6) {
                logger.warn("Match ${liveMatch.id} has been marked LIVE for $hoursSinceKickoff hours. Auto-resolving stale match.")
                if (liveMatch.homeScore != null && liveMatch.awayScore != null && (liveMatch.homeScore != 0 || liveMatch.awayScore != 0)) {
                    val finishedMatch = MatchJpaEntity(
                        id = liveMatch.id,
                        sportId = liveMatch.sportId,
                        leagueId = liveMatch.leagueId,
                        seasonId = liveMatch.seasonId,
                        homeTeamName = liveMatch.homeTeamName,
                        awayTeamName = liveMatch.awayTeamName,
                        homeTeamLogoUrl = liveMatch.homeTeamLogoUrl,
                        awayTeamLogoUrl = liveMatch.awayTeamLogoUrl,
                        kickoffTime = liveMatch.kickoffTime,
                        status = MatchStatus.FINISHED,
                        homeScore = liveMatch.homeScore,
                        awayScore = liveMatch.awayScore,
                        phase = liveMatch.phase,
                        periodScoresJson = liveMatch.periodScoresJson,
                        numberOfGames = liveMatch.numberOfGames,
                        streamUrl = liveMatch.streamUrl,
                        updatedAt = Instant.now()
                    )
                    matchRepository.save(finishedMatch)
                    eventPublisher.publishEvent(MatchFinishedEvent(
                        matchId = finishedMatch.id,
                        homeTeamName = finishedMatch.homeTeamName,
                        awayTeamName = finishedMatch.awayTeamName,
                        homeScore = liveMatch.homeScore ?: 0,
                        awayScore = liveMatch.awayScore ?: 0,
                        sportId = finishedMatch.sportId,
                        leagueId = finishedMatch.leagueId
                    ))
                    logger.info("Auto-concluded stale LIVE match ${liveMatch.id} as FINISHED with score ${liveMatch.homeScore} x ${liveMatch.awayScore}")
                } else if (hoursSinceKickoff >= 24) {
                    val cancelledMatch = MatchJpaEntity(
                        id = liveMatch.id,
                        sportId = liveMatch.sportId,
                        leagueId = liveMatch.leagueId,
                        seasonId = liveMatch.seasonId,
                        homeTeamName = liveMatch.homeTeamName,
                        awayTeamName = liveMatch.awayTeamName,
                        homeTeamLogoUrl = liveMatch.homeTeamLogoUrl,
                        awayTeamLogoUrl = liveMatch.awayTeamLogoUrl,
                        kickoffTime = liveMatch.kickoffTime,
                        status = MatchStatus.CANCELLED,
                        homeScore = null,
                        awayScore = null,
                        phase = liveMatch.phase,
                        periodScoresJson = liveMatch.periodScoresJson,
                        numberOfGames = liveMatch.numberOfGames,
                        streamUrl = liveMatch.streamUrl,
                        updatedAt = Instant.now()
                    )
                    matchRepository.save(cancelledMatch)
                    logger.info("Auto-cancelled stale LIVE match ${liveMatch.id} ($hoursSinceKickoff hours since kickoff with no scores)")
                }
            }
            return
        }

        val mappedStatus = mapPandaScoreStatus(matchedGame.status)
        logger.info("PandaScore reported status for match ${liveMatch.id}: original='${matchedGame.status}', mapped='$mappedStatus'")

        if (mappedStatus == MatchStatus.FINISHED) {
            val homeOpponent = matchedGame.opponents.find { areTeamNamesMatching(liveMatch.homeTeamName, it.opponent?.name ?: "") }?.opponent
            val awayOpponent = matchedGame.opponents.find { areTeamNamesMatching(liveMatch.awayTeamName, it.opponent?.name ?: "") }?.opponent

            val finalHomeScore = matchedGame.results.find { it.team_id == homeOpponent?.id }?.score
                ?: if (matchedGame.results.size >= 2) {
                    val idx = matchedGame.opponents.indexOfFirst { areTeamNamesMatching(liveMatch.homeTeamName, it.opponent?.name ?: "") }
                    if (idx in matchedGame.results.indices) matchedGame.results[idx].score else matchedGame.results[0].score
                } else null

            val finalAwayScore = matchedGame.results.find { it.team_id == awayOpponent?.id }?.score
                ?: if (matchedGame.results.size >= 2) {
                    val idx = matchedGame.opponents.indexOfFirst { areTeamNamesMatching(liveMatch.awayTeamName, it.opponent?.name ?: "") }
                    if (idx in matchedGame.results.indices) matchedGame.results[idx].score else matchedGame.results[1].score
                } else null

            val resolvedHomeScore = finalHomeScore ?: liveMatch.homeScore ?: 0
            val resolvedAwayScore = finalAwayScore ?: liveMatch.awayScore ?: 0
            val resolvedKickoff = parseIsoInstant(matchedGame.begin_at ?: matchedGame.scheduled_at) ?: liveMatch.kickoffTime

            val finishedMatch = MatchJpaEntity(
                id = liveMatch.id,
                sportId = liveMatch.sportId,
                leagueId = liveMatch.leagueId,
                seasonId = liveMatch.seasonId,
                homeTeamName = liveMatch.homeTeamName,
                awayTeamName = liveMatch.awayTeamName,
                homeTeamLogoUrl = homeOpponent?.image_url ?: liveMatch.homeTeamLogoUrl,
                awayTeamLogoUrl = awayOpponent?.image_url ?: liveMatch.awayTeamLogoUrl,
                kickoffTime = resolvedKickoff,
                status = MatchStatus.FINISHED,
                homeScore = resolvedHomeScore,
                awayScore = resolvedAwayScore,
                phase = matchedGame.serie?.full_name ?: liveMatch.phase,
                periodScoresJson = liveMatch.periodScoresJson,
                numberOfGames = matchedGame.number_of_games ?: liveMatch.numberOfGames,
                streamUrl = liveMatch.streamUrl,
                updatedAt = Instant.now()
            )

            matchRepository.save(finishedMatch)
            eventPublisher.publishEvent(MatchFinishedEvent(
                matchId = finishedMatch.id,
                homeTeamName = finishedMatch.homeTeamName,
                awayTeamName = finishedMatch.awayTeamName,
                homeScore = resolvedHomeScore,
                awayScore = resolvedAwayScore,
                sportId = finishedMatch.sportId,
                leagueId = finishedMatch.leagueId
            ))
            logger.info("Concluded LIVE match ${liveMatch.id} as FINISHED based on PandaScore data. Score: $resolvedHomeScore x $resolvedAwayScore")
        } else if (mappedStatus == MatchStatus.CANCELLED) {
            val cancelledMatch = MatchJpaEntity(
                id = liveMatch.id,
                sportId = liveMatch.sportId,
                leagueId = liveMatch.leagueId,
                seasonId = liveMatch.seasonId,
                homeTeamName = liveMatch.homeTeamName,
                awayTeamName = liveMatch.awayTeamName,
                homeTeamLogoUrl = liveMatch.homeTeamLogoUrl,
                awayTeamLogoUrl = liveMatch.awayTeamLogoUrl,
                kickoffTime = liveMatch.kickoffTime,
                status = MatchStatus.CANCELLED,
                homeScore = liveMatch.homeScore,
                awayScore = liveMatch.awayScore,
                phase = liveMatch.phase,
                periodScoresJson = liveMatch.periodScoresJson,
                numberOfGames = liveMatch.numberOfGames,
                streamUrl = liveMatch.streamUrl,
                updatedAt = Instant.now()
            )
            matchRepository.save(cancelledMatch)
            logger.info("Updated LIVE match ${liveMatch.id} to CANCELLED based on PandaScore data")
        } else if (mappedStatus == MatchStatus.LIVE) {
            val homeOpponent = matchedGame.opponents.find { areTeamNamesMatching(liveMatch.homeTeamName, it.opponent?.name ?: "") }?.opponent
            val awayOpponent = matchedGame.opponents.find { areTeamNamesMatching(liveMatch.awayTeamName, it.opponent?.name ?: "") }?.opponent
            val currentHomeScore = matchedGame.results.find { it.team_id == homeOpponent?.id }?.score
            val currentAwayScore = matchedGame.results.find { it.team_id == awayOpponent?.id }?.score

            if (currentHomeScore != liveMatch.homeScore || currentAwayScore != liveMatch.awayScore) {
                val updatedMatch = MatchJpaEntity(
                    id = liveMatch.id,
                    sportId = liveMatch.sportId,
                    leagueId = liveMatch.leagueId,
                    seasonId = liveMatch.seasonId,
                    homeTeamName = liveMatch.homeTeamName,
                    awayTeamName = liveMatch.awayTeamName,
                    homeTeamLogoUrl = liveMatch.homeTeamLogoUrl,
                    awayTeamLogoUrl = liveMatch.awayTeamLogoUrl,
                    kickoffTime = liveMatch.kickoffTime,
                    status = MatchStatus.LIVE,
                    homeScore = currentHomeScore ?: liveMatch.homeScore,
                    awayScore = currentAwayScore ?: liveMatch.awayScore,
                    phase = liveMatch.phase,
                    periodScoresJson = liveMatch.periodScoresJson,
                    numberOfGames = liveMatch.numberOfGames,
                    streamUrl = liveMatch.streamUrl,
                    updatedAt = Instant.now()
                )
                matchRepository.save(updatedMatch)
            }
        }
    }

    private fun purgeSyntheticBaselineMatches(leagueId: UUID) {
        val existing = matchRepository.findByLeagueId(leagueId)
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val finishedMatches = existing.filter { it.status == MatchStatus.FINISHED }
        val toDelete = mutableListOf<MatchJpaEntity>()
        val toUpdate = mutableListOf<MatchJpaEntity>()

        for (match in existing) {
            val isSynthetic = match.homeTeamLogoUrl?.contains("dicebear") == true || match.awayTeamLogoUrl?.contains("dicebear") == true
            val isOutOfSeason = if (activeSeason != null) {
                match.kickoffTime.isBefore(activeSeason.startDate.minus(14, ChronoUnit.DAYS)) ||
                match.kickoffTime.isAfter(activeSeason.endDate.plus(14, ChronoUnit.DAYS))
            } else false

            val isOrphanedLiveDuplicate = match.status == MatchStatus.LIVE && finishedMatches.any { fin ->
                fin.id != match.id &&
                (areTeamNamesMatching(match.homeTeamName, fin.homeTeamName) && areTeamNamesMatching(match.awayTeamName, fin.awayTeamName) ||
                 areTeamNamesMatching(match.homeTeamName, fin.awayTeamName) && areTeamNamesMatching(match.awayTeamName, fin.homeTeamName)) &&
                java.time.Duration.between(match.kickoffTime, fin.kickoffTime).abs().toHours() < 168
            }

            if (isSynthetic || isOutOfSeason || isOrphanedLiveDuplicate) {
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

    private fun parseIsoInstant(dateStr: String?): Instant? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            Instant.parse(dateStr)
        } catch (e: Exception) {
            null
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
