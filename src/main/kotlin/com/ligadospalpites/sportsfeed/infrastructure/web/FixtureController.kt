package com.ligadospalpites.sportsfeed.infrastructure.web

import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataSportRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataSeasonRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.domain.models.formatMatchPhase
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import com.ligadospalpites.shared.identity.UserResolver
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sports")
class FixtureController(
    private val sportRepository: SpringDataSportRepository,
    private val leagueRepository: SpringDataLeagueRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val seasonRepository: SpringDataSeasonRepository,
    private val entitlementRepository: SpringDataUserEntitlementRepository,
    private val userResolver: UserResolver
) {

    // 1. Get leagues grouped by sport
    @GetMapping("/leagues")
    fun getLeaguesGroupedBySport(): ResponseEntity<List<SportWithLeaguesResponse>> {
        val activeLeagues = leagueRepository.findByIsActiveTrue()
        val sports = sportRepository.findAll()

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
                    LeagueResponse(
                        leagueId = league.id,
                        name = league.name,
                        isActive = league.isActive,
                        logoUrl = league.logoUrl,
                        format = league.format,
                        currentSeason = currentSeasonRes
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
    ): ResponseEntity<Any> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        // Validation A: If leagueId is specified, check if it's active
        if (leagueId != null) {
            val league = leagueRepository.findById(leagueId).orElse(null)
            if (league == null || !league.isActive) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "LEAGUE_INACTIVE", "message" to "Esta liga está inativa."))
            }
        }

        // Validation B: Check MULTI_SPORT entitlement lock for non-default sports
        val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
        if (sportId != null && sportId != footballId) {
            val now = java.time.Instant.now()
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
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(mapOf("error" to "SPORT_LOCKED", "message" to "Assine o plano MULTI_SPORT para acessar este esporte."))
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
                matchRepository.findByLeagueId(leagueId)
            }
            else -> {
                val activeLeagueIds = leagueRepository.findByIsActiveTrue().map { it.id }.toSet()
                matchRepository.findAll().filter { activeLeagueIds.contains(it.leagueId) }
            }
        }

        val filtered = allMatches.filter { match ->
            (sportId == null || match.sportId == sportId)
        }.map { MatchResponse.fromEntity(it) }

        return ResponseEntity.ok(filtered)
    }

    // 3. Standings (Tabela) for Group Stage / Points Corridos
    @GetMapping("/standings")
    fun getStandings(@RequestParam leagueId: UUID): ResponseEntity<List<StandingRow>> {
        val league = leagueRepository.findById(leagueId).orElse(null)
        val format = league?.format ?: "POINTS"
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val matches = if (activeSeason != null) {
            matchRepository.findBySeasonId(activeSeason.id)
        } else {
            matchRepository.findByLeagueId(leagueId)
        }

        if (format == "GROUPS_AND_KNOCKOUT") {
            val groupMatches = matches.filter { it.phase?.startsWith("Grupo") == true || it.phase == "Fase de Grupos" }
            if (groupMatches.isNotEmpty()) {
                val groupNames = groupMatches.mapNotNull { it.phase }.filter { it.startsWith("Grupo") }.distinct().ifEmpty { listOf("Grupo A", "Grupo B") }
                val rows = mutableListOf<StandingRow>()
                groupNames.forEach { grp ->
                    val grpMatches = groupMatches.filter { it.phase == grp }
                    val teams = (grpMatches.map { it.homeTeamName } + grpMatches.map { it.awayTeamName }).distinct()
                    val finishedGrpMatches = grpMatches.filter { it.status == MatchStatus.FINISHED }

                    val computedGrpRows = teams.map { teamName ->
                        var played = 0
                        var won = 0
                        var drawn = 0
                        var lost = 0
                        var goalsFor = 0
                        var goalsAgainst = 0

                        finishedGrpMatches.forEach { m ->
                            if (m.homeTeamName == teamName) {
                                played++
                                val hScore = m.homeScore ?: 0
                                val aScore = m.awayScore ?: 0
                                goalsFor += hScore
                                goalsAgainst += aScore
                                when {
                                    hScore > aScore -> won++
                                    hScore == aScore -> drawn++
                                    else -> lost++
                                }
                            } else if (m.awayTeamName == teamName) {
                                played++
                                val aScore = m.awayScore ?: 0
                                val hScore = m.homeScore ?: 0
                                goalsFor += aScore
                                goalsAgainst += hScore
                                when {
                                    aScore > hScore -> won++
                                    aScore == hScore -> drawn++
                                    else -> lost++
                                }
                            }
                        }
                        val points = won * 3 + drawn
                        val goalDifference = goalsFor - goalsAgainst
                        StandingRow(
                            position = 0,
                            teamId = UUID.nameUUIDFromBytes(teamName.toByteArray()),
                            teamName = teamName,
                            points = points,
                            played = played,
                            won = won,
                            drawn = drawn,
                            lost = lost,
                            goalsFor = goalsFor,
                            goalsAgainst = goalsAgainst,
                            goalDifference = goalDifference,
                            groupName = grp
                        )
                    }

                    val sortedGrpRows = computedGrpRows.sortedWith(
                        compareByDescending<StandingRow> { it.points }
                            .thenByDescending { it.goalDifference }
                            .thenByDescending { it.goalsFor }
                            .thenBy { it.teamName }
                    ).mapIndexed { idx, r -> r.copy(position = idx + 1) }

                    rows.addAll(sortedGrpRows)
                }
                if (rows.isNotEmpty()) return ResponseEntity.ok(rows)
            }

            // Fallback group stage rows for Libertadores / Champions League
            val defaultGroupRows = listOf(
                StandingRow(1, UUID.nameUUIDFromBytes("Palmeiras".toByteArray()), "Palmeiras", 9, 3, 3, 0, 0, 7, 1, 6, "Grupo A"),
                StandingRow(2, UUID.nameUUIDFromBytes("River Plate".toByteArray()), "River Plate", 6, 3, 2, 0, 1, 5, 3, 2, "Grupo A"),
                StandingRow(1, UUID.nameUUIDFromBytes("Flamengo".toByteArray()), "Flamengo", 9, 3, 3, 0, 0, 8, 2, 6, "Grupo B"),
                StandingRow(2, UUID.nameUUIDFromBytes("Boca Juniors".toByteArray()), "Boca Juniors", 4, 3, 1, 1, 1, 4, 4, 0, "Grupo B")
            )
            return ResponseEntity.ok(defaultGroupRows)
        }

        // Standard Points Corridos Standings
        val teams = (matches.map { it.homeTeamName } + matches.map { it.awayTeamName }).distinct()
        if (teams.isNotEmpty()) {
            val finishedMatches = matches.filter { it.status == MatchStatus.FINISHED }
            val computedRows = teams.map { teamName ->
                var played = 0
                var won = 0
                var drawn = 0
                var lost = 0
                var goalsFor = 0
                var goalsAgainst = 0

                finishedMatches.forEach { m ->
                    if (m.homeTeamName == teamName) {
                        played++
                        val hScore = m.homeScore ?: 0
                        val aScore = m.awayScore ?: 0
                        goalsFor += hScore
                        goalsAgainst += aScore
                        when {
                            hScore > aScore -> won++
                            hScore == aScore -> drawn++
                            else -> lost++
                        }
                    } else if (m.awayTeamName == teamName) {
                        played++
                        val aScore = m.awayScore ?: 0
                        val hScore = m.homeScore ?: 0
                        goalsFor += aScore
                        goalsAgainst += hScore
                        when {
                            aScore > hScore -> won++
                            aScore == hScore -> drawn++
                            else -> lost++
                        }
                    }
                }
                val points = won * 3 + drawn
                val goalDifference = goalsFor - goalsAgainst
                StandingRow(
                    position = 0,
                    teamId = UUID.nameUUIDFromBytes(teamName.toByteArray()),
                    teamName = teamName,
                    points = points,
                    played = played,
                    won = won,
                    drawn = drawn,
                    lost = lost,
                    goalsFor = goalsFor,
                    goalsAgainst = goalsAgainst,
                    goalDifference = goalDifference
                )
            }

            val sortedRows = computedRows.sortedWith(
                compareByDescending<StandingRow> { it.points }
                    .thenByDescending { it.goalDifference }
                    .thenByDescending { it.goalsFor }
                    .thenBy { it.teamName }
            ).mapIndexed { idx, r -> r.copy(position = idx + 1) }

            return ResponseEntity.ok(sortedRows)
        }

        val rows = listOf(
            StandingRow(1, UUID.randomUUID(), "Brasil", 9, 3, 3, 0, 0, 8, 1, 7),
            StandingRow(2, UUID.randomUUID(), "França", 6, 3, 2, 0, 1, 5, 3, 2)
        )
        return ResponseEntity.ok(rows)
    }

    // 4. Bracket Match Tree for Knockout
    @GetMapping("/brackets")
    fun getBrackets(@RequestParam leagueId: UUID): ResponseEntity<BracketResponse> {
        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val matches = if (activeSeason != null) {
            matchRepository.findBySeasonId(activeSeason.id)
        } else {
            matchRepository.findByLeagueId(leagueId)
        }
        // Group matches into phases for the Flutter BracketBloc
        val stages = mapOf(
            "DECIMOSEXTO" to matches.filter { it.phase == "roundOf32" || it.phase == "Dezesseis-avos de Final" || it.homeTeamName.contains("Dezesseis") || it.awayTeamName.contains("Dezesseis") }.map { MatchResponse.fromEntity(it) },
            "ROUND_OF_32" to matches.filter { it.phase == "roundOf32" || it.phase == "Dezesseis-avos de Final" || it.homeTeamName.contains("Dezesseis") || it.awayTeamName.contains("Dezesseis") }.map { MatchResponse.fromEntity(it) },
            "OITAVAS" to matches.filter { it.phase == "roundOf16" || it.phase == "Oitavas de Final" || it.homeTeamName.contains("Oitavas") || it.awayTeamName.contains("Oitavas") }.map { MatchResponse.fromEntity(it) },
            "QUARTAS" to matches.filter { it.phase == "quarterFinals" || it.phase == "Quartas de Final" || it.homeTeamName.contains("Quartas") || it.awayTeamName.contains("Quartas") }.map { MatchResponse.fromEntity(it) },
            "SEMI" to matches.filter { it.phase == "semiFinals" || it.phase == "Semifinal" || it.homeTeamName.contains("Semi") || it.awayTeamName.contains("Semi") }.map { MatchResponse.fromEntity(it) },
            "FINAL" to matches.filter { it.phase == "finalMatch" || it.phase == "thirdPlace" || it.phase == "Grande Final" || it.homeTeamName.contains("Final") || it.awayTeamName.contains("Final") }.map { MatchResponse.fromEntity(it) }
        )

        return ResponseEntity.ok(BracketResponse(leagueId, stages))
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
    val currentSeason: SeasonResponse? = null
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
    val phase: String? = null,
    val homeTeamLogoUrl: String? = null,
    val awayTeamLogoUrl: String? = null,
    val periodScoresJson: String? = null,
    val numberOfGames: Int? = null,
    val streamUrl: String? = null
) {
    companion object {
        fun fromEntity(entity: MatchJpaEntity) = MatchResponse(
            matchId = entity.id,
            sportId = entity.sportId,
            leagueId = entity.leagueId,
            homeTeam = entity.homeTeamName,
            awayTeam = entity.awayTeamName,
            kickoffTime = entity.kickoffTime.toString(),
            status = entity.status.name,
            scoreHome = entity.homeScore,
            scoreAway = entity.awayScore,
            phase = formatMatchPhase(entity.phase),
            homeTeamLogoUrl = entity.homeTeamLogoUrl,
            awayTeamLogoUrl = entity.awayTeamLogoUrl,
            periodScoresJson = entity.periodScoresJson,
            numberOfGames = entity.numberOfGames,
            streamUrl = entity.streamUrl
        )
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
