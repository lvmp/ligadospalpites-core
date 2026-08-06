package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
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
    val phase: String
)

@Component
class EspnBasketballClient(
    @Value("\${app.sportsfeed.espn.basketball.url:https://site.api.espn.com/apis/site/v2/sports/basketball}") private val baseUrl: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(EspnBasketballClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        })
        .build()

    fun fetchNbaScoreboard(): List<EspnBasketballGame> {
        return fetchScoreboardForSport("nba")
    }

    fun fetchNbaStandings(): List<com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow> {
        logger.info("Fetching official NBA Standings from ESPN Public API")
        return try {
            val response = restClient.get()
                .uri("/nba/standings")
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
                val confName = conf.name ?: "NBA"
                val entries = conf.standings?.entries ?: emptyList()
                entries.forEachIndexed { idx, entry ->
                    val teamName = entry.team?.displayName ?: entry.team?.name ?: "Time"
                    val logo = entry.team?.logo ?: entry.team?.logos?.firstOrNull()?.href
                    val statMap = (entry.stats ?: emptyList()).associate { (it.name ?: "") to (it.displayValue ?: "") }
                    val wins = statMap["wins"]?.toIntOrNull() ?: 0
                    val losses = statMap["losses"]?.toIntOrNull() ?: 0
                    val winPercentStr = statMap["winPercent"]
                    val winRate = winPercentStr?.toDoubleOrNull() ?: if (wins + losses > 0) Math.round((wins.toDouble() / (wins + losses)) * 1000.0) / 1000.0 else 0.0
                    val gb = statMap["gamesBehind"] ?: "-"
                    val streak = statMap["streak"] ?: ""

                    rows.add(
                        com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow(
                            position = idx + 1,
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
            logger.error("Failed to fetch ESPN NBA standings: ${e.message}", e)
            emptyList()
        }
    }

    fun fetchWnbaScoreboard(): List<EspnBasketballGame> {
        return fetchScoreboardForSport("wnba")
    }

    fun fetchNcaaScoreboard(): List<EspnBasketballGame> {
        return fetchScoreboardForSport("mens-college-basketball")
    }

    fun fetchScoreboardForSport(leagueSlug: String): List<EspnBasketballGame> {
        logger.info("Fetching basketball scoreboard from ESPN Public API for slug: $leagueSlug")
        return try {
            val response = restClient.get()
                .uri("/$leagueSlug/scoreboard")
                .retrieve()
                .body(EspnScoreboardResponse::class.java)

            val games = response?.events?.mapNotNull { parseEspnEvent(it, leagueSlug) } ?: emptyList()
            logger.info("ESPN Public API returned ${games.size} games for $leagueSlug")
            games
        } catch (e: Exception) {
            logger.error("Failed to fetch ESPN basketball scoreboard for $leagueSlug: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseEspnEvent(event: EspnEvent, leagueSlug: String): EspnBasketballGame? {
        val competition = event.competitions?.firstOrNull() ?: return null
        val homeCompetitor = competition.competitors?.find { it.homeAway == "home" } ?: return null
        val awayCompetitor = competition.competitors.find { it.homeAway == "away" } ?: return null

        val homeName = homeCompetitor.team?.displayName ?: homeCompetitor.team?.name ?: "Home"
        val awayName = awayCompetitor.team?.displayName ?: awayCompetitor.team?.name ?: "Away"

        val homeLogo = homeCompetitor.team?.logo ?: homeCompetitor.team?.logos?.firstOrNull()?.href
        val awayLogo = awayCompetitor.team?.logo ?: awayCompetitor.team?.logos?.firstOrNull()?.href

        val homeScore = homeCompetitor.score?.toIntOrNull()
        val awayScore = awayCompetitor.score?.toIntOrNull()

        val statusShort = event.status?.type?.shortDetail ?: event.status?.type?.state ?: "SCHEDULED"

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

        val phaseName = when (leagueSlug.lowercase()) {
            "nba" -> "NBA"
            "wnba" -> "WNBA"
            "mens-college-basketball" -> "NCAA"
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
            phase = phaseName
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnScoreboardResponse(
    val events: List<EspnEvent>? = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class EspnEvent(
    val id: String = "",
    val date: String? = null,
    val status: EspnStatus? = null,
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
