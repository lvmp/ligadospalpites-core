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
        logger.info("Fetching basketball games from API-Basketball for league: $leagueId, season: $season")
        return try {
            val response = restClient.get()
                .uri("/games?league=$leagueId&season=$season")
                .retrieve()
                .body(ApiBasketballResponse::class.java)
            response?.response ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with API-Basketball API: ${e.message}", e)
            throw e
        }
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
