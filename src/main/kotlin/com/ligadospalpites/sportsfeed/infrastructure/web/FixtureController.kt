package com.ligadospalpites.sportsfeed.infrastructure.web

import com.ligadospalpites.shared.identity.UserResolver
import com.ligadospalpites.sportsfeed.application.usecases.GetBracketsUseCase
import com.ligadospalpites.sportsfeed.application.usecases.GetMatchTimelineUseCase
import com.ligadospalpites.sportsfeed.application.usecases.GetStandingsUseCase
import com.ligadospalpites.sportsfeed.domain.exceptions.LeagueInactiveException
import com.ligadospalpites.sportsfeed.domain.exceptions.SportLockedException
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.domain.models.formatMatchPhase
import com.ligadospalpites.sportsfeed.infrastructure.persistence.*
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sports")
class FixtureController(
    private val sportRepository: SpringDataSportRepository,
    private val leagueRepository: SpringDataLeagueRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val seasonRepository: SpringDataSeasonRepository,
    private val entitlementRepository: SpringDataUserEntitlementRepository,
    private val userResolver: UserResolver,
    private val getStandingsUseCase: GetStandingsUseCase,
    private val getBracketsUseCase: GetBracketsUseCase,
    @Autowired(required = false) private val getMatchTimelineUseCase: GetMatchTimelineUseCase? = null
) {

    // 1. Get leagues grouped by sport
    @GetMapping("/leagues")
    fun getLeaguesGroupedBySport(): ResponseEntity<List<SportWithLeaguesResponse>> {
        val activeLeagues = leagueRepository.findByIsActiveTrue()
        val sports = sportRepository.findAll()
        val activeLeagueIds = activeLeagues.map { it.id }.toSet()

        // Optimized query: load only matches for active leagues instead of entire table
        val activeMatches = if (activeLeagueIds.isNotEmpty()) {
            matchRepository.findByLeagueIdIn(activeLeagueIds).filter {
                it.status == MatchStatus.SCHEDULED || it.status == MatchStatus.LIVE
            }
        } else {
            emptyList()
        }
        val leaguesWithActiveMatches = activeMatches.map { it.leagueId }.toSet()

        val grouped = sports.map { sport ->
            val leaguesForSport = activeLeagues
                .filter { it.sportId == sport.id }
                .map { league ->
                    val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(league.id)
                    val currentSeasonRes = activeSeason?.let {
                        SeasonResponse(
                            seasonId = it.id,
                            name = it.name,
                            isActive = it.isActive,
                            displayLabel = "Temporada ${it.name}"
                        )
                    }
                    val isOffSeason = league.id !in leaguesWithActiveMatches

                    LeagueResponse(
                        leagueId = league.id,
                        name = league.name,
                        isActive = league.isActive,
                        logoUrl = league.logoUrl,
                        format = league.format,
                        currentSeason = currentSeasonRes,
                        isOffSeason = isOffSeason
                    )
                }

            SportWithLeaguesResponse(
                sportId = sport.id,
                sportName = sport.name,
                leagues = leaguesForSport
            )
        }.filter { it.leagues.isNotEmpty() }

        return ResponseEntity.ok(grouped)
    }

    // 2. List fixtures for a sport/league (with active check and premium sport lock)
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @GetMapping("/fixtures")
    fun getFixtures(
        @RequestParam(required = false) sportId: UUID?,
        @RequestParam(required = false) leagueId: UUID?,
        @RequestParam(required = false) seasonId: UUID?,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<List<MatchResponse>> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        // Validation A: If leagueId is specified, check if it's active
        if (leagueId != null) {
            val league = leagueRepository.findById(leagueId).orElse(null)
            if (league == null || !league.isActive) {
                throw LeagueInactiveException("Esta liga está inativa.")
            }
        }

        // Validation B: Check MULTI_SPORT entitlement lock for non-default sports
        val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
        if (sportId != null && sportId != footballId) {
            val now = Instant.now()
            val entitlements = entitlementRepository.findByUserId(userUUID)
            val hasMultiSport = entitlements.any {
                val expiresAt = it.expiresAt
                val isNotExpired = expiresAt == null || expiresAt.isAfter(now)
                isNotExpired && (
                    it.entitlementType == com.ligadospalpites.users.domain.models.EntitlementType.PREMIUM ||
                    (it.entitlementType == com.ligadospalpites.users.domain.models.EntitlementType.SPORT_PASS && it.sportId == sportId)
                )
            }
            if (!hasMultiSport) {
                throw SportLockedException("Assine o plano MULTI_SPORT para acessar este esporte.")
            }
        }

        // Resolve seasonId dynamically for backwards compatibility if leagueId is provided but seasonId is not
        val targetSeasonId = when {
            seasonId != null -> seasonId
            leagueId != null -> seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)?.id
            else -> null
        }

        val allMatches = when {
            targetSeasonId != null -> {
                matchRepository.findBySeasonId(targetSeasonId)
            }
            leagueId != null -> {
                val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
                if (activeSeason != null) matchRepository.findBySeasonId(activeSeason.id)
                else matchRepository.findByLeagueId(leagueId)
            }
            else -> {
                val activeLeagues = leagueRepository.findByIsActiveTrue()
                val activeLeagueIds = activeLeagues.map { it.id }.toSet()
                val activeSeasonIds = activeLeagues.mapNotNull {
                    seasonRepository.findByLeagueIdAndIsActiveTrue(it.id)?.id
                }.toSet()

                // Optimized query: load only matches for active leagues instead of entire table
                val rawMatches = if (activeLeagueIds.isNotEmpty()) {
                    matchRepository.findByLeagueIdIn(activeLeagueIds)
                } else {
                    emptyList()
                }

                if (activeSeasonIds.isNotEmpty()) {
                    rawMatches.filter { activeSeasonIds.contains(it.seasonId) }
                } else {
                    rawMatches
                }
            }
        }

        val esportsId = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")
        val nowInstant = Instant.now()

        val filtered = allMatches
            .filter { match -> (sportId == null || match.sportId == sportId) }
            .filter { match ->
                if (match.sportId == esportsId) {
                    if (match.status == MatchStatus.FINISHED && (match.homeScore ?: 0) == 0 && (match.awayScore ?: 0) == 0) {
                        return@filter false
                    }
                    if (match.status == MatchStatus.SCHEDULED && match.kickoffTime.isBefore(nowInstant.minus(24, ChronoUnit.HOURS))) {
                        return@filter false
                    }
                    if (match.phase?.contains("2024") == true || match.phase?.contains("2025") == true) {
                        return@filter false
                    }
                }
                true
            }
            .sortedBy { it.kickoffTime }
            .map { MatchResponse.fromEntity(it) }

        return ResponseEntity.ok(filtered)
    }

    // 2.1 Get Match Timeline / Commentary
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @GetMapping("/matches/{matchId}/timeline")
    fun getMatchTimeline(
        @PathVariable matchId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Any> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)
        if (getMatchTimelineUseCase == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("error" to "SERVICE_UNAVAILABLE", "message" to "Serviço de minuto a minuto temporariamente indisponível."))
        }

        return try {
            val events = getMatchTimelineUseCase.execute(matchId, userUUID)
            val responseList = events.map {
                MatchTimelineEventResponse(
                    id = it.id,
                    matchId = it.matchId,
                    minute = it.minute,
                    extraMinute = it.extraMinute,
                    displayMinute = it.displayMinute,
                    period = it.period,
                    eventType = it.eventType.name,
                    teamName = it.teamName,
                    playerName = it.playerName,
                    playerAssistName = it.playerAssistName,
                    description = it.description,
                    isImportant = it.isImportant,
                    createdAt = it.createdAt
                )
            }
            ResponseEntity.ok(responseList)
        } catch (e: org.springframework.security.access.AccessDeniedException) {
            ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "PREMIUM_REQUIRED", "message" to (e.message ?: "Assinatura PRO necessária para acessar o minuto a minuto.")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "MATCH_NOT_FOUND", "message" to (e.message ?: "Partida não encontrada.")))
        } catch (e: Exception) {
            ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "INTERNAL_ERROR", "message" to "Erro ao carregar o minuto a minuto."))
        }
    }

    // 3. Standings
    @GetMapping("/standings")
    fun getStandings(@RequestParam leagueId: UUID): ResponseEntity<List<StandingRow>> {
        val rows = getStandingsUseCase.execute(leagueId)
        return ResponseEntity.ok(rows)
    }

    // 4. Bracket Match Tree for Knockout
    @GetMapping("/brackets")
    fun getBrackets(@RequestParam leagueId: UUID): ResponseEntity<BracketResponse> {
        val bracket = getBracketsUseCase.execute(leagueId)
        return ResponseEntity.ok(bracket)
    }
}

