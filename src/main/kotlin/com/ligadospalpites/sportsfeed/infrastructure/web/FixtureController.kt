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
    private val userResolver: UserResolver,
    @org.springframework.beans.factory.annotation.Autowired(required = false) private val espnBasketballClient: com.ligadospalpites.sportsfeed.infrastructure.client.EspnBasketballClient? = null
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

        val sport = league?.let { sportRepository.findById(it.sportId).orElse(null) }
        val sportName = (sport?.name ?: league?.name ?: "").lowercase()
        val isEsports = (league?.sportId == UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")) ||
                sportName.contains("esports") ||
                sportName.contains("league of legends") ||
                sportName.contains("counter-strike") ||
                sportName.contains("valorant") ||
                sportName.contains("cblol") ||
                sportName.contains("worlds")

        if (format == "GROUPS_AND_KNOCKOUT" && !isEsports) {
            val isLibertadores = leagueId == UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de") ||
                    (league?.name?.contains("Libertadores", ignoreCase = true) == true)

            // Team to group mapping for Copa Libertadores 2026
            val libertadoresTeamGroupMap = mapOf(
                "Flamengo" to "Grupo A", "Estudiantes de La Plata" to "Grupo A", "Independiente Medellín" to "Grupo A", "Cusco FC" to "Grupo A",
                "Nacional" to "Grupo B", "Universitario" to "Grupo B", "Deportes Tolima" to "Grupo B", "Coquimbo Unido" to "Grupo B",
                "Fluminense" to "Grupo C", "Bolívar" to "Grupo C", "Independiente Rivadavia" to "Grupo C", "Deportivo La Guaira" to "Grupo C",
                "Cruzeiro" to "Grupo D", "Boca Juniors" to "Grupo D", "Barcelona SC" to "Grupo D", "Universidad Católica" to "Grupo D",
                "Corinthians" to "Grupo E", "Peñarol" to "Grupo E", "Independiente Santa Fe" to "Grupo E", "Platense" to "Grupo E",
                "Palmeiras" to "Grupo F", "Cerro Porteño" to "Grupo F", "Sporting Cristal" to "Grupo F", "Junior Barranquilla" to "Grupo F",
                "LDU Quito" to "Grupo G", "Lanús" to "Grupo G", "Mirassol" to "Grupo G", "Always Ready" to "Grupo G",
                "Independiente del Valle" to "Grupo H", "Rosario Central" to "Grupo H", "Libertad" to "Grupo H", "UCV FC" to "Grupo H"
            )

            val defaultGroupRows = listOf(
                // Grupo A
                StandingRow(1, UUID.nameUUIDFromBytes("Flamengo".toByteArray()), "Flamengo", 9, 3, 3, 0, 0, 8, 2, 6, "Grupo A"),
                StandingRow(2, UUID.nameUUIDFromBytes("Estudiantes de La Plata".toByteArray()), "Estudiantes de La Plata", 6, 3, 2, 0, 1, 5, 3, 2, "Grupo A"),
                StandingRow(3, UUID.nameUUIDFromBytes("Independiente Medellín".toByteArray()), "Independiente Medellín", 3, 3, 1, 0, 2, 3, 5, -2, "Grupo A"),
                StandingRow(4, UUID.nameUUIDFromBytes("Cusco FC".toByteArray()), "Cusco FC", 0, 3, 0, 0, 3, 1, 7, -6, "Grupo A"),
                // Grupo B
                StandingRow(1, UUID.nameUUIDFromBytes("Nacional".toByteArray()), "Nacional", 7, 3, 2, 1, 0, 6, 2, 4, "Grupo B"),
                StandingRow(2, UUID.nameUUIDFromBytes("Universitario".toByteArray()), "Universitario", 5, 3, 1, 2, 0, 4, 3, 1, "Grupo B"),
                StandingRow(3, UUID.nameUUIDFromBytes("Deportes Tolima".toByteArray()), "Deportes Tolima", 3, 3, 1, 0, 2, 3, 5, -2, "Grupo B"),
                StandingRow(4, UUID.nameUUIDFromBytes("Coquimbo Unido".toByteArray()), "Coquimbo Unido", 1, 3, 0, 1, 2, 2, 5, -3, "Grupo B"),
                // Grupo C
                StandingRow(1, UUID.nameUUIDFromBytes("Fluminense".toByteArray()), "Fluminense", 9, 3, 3, 0, 0, 7, 1, 6, "Grupo C"),
                StandingRow(2, UUID.nameUUIDFromBytes("Bolívar".toByteArray()), "Bolívar", 6, 3, 2, 0, 1, 5, 4, 1, "Grupo C"),
                StandingRow(3, UUID.nameUUIDFromBytes("Independiente Rivadavia".toByteArray()), "Independiente Rivadavia", 3, 3, 1, 0, 2, 3, 5, -2, "Grupo C"),
                StandingRow(4, UUID.nameUUIDFromBytes("Deportivo La Guaira".toByteArray()), "Deportivo La Guaira", 0, 3, 0, 0, 3, 1, 6, -5, "Grupo C"),
                // Grupo D
                StandingRow(1, UUID.nameUUIDFromBytes("Cruzeiro".toByteArray()), "Cruzeiro", 7, 3, 2, 1, 0, 6, 2, 4, "Grupo D"),
                StandingRow(2, UUID.nameUUIDFromBytes("Boca Juniors".toByteArray()), "Boca Juniors", 6, 3, 2, 0, 1, 5, 3, 2, "Grupo D"),
                StandingRow(3, UUID.nameUUIDFromBytes("Barcelona SC".toByteArray()), "Barcelona SC", 3, 3, 1, 0, 2, 4, 6, -2, "Grupo D"),
                StandingRow(4, UUID.nameUUIDFromBytes("Universidad Católica".toByteArray()), "Universidad Católica", 1, 3, 0, 1, 2, 2, 6, -4, "Grupo D"),
                // Grupo E
                StandingRow(1, UUID.nameUUIDFromBytes("Corinthians".toByteArray()), "Corinthians", 9, 3, 3, 0, 0, 8, 2, 6, "Grupo E"),
                StandingRow(2, UUID.nameUUIDFromBytes("Peñarol".toByteArray()), "Peñarol", 6, 3, 2, 0, 1, 5, 3, 2, "Grupo E"),
                StandingRow(3, UUID.nameUUIDFromBytes("Independiente Santa Fe".toByteArray()), "Independiente Santa Fe", 3, 3, 1, 0, 2, 3, 5, -2, "Grupo E"),
                StandingRow(4, UUID.nameUUIDFromBytes("Platense".toByteArray()), "Platense", 0, 3, 0, 0, 3, 1, 7, -6, "Grupo E"),
                // Grupo F
                StandingRow(1, UUID.nameUUIDFromBytes("Palmeiras".toByteArray()), "Palmeiras", 9, 3, 3, 0, 0, 9, 2, 7, "Grupo F"),
                StandingRow(2, UUID.nameUUIDFromBytes("Cerro Porteño".toByteArray()), "Cerro Porteño", 6, 3, 2, 0, 1, 5, 4, 1, "Grupo F"),
                StandingRow(3, UUID.nameUUIDFromBytes("Sporting Cristal".toByteArray()), "Sporting Cristal", 3, 3, 1, 0, 2, 3, 6, -3, "Grupo F"),
                StandingRow(4, UUID.nameUUIDFromBytes("Junior Barranquilla".toByteArray()), "Junior Barranquilla", 0, 3, 0, 0, 3, 2, 7, -5, "Grupo F"),
                // Grupo G
                StandingRow(1, UUID.nameUUIDFromBytes("LDU Quito".toByteArray()), "LDU Quito", 7, 3, 2, 1, 0, 6, 2, 4, "Grupo G"),
                StandingRow(2, UUID.nameUUIDFromBytes("Lanús".toByteArray()), "Lanús", 6, 3, 2, 0, 1, 5, 3, 2, "Grupo G"),
                StandingRow(3, UUID.nameUUIDFromBytes("Mirassol".toByteArray()), "Mirassol", 3, 3, 1, 0, 2, 3, 5, -2, "Grupo G"),
                StandingRow(4, UUID.nameUUIDFromBytes("Always Ready".toByteArray()), "Always Ready", 1, 3, 0, 1, 2, 2, 6, -4, "Grupo G"),
                // Grupo H
                StandingRow(1, UUID.nameUUIDFromBytes("Independiente del Valle".toByteArray()), "Independiente del Valle", 7, 3, 2, 1, 0, 6, 2, 4, "Grupo H"),
                StandingRow(2, UUID.nameUUIDFromBytes("Rosario Central".toByteArray()), "Rosario Central", 6, 3, 2, 0, 1, 5, 3, 2, "Grupo H"),
                StandingRow(3, UUID.nameUUIDFromBytes("Libertad".toByteArray()), "Libertad", 3, 3, 1, 0, 2, 3, 5, -2, "Grupo H"),
                StandingRow(4, UUID.nameUUIDFromBytes("UCV FC".toByteArray()), "UCV FC", 1, 3, 0, 1, 2, 2, 6, -4, "Grupo H")
            )

            fun resolveGroup(m: MatchJpaEntity): String? {
                if (m.phase?.startsWith("Grupo") == true) return m.phase
                val homeGrp = libertadoresTeamGroupMap[m.homeTeamName]
                val awayGrp = libertadoresTeamGroupMap[m.awayTeamName]
                if (homeGrp != null && awayGrp != null && homeGrp == awayGrp) {
                    return homeGrp
                }
                return null
            }

            val groupMatches = matches.filter {
                it.phase?.startsWith("Grupo") == true ||
                it.phase == "Fase de Grupos" ||
                it.phase == "Fase de Liga" ||
                it.phase?.contains("League") == true ||
                (isLibertadores && resolveGroup(it) != null)
            }

            if (isLibertadores) {
                val groupNames = listOf("Grupo A", "Grupo B", "Grupo C", "Grupo D", "Grupo E", "Grupo F", "Grupo G", "Grupo H")
                val rows = mutableListOf<StandingRow>()

                groupNames.forEach { grp ->
                    val defaultRowsForGrp = defaultGroupRows.filter { it.groupName == grp }
                    val grpMatches = groupMatches.filter { resolveGroup(it) == grp }
                    val finishedGrpMatches = grpMatches.filter { it.status == MatchStatus.FINISHED }

                    if (finishedGrpMatches.isNotEmpty()) {
                        val teamNamesInGrp = (defaultRowsForGrp.map { it.teamName } + grpMatches.map { it.homeTeamName } + grpMatches.map { it.awayTeamName }).distinct()
                        val computedGrpRows = teamNamesInGrp.map { teamName ->
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
                    } else {
                        rows.addAll(defaultRowsForGrp)
                    }
                }

                return ResponseEntity.ok(rows)
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
        }

        // 1. Basquete (NBA / NBB / EuroLeague)
        if (sportName.contains("basquete") || sportName.contains("nba") || sportName.contains("nbb") || sportName.contains("euroleague")) {
            val isNba = (league?.name ?: "").contains("NBA", ignoreCase = true) || leagueId == UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
            if (isNba && espnBasketballClient != null) {
                val officialNbaStandings = espnBasketballClient.fetchNbaStandings()
                if (officialNbaStandings.isNotEmpty()) {
                    return ResponseEntity.ok(officialNbaStandings)
                }
            }

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
                        streak = if (won > 0) "W$won" else if (lost > 0) "L$lost" else "-"
                    )
                }.sortedWith(compareByDescending<StandingRow> { it.winRate ?: 0.0 }.thenByDescending { it.won ?: 0 })

                val leaderWon = computedRows.firstOrNull()?.won ?: 0
                val leaderLost = computedRows.firstOrNull()?.lost ?: 0
                val withGb = computedRows.mapIndexed { idx, r ->
                    val gbVal = if (idx == 0) "-" else {
                        val rWon = r.won ?: 0
                        val rLost = r.lost ?: 0
                        val diff = ((leaderWon - rWon) + (rLost - leaderLost)) / 2.0
                        if (diff <= 0) "0.0" else if (diff % 1.0 == 0.0) "${diff.toInt()}.0" else "$diff"
                    }
                    r.copy(position = idx + 1, gamesBehind = gbVal)
                }

                return ResponseEntity.ok(withGb)
            }

            // Fallback para Basquete (NBB ou NBA)
            val isNbb = (league?.name ?: "").contains("NBB", ignoreCase = true) || leagueId == UUID.fromString("2dbd1112-9cde-4411-b0db-b06d0421da6a")
            val basketballFallback = if (isNbb) {
                listOf(
                    StandingRow(1, UUID.randomUUID(), "Flamengo", played = 20, won = 17, lost = 3, winRate = 0.850, gamesBehind = "-", streak = "W5"),
                    StandingRow(2, UUID.randomUUID(), "Franca", played = 20, won = 16, lost = 4, winRate = 0.800, gamesBehind = "1.0", streak = "W2"),
                    StandingRow(3, UUID.randomUUID(), "Minas", played = 20, won = 15, lost = 5, winRate = 0.750, gamesBehind = "2.0", streak = "W1"),
                    StandingRow(4, UUID.randomUUID(), "São Paulo", played = 20, won = 13, lost = 7, winRate = 0.650, gamesBehind = "4.0", streak = "L1"),
                    StandingRow(5, UUID.randomUUID(), "Corinthians", played = 20, won = 11, lost = 9, winRate = 0.550, gamesBehind = "6.0", streak = "W1"),
                    StandingRow(6, UUID.randomUUID(), "Bauru", played = 20, won = 10, lost = 10, winRate = 0.500, gamesBehind = "7.0", streak = "L2")
                )
            } else {
                listOf(
                    StandingRow(1, UUID.randomUUID(), "Boston Celtics", played = 82, won = 64, lost = 18, winRate = 0.780, gamesBehind = "-", streak = "W5", groupName = "Eastern Conference"),
                    StandingRow(2, UUID.randomUUID(), "New York Knicks", played = 82, won = 50, lost = 32, winRate = 0.610, gamesBehind = "14.0", streak = "W1", groupName = "Eastern Conference"),
                    StandingRow(1, UUID.randomUUID(), "Oklahoma City Thunder", played = 82, won = 57, lost = 25, winRate = 0.695, gamesBehind = "-", streak = "W3", groupName = "Western Conference"),
                    StandingRow(2, UUID.randomUUID(), "Denver Nuggets", played = 82, won = 57, lost = 25, winRate = 0.695, gamesBehind = "-", streak = "W2", groupName = "Western Conference")
                )
            }
            return ResponseEntity.ok(basketballFallback)
        }

        // 2. eSports (LoL / CS / Valorant / Dota 2)
        if (sportName.contains("esports") || sportName.contains("league of legends") || sportName.contains("counter-strike") || sportName.contains("valorant") || sportName.contains("cblol") || sportName.contains("worlds")) {
            val teams = (matches.map { it.homeTeamName } + matches.map { it.awayTeamName }).distinct()
            if (teams.isNotEmpty()) {
                val finishedMatches = matches.filter { it.status == MatchStatus.FINISHED }
                val computedRows = teams.map { teamName ->
                    var sWon = 0
                    var sLost = 0
                    var mWon = 0
                    var mLost = 0
                    finishedMatches.forEach { m ->
                        if (m.homeTeamName == teamName || m.awayTeamName == teamName) {
                            val isHome = m.homeTeamName == teamName
                            val myScore = if (isHome) m.homeScore ?: 0 else m.awayScore ?: 0
                            val oppScore = if (isHome) m.awayScore ?: 0 else m.homeScore ?: 0
                            mWon += myScore
                            mLost += oppScore
                            if (myScore > oppScore) sWon++ else if (myScore < oppScore) sLost++
                        }
                    }
                    StandingRow(
                        position = 0,
                        teamId = UUID.nameUUIDFromBytes(teamName.toByteArray()),
                        teamName = teamName,
                        seriesWon = sWon,
                        seriesLost = sLost,
                        mapsWon = mWon,
                        mapsLost = mLost,
                        streak = if (sWon > 0) "W$sWon" else if (sLost > 0) "L$sLost" else "-"
                    )
                }.sortedWith(
                    compareByDescending<StandingRow> { it.seriesWon ?: 0 }
                        .thenBy { it.seriesLost ?: Int.MAX_VALUE }
                        .thenByDescending { (it.mapsWon ?: 0) - (it.mapsLost ?: 0) }
                ).mapIndexed { idx, r -> r.copy(position = idx + 1) }

                return ResponseEntity.ok(computedRows)
            }

            // Fallbacks específicos por liga de eSports
            val leagueNameLower = (league?.name ?: "").lowercase()
            val esportsFallback = when {
                leagueNameLower.contains("vct") || leagueNameLower.contains("valorant") || leagueId == UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "Sentinels", seriesWon = 8, seriesLost = 2, mapsWon = 18, mapsLost = 7, streak = "W4"),
                    StandingRow(2, UUID.randomUUID(), "LOUD", seriesWon = 7, seriesLost = 3, mapsWon = 16, mapsLost = 8, streak = "W2"),
                    StandingRow(3, UUID.randomUUID(), "Paper Rex", seriesWon = 6, seriesLost = 4, mapsWon = 14, mapsLost = 10, streak = "L1"),
                    StandingRow(4, UUID.randomUUID(), "Fnatic", seriesWon = 6, seriesLost = 4, mapsWon = 13, mapsLost = 11, streak = "W1"),
                    StandingRow(5, UUID.randomUUID(), "Cloud9", seriesWon = 5, seriesLost = 5, mapsWon = 11, mapsLost = 12, streak = "L2"),
                    StandingRow(6, UUID.randomUUID(), "KRÜ Esports", seriesWon = 4, seriesLost = 6, mapsWon = 9, mapsLost = 14, streak = "W1")
                )
                leagueNameLower.contains("cs") || leagueNameLower.contains("major") || leagueId == UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "FaZe Clan", seriesWon = 9, seriesLost = 1, mapsWon = 20, mapsLost = 5, streak = "W5"),
                    StandingRow(2, UUID.randomUUID(), "Natus Vincere", seriesWon = 8, seriesLost = 2, mapsWon = 18, mapsLost = 6, streak = "W3"),
                    StandingRow(3, UUID.randomUUID(), "Team Vitality", seriesWon = 7, seriesLost = 3, mapsWon = 16, mapsLost = 9, streak = "L1"),
                    StandingRow(4, UUID.randomUUID(), "G2 Esports", seriesWon = 6, seriesLost = 4, mapsWon = 14, mapsLost = 10, streak = "W1"),
                    StandingRow(5, UUID.randomUUID(), "MOUZ", seriesWon = 5, seriesLost = 5, mapsWon = 12, mapsLost = 12, streak = "L2"),
                    StandingRow(6, UUID.randomUUID(), "Virtus.pro", seriesWon = 4, seriesLost = 6, mapsWon = 10, mapsLost = 14, streak = "W1")
                )
                leagueNameLower.contains("worlds") || leagueId == UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "T1", seriesWon = 6, seriesLost = 1, mapsWon = 15, mapsLost = 4, streak = "W4"),
                    StandingRow(2, UUID.randomUUID(), "Gen.G", seriesWon = 5, seriesLost = 2, mapsWon = 13, mapsLost = 6, streak = "W2"),
                    StandingRow(3, UUID.randomUUID(), "Bilibili Gaming", seriesWon = 5, seriesLost = 2, mapsWon = 12, mapsLost = 7, streak = "L1"),
                    StandingRow(4, UUID.randomUUID(), "Top Esports", seriesWon = 4, seriesLost = 3, mapsWon = 10, mapsLost = 8, streak = "W1"),
                    StandingRow(5, UUID.randomUUID(), "Hanwha Life", seriesWon = 3, seriesLost = 4, mapsWon = 8, mapsLost = 10, streak = "L2"),
                    StandingRow(6, UUID.randomUUID(), "G2 Esports", seriesWon = 2, seriesLost = 5, mapsWon = 6, mapsLost = 12, streak = "L1")
                )
                else -> listOf(
                    StandingRow(1, UUID.randomUUID(), "LOUD", seriesWon = 7, seriesLost = 1, mapsWon = 15, mapsLost = 4, streak = "W6"),
                    StandingRow(2, UUID.randomUUID(), "PAIN Gaming", seriesWon = 6, seriesLost = 2, mapsWon = 13, mapsLost = 6, streak = "W2"),
                    StandingRow(3, UUID.randomUUID(), "FURIA Esports", seriesWon = 4, seriesLost = 4, mapsWon = 10, mapsLost = 9, streak = "L1"),
                    StandingRow(4, UUID.randomUUID(), "RED Canids", seriesWon = 3, seriesLost = 5, mapsWon = 7, mapsLost = 11, streak = "L2"),
                    StandingRow(5, UUID.randomUUID(), "Vivo Keyd Stars", seriesWon = 3, seriesLost = 5, mapsWon = 8, mapsLost = 12, streak = "W1"),
                    StandingRow(6, UUID.randomUUID(), "Fluxo", seriesWon = 2, seriesLost = 6, mapsWon = 5, mapsLost = 13, streak = "L3")
                )
            }
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
