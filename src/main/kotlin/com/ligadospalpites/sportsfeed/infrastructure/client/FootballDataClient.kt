package com.ligadospalpites.sportsfeed.infrastructure.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant

@Component
class FootballDataClient(
    @Value("\${app.sportsfeed.football-data.url}") private val baseUrl: String,
    @Value("\${app.sportsfeed.football-data.api-key}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(FootballDataClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        })
        .defaultHeader("X-Auth-Token", apiKey)
        .build()

    fun fetchMatches(competitionCode: String = "WC"): List<FootballDataMatch> {
        logger.info("Fetching matches from Football-Data API for competition: $competitionCode")
        return try {
            val response = restClient.get()
                .uri("/v4/competitions/$competitionCode/matches")
                .retrieve()
                .body(FootballDataResponse::class.java)
            response?.matches ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with Football-Data API: ${e.message}", e)
            throw e
        }
    }
}

data class FootballDataResponse(
    val matches: List<FootballDataMatch> = emptyList()
)

data class FootballDataMatch(
    val id: Long,
    val utcDate: String,
    val status: String,
    val stage: String,
    val homeTeam: FootballDataTeam,
    val awayTeam: FootballDataTeam,
    val score: FootballDataScore? = null
)

data class FootballDataTeam(
    val id: Long? = null,
    val name: String? = null,
    val shortName: String? = null
)

data class FootballDataScore(
    val fullTime: FootballDataTeamScore? = null
)

data class FootballDataTeamScore(
    val home: Int? = null,
    val away: Int? = null
)