// DTOs
data class SeasonResponse(
    val seasonId: UUID,
    val name: String,
    val isActive: Boolean,
    val displayLabel: String
)

data class SportWithLeaguesResponse(val sportId: UUID, val sportName: String, val leagues: List<LeagueResponse>)

data class LeagueResponse(
    val leagueId: UUID,
    val name: String,
    val isActive: Boolean,
    val logoUrl: String? = null,
    val format: String = "POINTS",
    val currentSeason: SeasonResponse? = null,
    val isOffSeason: Boolean = false
)

data class MatchResponse(
    val matchId: UUID,
    val sportId: UUID,
    val leagueId: UUID,
    val homeTeam: String,
    val awayTeam: String,
    val kickoffTime: String,
    val status: String,
    val scoreHome: Int?,
    val scoreAway: Int?,
    val homeScore: Int? = scoreHome,
    val awayScore: Int? = scoreAway,
    val phase: String? = null,
    val homeTeamLogoUrl: String? = null,
    val awayTeamLogoUrl: String? = null,
    val periodScoresJson: String? = null,
    val numberOfGames: Int? = null,
    val streamUrl: String? = null
) {
    companion object {
        fun fromEntity(entity: MatchJpaEntity): MatchResponse {
            val isScheduled = entity.status == MatchStatus.SCHEDULED
            val sHome = if (isScheduled) null else entity.homeScore
            val sAway = if (isScheduled) null else entity.awayScore
            return MatchResponse(
                matchId = entity.id,
                sportId = entity.sportId,
                leagueId = entity.leagueId,
                homeTeam = entity.homeTeamName,
                awayTeam = entity.awayTeamName,
                kickoffTime = entity.kickoffTime.toString(),
                status = entity.status.name,
                scoreHome = sHome,
                scoreAway = sAway,
                homeScore = sHome,
                awayScore = sAway,
                phase = formatMatchPhase(entity.phase),
                homeTeamLogoUrl = entity.homeTeamLogoUrl,
                awayTeamLogoUrl = entity.awayTeamLogoUrl,
                periodScoresJson = entity.periodScoresJson,
                numberOfGames = entity.numberOfGames,
                streamUrl = entity.streamUrl
            )
        }
    }
}

