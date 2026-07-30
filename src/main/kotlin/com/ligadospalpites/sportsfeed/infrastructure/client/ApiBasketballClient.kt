package com.ligadospalpites.sportsfeed.infrastructure.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ApiBasketballClient(
    @Value("\${app.sportsfeed.api-basketball.url}") private val baseUrl: String,
    @Value("\${app.sportsfeed.api-basketball.api-key}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(ApiBasketballClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        })
        .defaultHeader("x-apisports-key", apiKey)
        .build()

    fun fetchGames(leagueId: Int, season: Int = 2026): List<ApiBasketballGameWrapper> {
        return fetchGames(leagueId = leagueId, seasonParam = season.toString())
    }

    fun fetchGames(leagueId: Int, seasonParam: String): List<ApiBasketballGameWrapper> {
        val seasonYearInt = seasonParam.take(4).toIntOrNull() ?: 2026
        val candidateSeasons = listOf(
            seasonParam,
            "${seasonYearInt - 1}-$seasonYearInt",
            "$seasonYearInt-${seasonYearInt + 1}",
            "$seasonYearInt"
        ).distinct()

        for (candidate in candidateSeasons) {
            logger.info("Fetching basketball games from API-Basketball for league: $leagueId, season: $candidate")
            try {
                val response = restClient.get()
                    .uri("/games?league=$leagueId&season=$candidate")
                    .retrieve()
                    .body(ApiBasketballResponse::class.java)
                val games = response?.response ?: emptyList()
                logger.info("API-Basketball response for league $leagueId, season $candidate: ${games.size} games retrieved.")
                if (games.isNotEmpty()) {
                    return games
                }
            } catch (e: Exception) {
                logger.error("Error communicating with API-Basketball API for season $candidate: ${e.message}", e)
                if (candidate == candidateSeasons.last()) {
                    throw e
                }
            }
        }
        return emptyList()
    }
}

data class ApiBasketballResponse(
    val response: List<ApiBasketballGameWrapper> = emptyList()
)

data class ApiBasketballGameWrapper(
    val date: String,
    val stage: String? = null,
    val status: ApiBasketballStatus,
    val teams: ApiBasketballTeams,
    val scores: ApiBasketballScores
)

data class ApiBasketballStatus(
    val short: String
)

data class ApiBasketballTeams(
    val home: ApiBasketballTeam,
    val away: ApiBasketballTeam
)

data class ApiBasketballTeam(
    val name: String,
    val logo: String? = null
)

data class ApiBasketballScores(
    val home: ApiBasketballTeamScore? = null,
    val away: ApiBasketballTeamScore? = null
)

data class ApiBasketballTeamScore(
    val total: Int? = null
)
