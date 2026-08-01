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
import java.util.UUID

data class EsportsLeagueMetadata(
    val id: UUID,
    val defaultName: String,
    val pandaScoreSlug: String? = null,
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
            defaultName = "CBLOL",
            pandaScoreSlug = "league-of-legends-cblol",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/league-of-legends.png"
        ),
        UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "VCT Americas",
            pandaScoreSlug = "vct-americas",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/valorant.png"
        ),
        UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "CS2 Major",
            pandaScoreSlug = "cs-go-major",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/csgo.png"
        ),
        UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14") to EsportsLeagueMetadata(
            id = UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14"),
            defaultName = "Worlds",
            pandaScoreSlug = "league-of-legends-world-championship",
            logoUrl = "https://cdn.jsdelivr.net/gh/walkxcode/dashboard-icons/png/league-of-legends.png"
        )
    )

    override fun supports(sportId: UUID, leagueId: UUID): Boolean {
        return sportId == esportsId && leaguesMetadata.containsKey(leagueId)
    }

    override fun syncMatches(sportId: UUID, leagueId: UUID) {
        val metadata = leaguesMetadata[leagueId] ?: return
        logger.info("Starting eSports games sync for league: ${metadata.defaultName}")
        ensureLeagueLogo(leagueId, metadata.logoUrl)

        val incomingMatches = try {
            self.fetchFromPandaScore(sportId, leagueId)
        } catch (e: Exception) {
            logger.error("Failed to fetch eSports matches from PandaScore API: ${e.message}")
            emptyList()
        }

        if (incomingMatches.isNotEmpty()) {
            performUpsert(leagueId, incomingMatches)
        } else {
            val existing = matchRepository.findByLeagueId(leagueId)
            if (existing.isEmpty()) {
                logger.warn("No games retrieved for eSports league ${metadata.defaultName} and local database is empty. Seeding initial baseline fixtures.")
                val baseline = generateBaselineFixtures(leagueId, metadata)
                performUpsert(leagueId, baseline)
            } else {
                logger.warn("No games retrieved for eSports league ${metadata.defaultName}. Local database unchanged.")
            }
        }
    }

    @CircuitBreaker(name = "pandaScoreApi", fallbackMethod = "fetchMatchesLocalFallback")
    @Retry(name = "pandaScoreApi")
    fun fetchFromPandaScore(sportId: UUID, leagueId: UUID): List<MatchJpaEntity> {
        val metadata = leaguesMetadata[leagueId] ?: throw IllegalArgumentException("Invalid league ID: $leagueId")
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: throw IllegalStateException("No active season found for eSports league: $leagueId")

        val externalGames = pandaScoreClient.fetchMatches(leagueSlug = metadata.pandaScoreSlug)
        return externalGames.mapNotNull { game ->
            if (game.opponents.size < 2) return@mapNotNull null
            val homeOpponent = game.opponents[0].opponent ?: return@mapNotNull null
            val awayOpponent = game.opponents[1].opponent ?: return@mapNotNull null

            val homeScore = game.results.find { it.team_id == homeOpponent.id }?.score
            val awayScore = game.results.find { it.team_id == awayOpponent.id }?.score

            val streamUrl = game.streams_list.firstOrNull { it.main }?.raw_url
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
                kickoffTime = parseIsoInstant(game.begin_at),
                status = mapPandaScoreStatus(game.status),
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

    private fun generateBaselineFixtures(leagueId: UUID, metadata: EsportsLeagueMetadata): List<MatchJpaEntity> {
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val targetSeasonId = activeSeason?.id ?: UUID.randomUUID()
        val now = Instant.now()

        val fixtures = when (metadata.defaultName) {
            "CBLOL" -> listOf(
                Tuple4("LOUD", "paiN Gaming", 2 to 1, 3),
                Tuple4("FURIA", "RED Canids", 0 to 2, 3),
                Tuple4("Kabum!", "Vivo Keyd", null to null, 3),
                Tuple4("Fluxo", "INTZ", null to null, 1)
            )
            "VCT Americas" -> listOf(
                Tuple4("LOUD", "Sentinels", 2 to 0, 3),
                Tuple4("NRG", "100 Thieves", 1 to 2, 3),
                Tuple4("Cloud9", "KRÜ Esports", null to null, 3),
                Tuple4("Leviatán", "FURIA", null to null, 3)
            )
            "CS2 Major" -> listOf(
                Tuple4("FURIA", "Natus Vincere", 1 to 2, 3),
                Tuple4("FaZe Clan", "G2 Esports", 2 to 0, 3),
                Tuple4("MOUZ", "Vitality", null to null, 3),
                Tuple4("Complexity", "Team Liquid", null to null, 1)
            )
            else -> listOf(
                Tuple4("T1", "Gen.G", 3 to 2, 5),
                Tuple4("G2 Esports", "Bilibili Gaming", 1 to 3, 5),
                Tuple4("Hanwha Life", "Top Esports", null to null, 5),
                Tuple4("Fnatic", "Team Liquid", null to null, 3)
            )
        }

        return fixtures.mapIndexed { index, (home, away, scores, boCount) ->
            val offsetDays = (index - 1).toLong()
            val status = when {
                scores.first != null -> MatchStatus.FINISHED
                offsetDays == 0L -> MatchStatus.LIVE
                else -> MatchStatus.SCHEDULED
            }
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = esportsId,
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
                phase = "Fase Principal",
                numberOfGames = boCount,
                streamUrl = "https://www.twitch.tv/gaules",
                updatedAt = now
            )
        }
    }

    override fun syncNews(sportId: UUID) {
        // News sync not required for eSports
    }

    internal fun performUpsert(leagueId: UUID, incoming: List<MatchJpaEntity>) {
        logger.info("Performing intelligent upsert on ${incoming.size} eSports matches for league: $leagueId")
        val existing = matchRepository.findByLeagueId(leagueId)

        val toSave = incoming.map { inc ->
            val matchMatch = existing.find { ext ->
                ext.homeTeamName.lowercase() == inc.homeTeamName.lowercase() &&
                ext.awayTeamName.lowercase() == inc.awayTeamName.lowercase()
            }

            if (matchMatch != null) {
                if (matchMatch.status == MatchStatus.SCHEDULED && inc.status == MatchStatus.LIVE) {
                    logger.info("eSports match started event published: ${matchMatch.id} (${inc.homeTeamName} x ${inc.awayTeamName})")
                    eventPublisher.publishEvent(MatchStartedEvent(matchMatch.id, inc.homeTeamName, inc.awayTeamName, inc.sportId, inc.leagueId))
                }

                if (matchMatch.status != MatchStatus.FINISHED && inc.status == MatchStatus.FINISHED) {
                    logger.info("eSports match finished event published: ${matchMatch.id} (${inc.homeTeamName} x ${inc.awayTeamName})")
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

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
