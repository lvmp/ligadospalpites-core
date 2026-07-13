package com.ligadospalpites.sportsfeed.infrastructure.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ApiFootballClient(
    @Value("\${app.sportsfeed.api-football.url}") private val baseUrl: String,
    @Value("\${app.sportsfeed.api-football.api-key}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(ApiFootballClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        })
        .defaultHeader("x-apisports-key", apiKey)
        .build()

    fun fetchMatches(leagueId: Int = 1, season: Int = 2026): List<ApiFootballFixtureWrapper> {
        logger.info("Fetching matches from API-Football for league: $leagueId, season: $season")
        return try {
            val response = restClient.get()
                .uri("/v3/fixtures?league=$leagueId&season=$season")
                .retrieve()
                .body(ApiFootballResponse::class.java)
            response?.response ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with API-Football API: ${e.message}", e)
            throw e
        }
    }
}

data class ApiFootballResponse(
    val response: List<ApiFootballFixtureWrapper> = emptyList()
)

data class ApiFootballFixtureWrapper(
    val fixture: ApiFootballFixture,
    val teams: ApiFootballTeams,
    val goals: ApiFootballGoals
)

data class ApiFootballFixture(
    val id: Long,
    val date: String,
    val status: ApiFootballStatus
)

data class ApiFootballStatus(
    val short: String
)

data class ApiFootballTeams(
    val home: ApiFootballTeam,
    val away: ApiFootballTeam
)

data class ApiFootballTeam(
    val name: String
)

data class ApiFootballGoals(
    val home: Int? = null,
    val away: Int? = null
)
