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
        if (format == "KNOCKOUT") {
            return ResponseEntity.ok(emptyList())
        }

        val activeSeason = seasonRepository.findByLeagueIdAndIsActiveTrue(leagueId)
        val matches = if (activeSeason != null) {
            matchRepository.findBySeasonId(activeSeason.id)
        } else {
            matchRepository.findByLeagueId(leagueId)
        }

        if (format == "GROUPS_AND_KNOCKOUT") {
            val groupMatches = matches.filter {
                it.phase?.startsWith("Grupo") == true ||
                it.phase == "Fase de Grupos" ||
                it.phase == "Fase de Liga" ||
                it.phase?.contains("League") == true
            }
            if (groupMatches.isNotEmpty()) {
                val groupNames = groupMatches.mapNotNull { it.phase }
                    .filter { it.startsWith("Grupo") || it == "Fase de Liga" }
                    .distinct()
                    .sorted()
                    .ifEmpty {
                        if (groupMatches.any { it.phase == "Fase de Liga" || it.phase?.contains("League") == true }) listOf("Fase de Liga")
                        else if (groupMatches.any { it.phase == "Fase de Grupos" }) listOf("Fase de Grupos")
                        else listOf("Grupo A", "Grupo B")
                    }
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

        val sport = league?.let { sportRepository.findById(it.sportId).orElse(null) }
        val sportName = (sport?.name ?: league?.name ?: "").lowercase()

        // 1. Basquete (NBA / NBB / EuroLeague)
        if (sportName.contains("basquete") || sportName.contains("nba") || sportName.contains("nbb") || sportName.contains("euroleague")) {
            val teams = (matches.map { it.homeTeamName } + matches.map { it.awayTeamName }).distinct()
            if (teams.isNotEmpty()) {
                val finishedMatches = matches.filter { it.status == MatchStatus.FINISHED }
                val computedRows = teams.map { teamName ->
                    var played = 0
                    var won = 0
                    var lost = 0
                    finishedMatches.forEach { m ->
                        if (m.homeTeamName == teamName || m.awayTeamName == teamName) {
                            played++
                            val isHome = m.homeTeamName == teamName
                            val myScore = if (isHome) m.homeScore ?: 0 else m.awayScore ?: 0
                            val oppScore = if (isHome) m.awayScore ?: 0 else m.homeScore ?: 0
                            if (myScore > oppScore) won++ else if (myScore < oppScore) lost++
                        }
                    }
                    val winRate = if (played > 0) Math.round((won.toDouble() / played) * 1000.0) / 1000.0 else 0.0
                    StandingRow(
                        position = 0,
                        teamId = UUID.nameUUIDFromBytes(teamName.toByteArray()),
                        teamName = teamName,
                        played = played,
                        won = won,
                        lost = lost,
                        winRate = winRate,
                        gamesBehind = "0.0",
                        streak = if (won > 0) "W$won" else "L$lost"
                    )
                }.sortedWith(compareByDescending<StandingRow> { it.winRate ?: 0.0 }.thenByDescending { it.won ?: 0 })
                    .mapIndexed { idx, r -> r.copy(position = idx + 1) }

                return ResponseEntity.ok(computedRows)
            }

            // Fallback para Basquete (ex: NBA / NBB)
            val basketballFallback = listOf(
                StandingRow(1, UUID.randomUUID(), "Boston Celtics", played = 82, won = 64, lost = 18, winRate = 0.780, gamesBehind = "-", streak = "W5"),
                StandingRow(2, UUID.randomUUID(), "New York Knicks", played = 82, won = 50, lost = 32, winRate = 0.610, gamesBehind = "14.0", streak = "W1"),
                StandingRow(3, UUID.randomUUID(), "Milwaukee Bucks", played = 82, won = 49, lost = 33, winRate = 0.598, gamesBehind = "15.0", streak = "L2"),
                StandingRow(4, UUID.randomUUID(), "Cleveland Cavaliers", played = 82, won = 48, lost = 34, winRate = 0.585, gamesBehind = "16.0", streak = "W2")
            )
            return ResponseEntity.ok(basketballFallback)
        }

        // 2. eSports (LoL / CS / Valorant)
        if (sportName.contains("esports") || sportName.contains("league of legends") || sportName.contains("counter-strike") || sportName.contains("valorant") || sportName.contains("cblol")) {
            val esportsFallback = listOf(
                StandingRow(1, UUID.randomUUID(), "LOUD", seriesWon = 7, seriesLost = 1, mapsWon = 15, mapsLost = 4, streak = "W6"),
                StandingRow(2, UUID.randomUUID(), "PAIN Gaming", seriesWon = 6, seriesLost = 2, mapsWon = 13, mapsLost = 6, streak = "W2"),
                StandingRow(3, UUID.randomUUID(), "FURIA Esports", seriesWon = 4, seriesLost = 4, mapsWon = 10, mapsLost = 9, streak = "L1"),
                StandingRow(4, UUID.randomUUID(), "RED Canids", seriesWon = 3, seriesLost = 5, mapsWon = 7, mapsLost = 11, streak = "L2")
            )
            return ResponseEntity.ok(esportsFallback)
        }

        // 3. Automobilismo (Formula 1 / F2 / FE / Stock Car)
        if (sportName.contains("automobilismo") || sportName.contains("formula 1") || sportName.contains("f1") || sportName.contains("stock car")) {
            val motorsportFallback = listOf(
                StandingRow(1, UUID.randomUUID(), "Max Verstappen", points = 437, won = 19, podiums = 21, fastestLaps = 9, constructorName = "Red Bull Racing"),
                StandingRow(2, UUID.randomUUID(), "Lando Norris", points = 374, won = 3, podiums = 12, fastestLaps = 4, constructorName = "McLaren"),
                StandingRow(3, UUID.randomUUID(), "Charles Leclerc", points = 356, won = 3, podiums = 11, fastestLaps = 3, constructorName = "Ferrari"),
                StandingRow(4, UUID.randomUUID(), "Oscar Piastri", points = 292, won = 2, podiums = 7, fastestLaps = 1, constructorName = "McLaren")
            )
            return ResponseEntity.ok(motorsportFallback)
        }

        // 4. Futebol Americano (NFL / College)
        if (sportName.contains("futebol americano") || sportName.contains("nfl") || sportName.contains("ncaa")) {
            val nflFallback = listOf(
                StandingRow(1, UUID.randomUUID(), "Kansas City Chiefs", won = 14, lost = 3, drawn = 0, winRate = 0.824, conferenceRecord = "10-2", divisionRecord = "5-1"),
                StandingRow(2, UUID.randomUUID(), "Buffalo Bills", won = 11, lost = 6, drawn = 0, winRate = 0.647, conferenceRecord = "7-5", divisionRecord = "4-2"),
                StandingRow(3, UUID.randomUUID(), "Baltimore Ravens", won = 13, lost = 4, drawn = 0, winRate = 0.765, conferenceRecord = "8-4", divisionRecord = "4-2"),
                StandingRow(4, UUID.randomUUID(), "San Francisco 49ers", won = 12, lost = 5, drawn = 0, winRate = 0.706, conferenceRecord = "10-2", divisionRecord = "5-1")
            )
            return ResponseEntity.ok(nflFallback)
        }

        // 5. Tênis (ATP / WTA / Grand Slams)
        if (sportName.contains("tênis") || sportName.contains("tenis") || sportName.contains("atp") || sportName.contains("wta")) {
            val tennisFallback = listOf(
                StandingRow(1, UUID.randomUUID(), "Jannik Sinner", points = 11830, tournamentsPlayed = 17, titlesWon = 8),
                StandingRow(2, UUID.randomUUID(), "Alexander Zverev", points = 7915, tournamentsPlayed = 21, titlesWon = 3),
                StandingRow(3, UUID.randomUUID(), "Carlos Alcaraz", points = 7010, tournamentsPlayed = 16, titlesWon = 4),
                StandingRow(4, UUID.randomUUID(), "Novak Djokovic", points = 3910, tournamentsPlayed = 12, titlesWon = 1)
            )
            return ResponseEntity.ok(tennisFallback)
        }

        // Default: Futebol (Points Corridos / Grupos)
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
                val winRate = if (played > 0) Math.round((won.toDouble() / played) * 100.0) / 100.0 else 0.0
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
                    winRate = winRate
                )
            }

            val sortedRows = computedRows.sortedWith(
                compareByDescending<StandingRow> { it.points ?: 0 }
                    .thenByDescending { it.goalDifference ?: 0 }
                    .thenByDescending { it.goalsFor ?: 0 }
                    .thenBy { it.teamName }
            ).mapIndexed { idx, r -> r.copy(position = idx + 1) }

            return ResponseEntity.ok(sortedRows)
        }

        val defaultFootballRows = listOf(
            StandingRow(1, UUID.randomUUID(), "Brasil", points = 9, played = 3, won = 3, drawn = 0, lost = 0, goalsFor = 8, goalsAgainst = 1, goalDifference = 7, winRate = 1.0),
            StandingRow(2, UUID.randomUUID(), "França", points = 6, played = 3, won = 2, drawn = 0, lost = 1, goalsFor = 5, goalsAgainst = 3, goalDifference = 2, winRate = 0.67)
        )
        return ResponseEntity.ok(defaultFootballRows)
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
            "DECIMOSEXTO" to matches.filter { it.phase == "roundOf32" || it.phase == "Dezesseis-avos de Final" || it.phase == "Terceira Fase" || it.homeTeamName.contains("Dezesseis") || it.awayTeamName.contains("Dezesseis") }.map { MatchResponse.fromEntity(it) },
            "ROUND_OF_32" to matches.filter { it.phase == "roundOf32" || it.phase == "Dezesseis-avos de Final" || it.phase == "Terceira Fase" || it.homeTeamName.contains("Dezesseis") || it.awayTeamName.contains("Dezesseis") }.map { MatchResponse.fromEntity(it) },
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
