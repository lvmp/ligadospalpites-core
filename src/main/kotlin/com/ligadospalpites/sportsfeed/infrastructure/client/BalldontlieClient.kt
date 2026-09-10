package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@JsonIgnoreProperties(ignoreUnknown = true)
data class BalldontlieTeamData(
    val id: Int = 0,
    @JsonProperty("full_name") val fullName: String = "",
    val name: String = "",
    val abbreviation: String = "",
    val city: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BalldontlieGameData(
    val id: Int = 0,
    val date: String = "",
    val datetime: String? = null,
    val season: Int = 0,
    val status: String? = null,
    val period: Int? = null,
    val time: String? = null,
    val postseason: Boolean = false,
    @JsonProperty("home_team_score") val homeTeamScore: Int? = null,
    @JsonProperty("visitor_team_score") val visitorTeamScore: Int? = null,
    @JsonProperty("home_team") val homeTeam: BalldontlieTeamData = BalldontlieTeamData(),
    @JsonProperty("visitor_team") val visitorTeam: BalldontlieTeamData = BalldontlieTeamData()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BalldontlieGamesResponse(
    val data: List<BalldontlieGameData> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BalldontlieStandingItem(
    val team: BalldontlieTeamData = BalldontlieTeamData(),
    val conference: String = "",
    val wins: Int = 0,
    val losses: Int = 0,
    @JsonProperty("win_percentage") val winPercentage: Double? = null,
    @JsonProperty("games_behind") val gamesBehind: String? = null,
    val streak: String? = null,
    @JsonProperty("conference_rank") val conferenceRank: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BalldontlieStandingsResponse(
    val data: List<BalldontlieStandingItem> = emptyList()
)

data class BalldontlieNbaGame(
    val externalId: String,
    val date: String,
    val homeTeamName: String,
    val awayTeamName: String,
    val homeTeamLogoUrl: String?,
    val awayTeamLogoUrl: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val statusShort: String,
    val phase: String
)

@Component
class BalldontlieClient(
    @Value("\${app.sportsfeed.balldontlie.url:https://api.balldontlie.io}") private val baseUrl: String,
    @Value("\${app.sportsfeed.balldontlie.api-key:}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(BalldontlieClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("Authorization", apiKey)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        })
        .build()

    @CircuitBreaker(name = "balldontlieApi", fallbackMethod = "fetchNbaGamesFallback")
    @Retry(name = "balldontlieApi")
    fun fetchNbaGames(): List<BalldontlieNbaGame> {
        logger.info("Fetching NBA games from balldontlie.io API (/nba/v1/games)")
        val response = restClient.get()
            .uri("/nba/v1/games")
            .retrieve()
            .body(BalldontlieGamesResponse::class.java)

        val rawGames = response?.data ?: emptyList()
        logger.info("Successfully fetched ${rawGames.size} NBA games from balldontlie.io")

        return rawGames.map { game ->
            BalldontlieNbaGame(
                externalId = game.id.toString(),
                date = game.datetime ?: if (game.date.contains("T")) game.date else "${game.date}T00:00:00Z",
                homeTeamName = game.homeTeam.fullName.ifBlank { game.homeTeam.name },
                awayTeamName = game.visitorTeam.fullName.ifBlank { game.visitorTeam.name },
                homeTeamLogoUrl = null,
                awayTeamLogoUrl = null,
                homeScore = game.homeTeamScore,
                awayScore = game.visitorTeamScore,
                statusShort = mapBalldontlieStatus(game.status),
                phase = if (game.postseason) "Playoffs" else "Temporada Regular"
            )
        }
    }

    fun fetchStandings(season: Int = 2026): List<com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow> {
        logger.info("Fetching NBA Standings fallback from balldontlie.io API (/nba/v1/standings)")
        return try {
            val response = restClient.get()
                .uri("/nba/v1/standings?season=$season")
                .retrieve()
                .body(BalldontlieStandingsResponse::class.java)

            val rawList = response?.data ?: emptyList()
            rawList.mapIndexed { idx, s ->
                val teamName = s.team.fullName.ifBlank { s.team.name }
                val conf = when {
                    s.conference.startsWith("East", ignoreCase = true) -> "Eastern Conference"
                    s.conference.startsWith("West", ignoreCase = true) -> "Western Conference"
                    else -> s.conference.ifBlank { "NBA" }
                }
                com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow(
                    position = s.conferenceRank ?: (idx + 1),
                    teamId = java.util.UUID.nameUUIDFromBytes(teamName.toByteArray()),
                    teamName = teamName,
                    played = s.wins + s.losses,
                    won = s.wins,
                    lost = s.losses,
                    winRate = s.winPercentage ?: if (s.wins + s.losses > 0) Math.round((s.wins.toDouble() / (s.wins + s.losses)) * 1000.0) / 1000.0 else 0.0,
                    gamesBehind = s.gamesBehind ?: "-",
                    streak = s.streak ?: "-",
                    groupName = conf
                )
            }
        } catch (e: Exception) {
            logger.warn("Balldontlie standings API failed: ${e.message}")
            emptyList()
        }
    }

    fun fetchNbaGamesFallback(e: Throwable): List<BalldontlieNbaGame> {
        logger.error("Balldontlie API call failed, activating fallback: ${e.message}")
        return emptyList()
    }

    private fun mapBalldontlieStatus(status: String?): String {
        if (status.isNullOrBlank()) return "NS"
        val upper = status.uppercase()
        return when {
            upper.contains("FINAL") -> "FT"
            upper.contains("1ST") || upper.contains("2ND") || upper.contains("3RD") || upper.contains("4TH") || upper.contains("HALF") || upper.contains("OT") -> "IN_PROGRESS"
            else -> "NS"
        }
    }
}
