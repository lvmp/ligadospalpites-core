package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class StatsNbaResultSet(
    val name: String? = null,
    val headers: List<String> = emptyList(),
    val rowSet: List<List<Any?>> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StatsNbaResponse(
    val resource: String? = null,
    val parameters: Map<String, Any?>? = null,
    val resultSets: List<StatsNbaResultSet> = emptyList()
)

@Component
class StatsNbaClient(
    @Value("\${app.sportsfeed.stats-nba.url:https://stats.nba.com}") private val baseUrl: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(StatsNbaClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .defaultHeader("Accept", "application/json, text/plain, */*")
        .defaultHeader("Accept-Language", "en-US,en;q=0.9")
        .defaultHeader("Referer", "https://www.nba.com/")
        .defaultHeader("Origin", "https://www.nba.com")
        .defaultHeader("Connection", "keep-alive")
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3000)
            setReadTimeout(5000)
        })
        .build()

    @CircuitBreaker(name = "statsNbaApi", fallbackMethod = "fetchStandingsFallback")
    @Retry(name = "statsNbaApi")
    fun fetchStandings(season: String = "2026-27"): List<StandingRow> {
        logger.info("Fetching official NBA Standings from stats.nba.com API for season: $season")
        val response = restClient.get()
            .uri("/stats/leaguestandingsv3?LeagueID=00&Season=$season&SeasonType=Regular+Season")
            .retrieve()
            .body(StatsNbaResponse::class.java)

        val resultSet = response?.resultSets?.find { it.name.equals("Standings", ignoreCase = true) }
            ?: response?.resultSets?.firstOrNull()

        if (resultSet == null || resultSet.headers.isEmpty() || resultSet.rowSet.isEmpty()) {
            logger.warn("Empty resultSets received from stats.nba.com")
            return emptyList()
        }

        val headerMap = resultSet.headers.mapIndexed { idx, name -> name.uppercase() to idx }.toMap()

        val teamIdIdx = headerMap["TEAMID"]
        val teamCityIdx = headerMap["TEAMCITY"]
        val teamNameIdx = headerMap["TEAMNAME"]
        val confIdx = headerMap["CONFERENCE"]
        val winsIdx = headerMap["WINS"]
        val lossesIdx = headerMap["LOSSES"]
        val winPctIdx = headerMap["WINPCT"]
        val confGbIdx = headerMap["CONFERENCEGAMESBEHIND"]
        val streakIdx = headerMap["STRCURRENTSTREAK"] ?: headerMap["STREAK"]
        val confRankIdx = headerMap["CONFERENCERANK"] ?: headerMap["PLAYOFFRANK"]

        val rows = mutableListOf<StandingRow>()

        for (row in resultSet.rowSet) {
            val teamId = if (teamIdIdx != null && teamIdIdx < row.size) row[teamIdIdx]?.toString() else null
            val teamCity = if (teamCityIdx != null && teamCityIdx < row.size) row[teamCityIdx]?.toString() ?: "" else ""
            val teamShortName = if (teamNameIdx != null && teamNameIdx < row.size) row[teamNameIdx]?.toString() ?: "" else ""
            val fullTeamName = if (teamCity.isNotBlank() && teamShortName.isNotBlank()) "$teamCity $teamShortName".trim() else teamShortName

            val confRaw = if (confIdx != null && confIdx < row.size) row[confIdx]?.toString() ?: "" else ""
            val groupName = when {
                confRaw.startsWith("East", ignoreCase = true) -> "Eastern Conference"
                confRaw.startsWith("West", ignoreCase = true) -> "Western Conference"
                else -> confRaw.ifBlank { "NBA" }
            }

            val wins = if (winsIdx != null && winsIdx < row.size) (row[winsIdx] as? Number)?.toInt() ?: row[winsIdx]?.toString()?.toIntOrNull() ?: 0 else 0
            val losses = if (lossesIdx != null && lossesIdx < row.size) (row[lossesIdx] as? Number)?.toInt() ?: row[lossesIdx]?.toString()?.toIntOrNull() ?: 0 else 0
            val winRate = if (winPctIdx != null && winPctIdx < row.size) {
                (row[winPctIdx] as? Number)?.toDouble() ?: row[winPctIdx]?.toString()?.toDoubleOrNull()
            } else null

            val calculatedWinRate = winRate ?: if (wins + losses > 0) Math.round((wins.toDouble() / (wins + losses)) * 1000.0) / 1000.0 else 0.0

            val gbRaw = if (confGbIdx != null && confGbIdx < row.size) row[confGbIdx]?.toString() ?: "-" else "-"
            val gamesBehind = if (gbRaw == "0" || gbRaw == "0.0") "-" else gbRaw

            val streak = if (streakIdx != null && streakIdx < row.size) row[streakIdx]?.toString() ?: "-" else "-"

            val position = if (confRankIdx != null && confRankIdx < row.size) {
                (row[confRankIdx] as? Number)?.toInt() ?: row[confRankIdx]?.toString()?.toIntOrNull() ?: 0
            } else 0

            val logoUrl = if (!teamId.isNullOrBlank()) {
                "https://cdn.nba.com/logos/nba/$teamId/global/L/logo.svg"
            } else null

            rows.add(
                StandingRow(
                    position = position,
                    teamId = UUID.nameUUIDFromBytes(fullTeamName.toByteArray()),
                    teamName = fullTeamName,
                    played = wins + losses,
                    won = wins,
                    lost = losses,
                    winRate = calculatedWinRate,
                    gamesBehind = gamesBehind,
                    streak = streak,
                    groupName = groupName,
                    teamLogoUrl = logoUrl
                )
            )
        }

        // Re-sort and normalize positions by conference
        val normalized = rows.groupBy { it.groupName }.flatMap { (_, confRows) ->
            confRows.sortedWith(
                compareByDescending<StandingRow> { it.winRate ?: 0.0 }
                    .thenByDescending { it.won ?: 0 }
            ).mapIndexed { idx, r ->
                r.copy(position = idx + 1)
            }
        }

        logger.info("stats.nba.com returned ${normalized.size} normalized standing rows for NBA")
        return normalized
    }

    fun fetchStandingsFallback(season: String, e: Throwable): List<StandingRow> {
        logger.warn("stats.nba.com API call failed or timed out (${e.message}), activating fallback chain.")
        return emptyList()
    }
}