data class StandingRow(
    val position: Int,
    val teamId: UUID,
    val teamName: String,
    val points: Int? = null,
    val played: Int? = null,
    val won: Int? = null,
    val drawn: Int? = null,
    val lost: Int? = null,
    val goalsFor: Int? = null,
    val goalsAgainst: Int? = null,
    val goalDifference: Int? = null,
    val groupName: String? = null,
    val winRate: Double? = null,
    val gamesBehind: String? = null,
    val streak: String? = null,
    val seriesWon: Int? = null,
    val seriesLost: Int? = null,
    val mapsWon: Int? = null,
    val mapsLost: Int? = null,
    val podiums: Int? = null,
    val fastestLaps: Int? = null,
    val constructorName: String? = null,
    val conferenceRecord: String? = null,
    val divisionRecord: String? = null,
    val tournamentsPlayed: Int? = null,
    val titlesWon: Int? = null,
    val teamLogoUrl: String? = null
)

data class BracketResponse(
    val leagueId: UUID,
    val phases: Map<String, List<MatchResponse>>
)

data class MatchTimelineEventResponse(
    val id: UUID,
    val matchId: UUID,
    val minute: Int,
    val extraMinute: Int?,
    val displayMinute: String,
    val period: String,
    val eventType: String,
    val teamName: String?,
    val playerName: String?,
    val playerAssistName: String?,
    val description: String,
    val isImportant: Boolean,
    val createdAt: Instant
)
