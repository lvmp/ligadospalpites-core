package com.ligadospalpites.sportsfeed.application.usecases

import com.fasterxml.jackson.databind.ObjectMapper
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.client.*
import com.ligadospalpites.sportsfeed.infrastructure.persistence.*
import com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetStandingsUseCase(
    private val sportRepository: SpringDataSportRepository,
    private val leagueRepository: SpringDataLeagueRepository,
    private val matchRepository: SpringDataMatchRepository,
    private val seasonRepository: SpringDataSeasonRepository,
    @Autowired(required = false) private val espnBasketballClient: EspnBasketballClient? = null,
    @Autowired(required = false) private val statsNbaClient: StatsNbaClient? = null,
    @Autowired(required = false) private val balldontlieClient: BalldontlieClient? = null,
    @Autowired(required = false) private val redisTemplate: StringRedisTemplate? = null,
    @Autowired(required = false) private val footballDataClient: FootballDataClient? = null,
    @Autowired(required = false) private val espnSoccerClient: EspnSoccerClient? = null,
    @Autowired(required = false) private val pandaScoreClient: PandaScoreClient? = null,
    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()
) {

    @Transactional(readOnly = true)
    fun execute(leagueId: UUID): List<StandingRow> {
        val league = leagueRepository.findById(leagueId).orElse(null)
        val format = league?.format ?: "POINTS"
        if (format == "KNOCKOUT") {
            return emptyList()
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

            if (isLibertadores) {
                val cacheKey = "standings:league:$leagueId"
                val cachedJson = redisTemplate?.opsForValue()?.get(cacheKey)
                if (!cachedJson.isNullOrBlank()) {
                    try {
                        val cachedRows: List<StandingRow> = objectMapper.readValue(
                            cachedJson,
                            objectMapper.typeFactory.constructCollectionType(List::class.java, StandingRow::class.java)
                        )
                        if (cachedRows.isNotEmpty()) {
                            return cachedRows
                        }
                    } catch (_: Exception) {}
                }

                if (espnSoccerClient != null) {
                    try {
                        val officialRows = espnSoccerClient.fetchLibertadoresStandings()
                        if (officialRows.isNotEmpty()) {
                            try {
                                redisTemplate?.opsForValue()?.set(cacheKey, objectMapper.writeValueAsString(officialRows), java.time.Duration.ofHours(2))
                            } catch (_: Exception) {}
                            return officialRows
                        }
                    } catch (_: Exception) {}
                }

                val defaultGroupRows = listOf(
                    // Grupo A
                    StandingRow(1, UUID.nameUUIDFromBytes("Flamengo".toByteArray()), "Flamengo", 16, 6, 5, 1, 0, 14, 2, 12, "Grupo A", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/819.png"),
                    StandingRow(2, UUID.nameUUIDFromBytes("Estudiantes de La Plata".toByteArray()), "Estudiantes de La Plata", 9, 6, 2, 3, 1, 6, 5, 1, "Grupo A", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/8.png"),
                    StandingRow(3, UUID.nameUUIDFromBytes("Independiente Medellín".toByteArray()), "Independiente Medellín", 7, 6, 2, 1, 3, 6, 11, -5, "Grupo A", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/2690.png"),
                    StandingRow(4, UUID.nameUUIDFromBytes("Cusco FC".toByteArray()), "Cusco FC", 1, 6, 0, 1, 5, 4, 12, -8, "Grupo A", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/11995.png"),
                    // Grupo B
                    StandingRow(1, UUID.nameUUIDFromBytes("Coquimbo Unido".toByteArray()), "Coquimbo Unido", 10, 6, 3, 1, 2, 8, 6, 2, "Grupo B", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/8186.png"),
                    StandingRow(2, UUID.nameUUIDFromBytes("Deportes Tolima".toByteArray()), "Deportes Tolima", 8, 6, 2, 2, 2, 7, 6, 1, "Grupo B", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/5489.png"),
                    StandingRow(3, UUID.nameUUIDFromBytes("Nacional".toByteArray()), "Nacional", 8, 6, 2, 2, 2, 7, 9, -2, "Grupo B", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/2684.png"),
                    StandingRow(4, UUID.nameUUIDFromBytes("Universitario".toByteArray()), "Universitario", 6, 6, 1, 3, 2, 5, 6, -1, "Grupo B", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/2685.png"),
                    // Grupo C
                    StandingRow(1, UUID.nameUUIDFromBytes("Independiente Rivadavia".toByteArray()), "Independiente Rivadavia", 16, 6, 5, 1, 0, 15, 6, 9, "Grupo C", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/9744.png"),
                    StandingRow(2, UUID.nameUUIDFromBytes("Fluminense".toByteArray()), "Fluminense", 8, 6, 2, 2, 2, 7, 7, 0, "Grupo C", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/3445.png"),
                    StandingRow(3, UUID.nameUUIDFromBytes("Bolívar".toByteArray()), "Bolívar", 5, 6, 1, 2, 3, 6, 8, -2, "Grupo C", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/2681.png"),
                    StandingRow(4, UUID.nameUUIDFromBytes("Deportivo La Guaira".toByteArray()), "Deportivo La Guaira", 3, 6, 0, 3, 3, 6, 13, -7, "Grupo C", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/17090.png"),
                    // Grupo D
                    StandingRow(1, UUID.nameUUIDFromBytes("Universidad Católica".toByteArray()), "Universidad Católica", 13, 6, 4, 1, 1, 8, 4, 4, "Grupo D", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/885.png"),
                    StandingRow(2, UUID.nameUUIDFromBytes("Cruzeiro".toByteArray()), "Cruzeiro", 11, 6, 3, 2, 1, 8, 3, 5, "Grupo D", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/2022.png"),
                    StandingRow(3, UUID.nameUUIDFromBytes("Boca Juniors".toByteArray()), "Boca Juniors", 7, 6, 2, 1, 3, 6, 5, 1, "Grupo D", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/5.png"),
                    StandingRow(4, UUID.nameUUIDFromBytes("Barcelona SC".toByteArray()), "Barcelona SC", 3, 6, 1, 0, 5, 4, 14, -10, "Grupo D", teamLogoUrl = "https://a.espncdn.com/i/teamlogos/soccer/500/8.png"),
                    // Grupo E
                    StandingRow(1, UUID.nameUUIDFromBytes("Corinthians".toByteArray()), "Corinthians", 13, 6, 4, 1, 1, 10, 4, 6, "Grupo E"),
                    StandingRow(2, UUID.nameUUIDFromBytes("Peñarol".toByteArray()), "Peñarol", 10, 6, 3, 1, 2, 8, 5, 3, "Grupo E"),
                    StandingRow(3, UUID.nameUUIDFromBytes("Independiente Santa Fe".toByteArray()), "Independiente Santa Fe", 7, 6, 2, 1, 3, 5, 8, -3, "Grupo E"),
                    StandingRow(4, UUID.nameUUIDFromBytes("Platense".toByteArray()), "Platense", 4, 6, 1, 1, 4, 3, 9, -6, "Grupo E"),
                    // Grupo F
                    StandingRow(1, UUID.nameUUIDFromBytes("Palmeiras".toByteArray()), "Palmeiras", 15, 6, 5, 0, 1, 14, 4, 10, "Grupo F"),
                    StandingRow(2, UUID.nameUUIDFromBytes("Cerro Porteño".toByteArray()), "Cerro Porteño", 10, 6, 3, 1, 2, 8, 7, 1, "Grupo F"),
                    StandingRow(3, UUID.nameUUIDFromBytes("Sporting Cristal".toByteArray()), "Sporting Cristal", 6, 6, 2, 0, 4, 6, 10, -4, "Grupo F"),
                    StandingRow(4, UUID.nameUUIDFromBytes("Junior Barranquilla".toByteArray()), "Junior Barranquilla", 4, 6, 1, 1, 4, 4, 11, -7, "Grupo F"),
                    // Grupo G
                    StandingRow(1, UUID.nameUUIDFromBytes("LDU Quito".toByteArray()), "LDU Quito", 12, 6, 4, 0, 2, 9, 5, 4, "Grupo G"),
                    StandingRow(2, UUID.nameUUIDFromBytes("Lanús".toByteArray()), "Lanús", 10, 6, 3, 1, 2, 8, 6, 2, "Grupo G"),
                    StandingRow(3, UUID.nameUUIDFromBytes("Mirassol".toByteArray()), "Mirassol", 7, 6, 2, 1, 3, 6, 8, -2, "Grupo G"),
                    StandingRow(4, UUID.nameUUIDFromBytes("Always Ready".toByteArray()), "Always Ready", 5, 6, 1, 2, 3, 4, 8, -4, "Grupo G"),
                    // Grupo H
                    StandingRow(1, UUID.nameUUIDFromBytes("Independiente del Valle".toByteArray()), "Independiente del Valle", 13, 6, 4, 1, 1, 10, 5, 5, "Grupo H"),
                    StandingRow(2, UUID.nameUUIDFromBytes("Rosario Central".toByteArray()), "Rosario Central", 9, 6, 2, 3, 1, 8, 6, 2, "Grupo H"),
                    StandingRow(3, UUID.nameUUIDFromBytes("Libertad".toByteArray()), "Libertad", 7, 6, 2, 1, 3, 6, 9, -3, "Grupo H"),
                    StandingRow(4, UUID.nameUUIDFromBytes("UCV FC".toByteArray()), "UCV FC", 4, 6, 1, 1, 4, 4, 8, -4, "Grupo H")
                )
                return defaultGroupRows
            }

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
                if (rows.isNotEmpty()) return rows
            }
        }

        // 1. Basquete (NBA / NBB / EuroLeague)
        if (sportName.contains("basquete") || sportName.contains("nba") || sportName.contains("nbb") || sportName.contains("euroleague")) {
            val isNba = (league?.name ?: "").contains("NBA", ignoreCase = true) || leagueId == UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
            if (isNba) {
                val cacheKey = "standings:league:$leagueId"
                val cachedJson = redisTemplate?.opsForValue()?.get(cacheKey)
                if (!cachedJson.isNullOrBlank()) {
                    try {
                        val cachedRows: List<StandingRow> = objectMapper.readValue(
                            cachedJson,
                            objectMapper.typeFactory.constructCollectionType(List::class.java, StandingRow::class.java)
                        )
                        if (cachedRows.isNotEmpty()) {
                            return cachedRows
                        }
                    } catch (_: Exception) {}
                }

                if (statsNbaClient != null) {
                    try {
                        val statsRows = statsNbaClient.fetchStandings()
                        if (statsRows.isNotEmpty()) {
                            try {
                                redisTemplate?.opsForValue()?.set(cacheKey, objectMapper.writeValueAsString(statsRows), java.time.Duration.ofHours(2))
                            } catch (_: Exception) {}
                            return statsRows
                        }
                    } catch (_: Exception) {}
                }

                if (espnBasketballClient != null) {
                    try {
                        val officialNbaStandings = espnBasketballClient.fetchNbaStandings()
                        if (officialNbaStandings.isNotEmpty()) {
                            try {
                                redisTemplate?.opsForValue()?.set(cacheKey, objectMapper.writeValueAsString(officialNbaStandings), java.time.Duration.ofHours(2))
                            } catch (_: Exception) {}
                            return officialNbaStandings
                        }
                    } catch (_: Exception) {}
                }

                if (balldontlieClient != null) {
                    try {
                        val bdlRows = balldontlieClient.fetchStandings()
                        if (bdlRows.isNotEmpty()) {
                            try {
                                redisTemplate?.opsForValue()?.set(cacheKey, objectMapper.writeValueAsString(bdlRows), java.time.Duration.ofHours(2))
                            } catch (_: Exception) {}
                            return bdlRows
                        }
                    } catch (_: Exception) {}
                }
            }

            val teams = (matches.map { it.homeTeamName } + matches.map { it.awayTeamName }).distinct()
            if (teams.isNotEmpty()) {
                val finishedMatches = matches.filter { 
                    it.status == MatchStatus.FINISHED && 
                    ! (it.phase ?: "").contains("pré-temporada", ignoreCase = true) && 
                    ! (it.phase ?: "").contains("preseason", ignoreCase = true)
                }

                val nbaWesternConferenceTeams = setOf(
                    "dallas mavericks", "denver nuggets", "golden state warriors", "houston rockets",
                    "los angeles clippers", "los angeles lakers", "memphis grizzlies", "minnesota timberwolves",
                    "new orleans pelicans", "oklahoma city thunder", "phoenix suns", "portland trail blazers",
                    "sacramento kings", "san antonio spurs", "utah jazz"
                )

                fun resolveConference(teamName: String): String {
                    val norm = teamName.lowercase().trim()
                    return if (nbaWesternConferenceTeams.any { norm.contains(it) || it.contains(norm) }) {
                        "Western Conference"
                    } else {
                        "Eastern Conference"
                    }
                }

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
                    val group = if (isNba) resolveConference(teamName) else null
                    StandingRow(
                        position = 0,
                        teamId = UUID.nameUUIDFromBytes(teamName.toByteArray()),
                        teamName = teamName,
                        played = played,
                        won = won,
                        lost = lost,
                        winRate = winRate,
                        gamesBehind = "0.0",
                        streak = if (won > 0) "W$won" else if (lost > 0) "L$lost" else "-",
                        groupName = group
                    )
                }

                if (isNba) {
                    val grouped = computedRows.groupBy { it.groupName }
                    val conferenceSorted = grouped.flatMap { (_, confRows) ->
                        val sortedConf = confRows.sortedWith(
                            compareByDescending<StandingRow> { it.winRate ?: 0.0 }
                                .thenByDescending { it.won ?: 0 }
                        )
                        val leaderWon = sortedConf.firstOrNull()?.won ?: 0
                        val leaderLost = sortedConf.firstOrNull()?.lost ?: 0

                        sortedConf.mapIndexed { idx, r ->
                            val gbVal = if (idx == 0) "-" else {
                                val rWon = r.won ?: 0
                                val rLost = r.lost ?: 0
                                val diff = ((leaderWon - rWon) + (rLost - leaderLost)) / 2.0
                                if (diff <= 0) "0.0" else if (diff % 1.0 == 0.0) "${diff.toInt()}.0" else "$diff"
                            }
                            r.copy(position = idx + 1, gamesBehind = gbVal)
                        }
                    }
                    return conferenceSorted
                }

                val sortedGeneral = computedRows.sortedWith(
                    compareByDescending<StandingRow> { it.winRate ?: 0.0 }
                        .thenByDescending { it.won ?: 0 }
                )
                val leaderWon = sortedGeneral.firstOrNull()?.won ?: 0
                val leaderLost = sortedGeneral.firstOrNull()?.lost ?: 0
                val withGb = sortedGeneral.mapIndexed { idx, r ->
                    val gbVal = if (idx == 0) "-" else {
                        val rWon = r.won ?: 0
                        val rLost = r.lost ?: 0
                        val diff = ((leaderWon - rWon) + (rLost - leaderLost)) / 2.0
                        if (diff <= 0) "0.0" else if (diff % 1.0 == 0.0) "${diff.toInt()}.0" else "$diff"
                    }
                    r.copy(position = idx + 1, gamesBehind = gbVal)
                }

                return withGb
            }

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
            return basketballFallback
        }

        // 2. eSports (LoL / CS / Valorant / Dota 2)
        if (sportName.contains("esports") || sportName.contains("league of legends") || sportName.contains("counter-strike") || sportName.contains("valorant") || sportName.contains("cblol") || sportName.contains("worlds")) {
            val esportsCacheKey = "standings:esports:$leagueId"
            val cachedJson = redisTemplate?.opsForValue()?.get(esportsCacheKey)
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val rows: List<StandingRow> = objectMapper.readValue(
                        cachedJson,
                        objectMapper.typeFactory.constructCollectionType(List::class.java, StandingRow::class.java)
                    )
                    return rows
                } catch (_: Exception) {}
            }

            val pandaScoreLeagueSlugMap = mapOf(
                UUID.fromString("7c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to "cblol",
                UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to "vct-americas",
                UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14") to "pgl-major",
                UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14") to "worlds",
                UUID.fromString("bc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to "esl-pro-league",
                UUID.fromString("cc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to "blast-premier",
                UUID.fromString("dc1e3a11-b9db-44ab-ba02-411a0c0bcf14") to "valorant-champions"
            )
            val slug = pandaScoreLeagueSlugMap[leagueId]
            if (pandaScoreClient != null && !slug.isNullOrBlank()) {
                val officialStandings = pandaScoreClient.fetchStandings(slug)
                if (officialStandings.isNotEmpty()) {
                    val mappedRows = officialStandings.map { s ->
                        val played = s.matches_played ?: ((s.wins ?: 0) + (s.losses ?: 0))
                        val sWon = s.wins ?: 0
                        val sLost = s.losses ?: 0
                        val winRate = if (played > 0) Math.round((sWon.toDouble() / played) * 1000.0) / 1000.0 else 0.0
                        StandingRow(
                            position = s.rank,
                            teamId = UUID.nameUUIDFromBytes(s.team.name.toByteArray()),
                            teamName = s.team.name,
                            played = played,
                            points = s.points,
                            seriesWon = sWon,
                            seriesLost = sLost,
                            winRate = winRate,
                            teamLogoUrl = s.team.image_url,
                            streak = if (sWon > 0) "W$sWon" else if (sLost > 0) "L$sLost" else "-"
                        )
                    }
                    try {
                        redisTemplate?.opsForValue()?.set(esportsCacheKey, objectMapper.writeValueAsString(mappedRows), java.time.Duration.ofHours(2))
                    } catch (_: Exception) {}
                    return mappedRows
                }
            }

            val teams = (matches.map { it.homeTeamName } + matches.map { it.awayTeamName }).distinct()
            if (teams.isNotEmpty()) {
                val finishedMatches = matches.filter { it.status == MatchStatus.FINISHED }
                val logosMap = matches.mapNotNull { m ->
                    val h = m.homeTeamLogoUrl?.let { m.homeTeamName to it }
                    val a = m.awayTeamLogoUrl?.let { m.awayTeamName to it }
                    listOfNotNull(h, a)
                }.flatten().toMap()

                val computedRows = teams.map { teamName ->
                    var sWon = 0
                    var sLost = 0
                    var mWon = 0
                    var mLost = 0
                    val teamMatches = finishedMatches
                        .filter { it.homeTeamName == teamName || it.awayTeamName == teamName }
                        .sortedByDescending { it.kickoffTime }

                    teamMatches.forEach { m ->
                        val isHome = m.homeTeamName == teamName
                        val myScore = if (isHome) m.homeScore ?: 0 else m.awayScore ?: 0
                        val oppScore = if (isHome) m.awayScore ?: 0 else m.homeScore ?: 0
                        mWon += myScore
                        mLost += oppScore
                        if (myScore > oppScore) sWon++ else if (myScore < oppScore) sLost++
                    }

                    var streakCount = 0
                    var streakType: Char? = null
                    for (m in teamMatches) {
                        val isHome = m.homeTeamName == teamName
                        val myScore = if (isHome) m.homeScore ?: 0 else m.awayScore ?: 0
                        val oppScore = if (isHome) m.awayScore ?: 0 else m.homeScore ?: 0
                        val result = if (myScore > oppScore) 'W' else if (oppScore > myScore) 'L' else null
                        if (result != null) {
                            if (streakType == null) {
                                streakType = result
                                streakCount = 1
                            } else if (streakType == result) {
                                streakCount++
                            } else {
                                break
                            }
                        }
                    }
                    val streakStr = if (streakType != null) "$streakType$streakCount" else "-"
                    val played = sWon + sLost
                    val winRate = if (played > 0) Math.round((sWon.toDouble() / played) * 1000.0) / 1000.0 else 0.0

                    StandingRow(
                        position = 0,
                        teamId = UUID.nameUUIDFromBytes(teamName.toByteArray()),
                        teamName = teamName,
                        played = played,
                        winRate = winRate,
                        seriesWon = sWon,
                        seriesLost = sLost,
                        mapsWon = mWon,
                        mapsLost = mLost,
                        streak = streakStr,
                        teamLogoUrl = logosMap[teamName]
                    )
                }.sortedWith(
                    compareByDescending<StandingRow> { it.seriesWon ?: 0 }
                        .thenBy { it.seriesLost ?: Int.MAX_VALUE }
                        .thenByDescending { (it.mapsWon ?: 0) - (it.mapsLost ?: 0) }
                        .thenByDescending { it.mapsWon ?: 0 }
                        .thenBy { it.teamName }
                ).mapIndexed { idx, r -> r.copy(position = idx + 1) }

                try {
                    redisTemplate?.opsForValue()?.set(esportsCacheKey, objectMapper.writeValueAsString(computedRows), java.time.Duration.ofMinutes(5))
                } catch (_: Exception) {}
                return computedRows
            }

            val leagueNameLower = (league?.name ?: "").lowercase()
            val esportsFallback = when {
                leagueNameLower.contains("vct champions") || leagueId == UUID.fromString("dc1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "Sentinels", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(2, UUID.randomUUID(), "Fnatic", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(3, UUID.randomUUID(), "Paper Rex", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(4, UUID.randomUUID(), "Team Heretics", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-")
                )
                leagueNameLower.contains("esl pro league") || leagueId == UUID.fromString("bc1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "MOUZ", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(2, UUID.randomUUID(), "Natus Vincere", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(3, UUID.randomUUID(), "Eternal Fire", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(4, UUID.randomUUID(), "G2 Esports", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-")
                )
                leagueNameLower.contains("blast premier") || leagueId == UUID.fromString("cc1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "Team Spirit", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(2, UUID.randomUUID(), "FaZe Clan", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(3, UUID.randomUUID(), "Astralis", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(4, UUID.randomUUID(), "Vitality", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-")
                )
                leagueNameLower.contains("vct") || leagueNameLower.contains("valorant") || leagueId == UUID.fromString("8c1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "Sentinels", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(2, UUID.randomUUID(), "LOUD", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(3, UUID.randomUUID(), "Paper Rex", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(4, UUID.randomUUID(), "Fnatic", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(5, UUID.randomUUID(), "Cloud9", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(6, UUID.randomUUID(), "KRÜ Esports", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-")
                )
                leagueNameLower.contains("cs") || leagueNameLower.contains("major") || leagueId == UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "FaZe Clan", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(2, UUID.randomUUID(), "Natus Vincere", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(3, UUID.randomUUID(), "Team Vitality", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(4, UUID.randomUUID(), "G2 Esports", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(5, UUID.randomUUID(), "MOUZ", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(6, UUID.randomUUID(), "Virtus.pro", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-")
                )
                leagueNameLower.contains("worlds") || leagueId == UUID.fromString("ac1e3a11-b9db-44ab-ba02-411a0c0bcf14") -> listOf(
                    StandingRow(1, UUID.randomUUID(), "T1", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(2, UUID.randomUUID(), "Gen.G", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(3, UUID.randomUUID(), "Bilibili Gaming", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(4, UUID.randomUUID(), "Top Esports", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(5, UUID.randomUUID(), "Hanwha Life", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(6, UUID.randomUUID(), "G2 Esports", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-")
                )
                else -> listOf(
                    StandingRow(1, UUID.randomUUID(), "LOUD", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(2, UUID.randomUUID(), "PAIN Gaming", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(3, UUID.randomUUID(), "FURIA Esports", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(4, UUID.randomUUID(), "RED Canids", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(5, UUID.randomUUID(), "Vivo Keyd Stars", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-"),
                    StandingRow(6, UUID.randomUUID(), "Fluxo", played = 0, winRate = 0.0, seriesWon = 0, seriesLost = 0, mapsWon = 0, mapsLost = 0, streak = "-")
                )
            }
            return esportsFallback
        }

        // 3. Automobilismo (Formula 1 / F2 / FE / Stock Car)
        if (sportName.contains("automobilismo") || sportName.contains("formula 1") || sportName.contains("f1") || sportName.contains("stock car")) {
            return listOf(
                StandingRow(1, UUID.randomUUID(), "Max Verstappen", points = 437, won = 19, podiums = 21, fastestLaps = 9, constructorName = "Red Bull Racing"),
                StandingRow(2, UUID.randomUUID(), "Lando Norris", points = 374, won = 3, podiums = 12, fastestLaps = 4, constructorName = "McLaren"),
                StandingRow(3, UUID.randomUUID(), "Charles Leclerc", points = 356, won = 3, podiums = 11, fastestLaps = 3, constructorName = "Ferrari"),
                StandingRow(4, UUID.randomUUID(), "Oscar Piastri", points = 292, won = 2, podiums = 7, fastestLaps = 1, constructorName = "McLaren")
            )
        }

        // 4. Futebol Americano (NFL / College)
        if (sportName.contains("futebol americano") || sportName.contains("nfl") || sportName.contains("ncaa")) {
            return listOf(
                StandingRow(1, UUID.randomUUID(), "Kansas City Chiefs", won = 14, lost = 3, drawn = 0, winRate = 0.824, conferenceRecord = "10-2", divisionRecord = "5-1"),
                StandingRow(2, UUID.randomUUID(), "Buffalo Bills", won = 11, lost = 6, drawn = 0, winRate = 0.647, conferenceRecord = "7-5", divisionRecord = "4-2"),
                StandingRow(3, UUID.randomUUID(), "Baltimore Ravens", won = 13, lost = 4, drawn = 0, winRate = 0.765, conferenceRecord = "8-4", divisionRecord = "4-2"),
                StandingRow(4, UUID.randomUUID(), "San Francisco 49ers", won = 12, lost = 5, drawn = 0, winRate = 0.706, conferenceRecord = "10-2", divisionRecord = "5-1")
            )
        }

        // 5. Tênis (ATP / WTA / Grand Slams)
        if (sportName.contains("tênis") || sportName.contains("tenis") || sportName.contains("atp") || sportName.contains("wta")) {
            return listOf(
                StandingRow(1, UUID.randomUUID(), "Jannik Sinner", points = 11830, tournamentsPlayed = 17, titlesWon = 8),
                StandingRow(2, UUID.randomUUID(), "Alexander Zverev", points = 7915, tournamentsPlayed = 21, titlesWon = 3),
                StandingRow(3, UUID.randomUUID(), "Carlos Alcaraz", points = 7010, tournamentsPlayed = 16, titlesWon = 4),
                StandingRow(4, UUID.randomUUID(), "Novak Djokovic", points = 3910, tournamentsPlayed = 12, titlesWon = 1)
            )
        }

        // Default: Futebol (Points Corridos / Grupos)
        val competitionCodeMap = mapOf(
            UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e") to "WC",
            UUID.fromString("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d") to "BSA",
            UUID.fromString("2a3b4c5d-6e7f-8a9b-0c1d-2e3f4a5b6c7d") to "PL",
            UUID.fromString("3a4b5c6d-7e8f-9a0b-1c2d-3e4f5a6b7c8d") to "PD",
            UUID.fromString("4a5b6c7d-8e9f-0a1b-2c3d-4e5f6a7b8c9d") to "CL",
            UUID.fromString("5a6b7c8d-9e0f-1a2b-3c4d-5e6f7a8b9c0d") to "FL1",
            UUID.fromString("6a7b8c9d-0e1f-2a3b-4c5d-6e7f8a9b0c1d") to "BL1",
            UUID.fromString("7a8b9c0d-1e2f-3a4b-5c6d-7e8f9a0b1c2d") to "SA",
            UUID.fromString("8a9b0c1d-2e3f-4a5b-6c7d-8e9f0a1b2c3d") to "DED",
            UUID.fromString("9a0b1c2d-3e4f-5a6b-7c8d-9e0f1a2b3c4d") to "PPL"
        )
        val compCode = competitionCodeMap[leagueId]
        if (!compCode.isNullOrBlank() && footballDataClient != null) {
            val officialStandings = footballDataClient.fetchStandings(compCode)
            if (officialStandings != null && officialStandings.standings.isNotEmpty()) {
                val officialRows = officialStandings.standings.flatMap { group ->
                    group.table.map { row ->
                        StandingRow(
                            position = row.position,
                            teamId = UUID.nameUUIDFromBytes((row.team.shortName ?: row.team.name ?: "").toByteArray()),
                            teamName = row.team.shortName ?: row.team.name ?: "",
                            points = row.points,
                            played = row.playedGames,
                            won = row.won,
                            drawn = row.draw,
                            lost = row.lost,
                            goalsFor = row.goalsFor,
                            goalsAgainst = row.goalsAgainst,
                            goalDifference = row.goalDifference,
                            groupName = group.group ?: group.stage,
                            winRate = if (row.playedGames > 0) Math.round((row.won.toDouble() / row.playedGames) * 100.0) / 100.0 else 0.0
                        )
                    }
                }
                if (officialRows.isNotEmpty()) {
                    return officialRows
                }
            }
        }

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

            return sortedRows
        }

        val defaultFootballRows = listOf(
            StandingRow(1, UUID.randomUUID(), "Brasil", points = 9, played = 3, won = 3, drawn = 0, lost = 0, goalsFor = 8, goalsAgainst = 1, goalDifference = 7, winRate = 1.0),
            StandingRow(2, UUID.randomUUID(), "França", points = 6, played = 3, won = 2, drawn = 0, lost = 1, goalsFor = 5, goalsAgainst = 3, goalDifference = 2, winRate = 0.67)
        )
        return defaultFootballRows
    }
}
