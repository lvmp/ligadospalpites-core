package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GoalApiSoccerClient(
    @Value("\${app.sportsfeed.goal-api.url:https://goal-api.com/api/v1}") private val baseUrl: String,
    @Value("\${app.sportsfeed.goal-api.api-key:mock-key-goal}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(GoalApiSoccerClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        })
        .defaultHeader("X-API-Key", apiKey)
        .defaultHeader("Authorization", "Bearer $apiKey")
        .build()

    fun fetchFixtures(leagueId: Int, season: Int? = null): List<GoalApiFixture> {
        val uri = if (season != null) {
            "/leagues/$leagueId/fixtures?season=$season"
        } else {
            "/leagues/$leagueId/fixtures"
        }
        logger.info("Fetching fixtures from GOAL API for leagueId: $leagueId, season: ${season ?: "all"}")
        return try {
            val response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(object : ParameterizedTypeReference<GoalApiResponse<List<GoalApiFixture>>>() {})

            response?.data ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with GOAL API for leagueId $leagueId: ${e.message}", e)
            throw e
        }
    }

    /**
     * Busca confrontos da Copa do Brasil na GOAL API (League ID: 349)
     */
    fun fetchCopaDoBrasilFixtures(seasonYear: Int = 2026): List<GoalApiFixture> {
        return fetchFixtures(leagueId = 349, season = seasonYear)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoalApiResponse<T>(
    val success: Boolean = true,
    val data: T? = null,
    val pagination: GoalApiPagination? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoalApiPagination(
    val page: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
    val totalPages: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoalApiFixture(
    val id: Long? = null,
    val leagueId: Int? = null,
    val season: Int? = null,
    val round: String? = null,
    val stage: String? = null,
    val status: String? = null,
    val kickoffUtc: String? = null,
    val matchDate: String? = null,
    val matchTime: String? = null,
    val homeTeam: GoalApiTeam? = null,
    val awayTeam: GoalApiTeam? = null,
    val score: GoalApiScore? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoalApiTeam(
    val id: Long? = null,
    val name: String? = null,
    val shortName: String? = null,
    val logo: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoalApiScore(
    val home: Int? = null,
    val away: Int? = null,
    val homeHalfTime: Int? = null,
    val awayHalfTime: Int? = null,
    val penalties: GoalApiPenaltiesScore? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoalApiPenaltiesScore(
    val home: Int? = null,
    val away: Int? = null
)
