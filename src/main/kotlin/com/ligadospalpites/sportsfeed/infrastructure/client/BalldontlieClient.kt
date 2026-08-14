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
