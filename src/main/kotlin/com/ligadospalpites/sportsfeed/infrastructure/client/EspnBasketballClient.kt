package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.ligadospalpites.sportsfeed.domain.models.NbaTeamAliasResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

data class PeriodScoresDto(
    val home: List<Int> = emptyList(),
    val away: List<Int> = emptyList(),
    val homeOvertime: Int = 0,
    val awayOvertime: Int = 0
)

data class EspnBasketballGame(
    val externalId: String,
    val date: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeTeamLogoUrl: String?,
    val awayTeamLogoUrl: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val statusShort: String,
    val periodScoresJson: String?,
    val phase: String,
    val seasonType: Int = 2
)

@Component
class EspnBasketballClient(
    @Value("\${app.sportsfeed.espn.basketball.url:https://site.api.espn.com/apis/site/v2/sports/basketball}") private val baseUrl: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(EspnBasketballClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
        .defaultHeader("Accept", "application/json, text/plain, */*")
        .defaultHeader("Accept-Language", "en-US,en;q=0.9")
        .defaultHeader("Referer", "https://www.espn.com/")
        .defaultHeader("Origin", "https://www.espn.com")
        .defaultHeader("Connection", "keep-alive")
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        })
        .build()

    fun fetchNbaScoreboard(): List<EspnBasketballGame> {
        return fetchScoreboardForSport("nba")
    }

    fun fetchNbaStandings(): List<com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow> {
        val standingsBaseUrl = baseUrl.replace("/apis/site/v2/", "/apis/v2/").trimEnd('/')
        val standingsUri = "$standingsBaseUrl/nba/standings"
        logger.info("Fetching official NBA Standings from ESPN Public API: $standingsUri")
        return try {
            val response = restClient.get()
                .uri(standingsUri)
                .retrieve()
                .body(EspnNbaStandingsResponse::class.java)

            val rows = mutableListOf<com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow>()

            fun collectConferences(confList: List<EspnNbaConference>?): List<EspnNbaConference> {
                val list = mutableListOf<EspnNbaConference>()
                confList?.forEach { conf ->
                    if (!conf.standings?.entries.isNullOrEmpty()) {
                        list.add(conf)
                    }
                    conf.children?.let { list.addAll(collectConferences(it)) }
                }
                return list
            }

            val conferences = collectConferences(response?.children)
            conferences.forEach { conf ->
                val rawConfName = conf.name ?: "NBA"
                val confName = when {
                    rawConfName.contains("East", ignoreCase = true) -> NbaTeamAliasResolver.EASTERN_CONFERENCE
                    rawConfName.contains("West", ignoreCase = true) -> NbaTeamAliasResolver.WESTERN_CONFERENCE
                    else -> rawConfName
                }
                val entries = conf.standings?.entries ?: emptyList()
                entries.forEachIndexed { idx, entry ->
                    val rawTeamName = entry.team?.displayName ?: entry.team?.name ?: "Time"
                    val teamName = NbaTeamAliasResolver.canonicalName(rawTeamName)
                    val logo = entry.team?.logo ?: entry.team?.logos?.firstOrNull()?.href
                    val statMap = (entry.stats ?: emptyList()).associate { (it.name ?: "") to (it.displayValue ?: "") }
                    val wins = statMap["wins"]?.toIntOrNull() ?: 0
                    val losses = statMap["losses"]?.toIntOrNull() ?: 0
                    val winPercentStr = statMap["winPercent"]
                    val winRate = winPercentStr?.toDoubleOrNull() ?: if (wins + losses > 0) Math.round((wins.toDouble() / (wins + losses)) * 1000.0) / 1000.0 else 0.0
                    val gb = statMap["gamesBehind"] ?: "-"
                    val streak = statMap["streak"] ?: ""
                    val position = statMap["playoffSeed"]?.toDoubleOrNull()?.toInt() ?: (idx + 1)

                    rows.add(
                        com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow(
                            position = position,
                            teamId = java.util.UUID.nameUUIDFromBytes(teamName.toByteArray()),
                            teamName = teamName,
                            played = wins + losses,
                            won = wins,
                            lost = losses,
                            winRate = winRate,
                            gamesBehind = gb,
                            streak = streak,
                            groupName = confName,
                            teamLogoUrl = logo
                        )
                    )
                }
            }
            logger.info("ESPN Public API returned ${rows.size} standing rows for NBA")
            rows
        } catch (e: Exception) {
            logger.warn("Could not fetch ESPN NBA standings: ${e.message}")
            emptyList()
        }
    }

    fun fetchNbaScoreboard(dates: String? = null): List<EspnBasketballGame> {
        return fetchScoreboardForSport("nba", dates)
    }

    fun fetchWnbaScoreboard(dates: String? = null): List<EspnBasketballGame> {
        return fetchScoreboardForSport("wnba", dates)
    }

    fun fetchNcaaScoreboard(dates: String? = null): List<EspnBasketballGame> {
        return fetchScoreboardForSport("mens-college-basketball", dates)
    }

    fun fetchScoreboardForSport(leagueSlug: String, dates: String? = null): List<EspnBasketballGame> {
        val uri = if (!dates.isNullOrBlank()) "/$leagueSlug/scoreboard?dates=$dates&limit=500" else "/$leagueSlug/scoreboard?limit=100"
        logger.info("Fetching basketball scoreboard from ESPN Public API for slug: $leagueSlug with uri: $uri")
        return try {
            val response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(EspnScoreboardResponse::class.java)

            val games = response?.events?.mapNotNull { parseEspnEvent(it, leagueSlug) } ?: emptyList()
            logger.info("ESPN Public API returned ${games.size} games for $leagueSlug")
            games
        } catch (e: Exception) {
            logger.warn("Could not fetch ESPN basketball scoreboard for $leagueSlug: ${e.message}")
            emptyList()
        }
    }

    private fun parseEspnEvent(event: EspnEvent, leagueSlug: String): EspnBasketballGame? {
        val competition = event.competitions?.firstOrNull() ?: return null
        val homeCompetitor = competition.competitors?.find { it.homeAway == "home" } ?: return null
        val awayCompetitor = competition.competitors.find { it.homeAway == "away" } ?: return null

        val rawHomeName = homeCompetitor.team?.displayName ?: homeCompetitor.team?.name ?: "Home"
        val rawAwayName = awayCompetitor.team?.displayName ?: awayCompetitor.team?.name ?: "Away"
        val homeName = if (leagueSlug == "nba") NbaTeamAliasResolver.canonicalName(rawHomeName) else rawHomeName
        val awayName = if (leagueSlug == "nba") NbaTeamAliasResolver.canonicalName(rawAwayName) else rawAwayName

        val homeLogo = homeCompetitor.team?.logo ?: homeCompetitor.team?.logos?.firstOrNull()?.href
        val awayLogo = awayCompetitor.team?.logo ?: awayCompetitor.team?.logos?.firstOrNull()?.href

        val homeScore = homeCompetitor.score?.toIntOrNull()
        val awayScore = awayCompetitor.score?.toIntOrNull()

        val state = event.status?.type?.state?.lowercase()
        val shortDetail = event.status?.type?.shortDetail ?: ""
        val isLive = state == "in" ||
                     shortDetail.contains("1st", ignoreCase = true) ||
                     shortDetail.contains("2nd", ignoreCase = true) ||
                     shortDetail.contains("3rd", ignoreCase = true) ||
                     shortDetail.contains("4th", ignoreCase = true) ||
                     shortDetail.contains("half", ignoreCase = true) ||
                     shortDetail.contains("ot", ignoreCase = true)

        val isFinished = state == "post" || shortDetail.contains("final", ignoreCase = true)

        val statusShort = when {
            isFinished -> "FINAL"
            isLive -> "LIVE"
            else -> shortDetail.ifBlank { "SCHEDULED" }
        }

        // Build period scores breakdown
        val homeQuarterScores = homeCompetitor.linescores?.mapNotNull { it.value?.toInt() } ?: emptyList()
        val awayQuarterScores = awayCompetitor.linescores?.mapNotNull { it.value?.toInt() } ?: emptyList()

        val homeQ = homeQuarterScores.take(4)
        val awayQ = awayQuarterScores.take(4)

        val homeOt = if (homeQuarterScores.size > 4) homeQuarterScores.drop(4).sum() else 0
        val awayOt = if (awayQuarterScores.size > 4) awayQuarterScores.drop(4).sum() else 0

        val periodScores = PeriodScoresDto(
            home = homeQ,
            away = awayQ,
            homeOvertime = homeOt,
            awayOvertime = awayOt
        )

        val periodScoresJsonString = try {
            objectMapper.writeValueAsString(periodScores)
        } catch (e: Exception) {
            null
        }

        val seasonType = event.season?.type ?: 2
        val phaseName = when {
            seasonType == 1 -> "Pré-Temporada"
            seasonType == 3 -> "Playoffs"
            else -> "Temporada Regular"
        }

        return EspnBasketballGame(
            externalId = event.id,
            date = event.date ?: "",
            homeTeamName = homeName,
            awayTeamName = awayName,
            homeTeamLogoUrl = homeLogo,
            awayTeamLogoUrl = awayLogo,
            homeScore = homeScore,
            awayScore = awayScore,
            statusShort = statusShort,
            periodScoresJson = periodScoresJsonString,
            phase = phaseName,
            seasonType = seasonType
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnScoreboardResponse(
    val events: List<EspnEvent>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnSeason(
    val type: Int? = 2
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnEvent(
    val id: String = "",
    val date: String? = null,
    val status: EspnStatus? = null,
    val season: EspnSeason? = null,
    val competitions: List<EspnCompetition>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnStatus(
    val type: EspnStatusType? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnStatusType(
    val state: String? = null,
    val shortDetail: String? = null,
    val completed: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnCompetition(
    val competitors: List<EspnCompetitor>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnCompetitor(
    val homeAway: String = "",
    val score: String? = null,
    val team: EspnTeam? = EspnTeam(),
    val linescores: List<EspnLinescore>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnTeam(
    val name: String? = null,
    val displayName: String? = null,
    val logo: String? = null,
    val logos: List<EspnLogo>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnLogo(
    val href: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnLinescore(
    val value: Double? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnNbaStandingsResponse(
    val children: List<EspnNbaConference>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnNbaConference(
    val name: String? = null,
    val standings: EspnNbaStandingsWrapper? = null,
    val children: List<EspnNbaConference>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnNbaStandingsWrapper(
    val entries: List<EspnNbaEntry>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnNbaEntry(
    val team: EspnTeam? = null,
    val stats: List<EspnNbaStat>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnNbaStat(
    val name: String? = null,
    val displayValue: String? = null
)
