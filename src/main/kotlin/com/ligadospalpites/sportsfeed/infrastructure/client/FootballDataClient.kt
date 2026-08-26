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

    @JvmOverloads
    fun fetchMatches(competitionCode: String = "WC", seasonYear: Int? = null): List<FootballDataMatch> {
        val uriStr = if (seasonYear != null) {
            "/v4/competitions/$competitionCode/matches?season=$seasonYear"
        } else {
            "/v4/competitions/$competitionCode/matches"
        }
        logger.info("Fetching matches from Football-Data API for competition: $competitionCode, season: ${seasonYear ?: "default"}")
        return try {
            val response = restClient.get()
                .uri(uriStr)
                .retrieve()
                .body(FootballDataResponse::class.java)
            response?.matches ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with Football-Data API: ${e.message}", e)
            throw e
        }
    }

    fun fetchStandings(competitionCode: String): FootballDataStandingsResponse? {
        logger.info("Fetching standings from Football-Data API for competition: $competitionCode")
        return try {
            restClient.get()
                .uri("/v4/competitions/$competitionCode/standings")
                .retrieve()
                .body(FootballDataStandingsResponse::class.java)
        } catch (e: Exception) {
            logger.error("Error fetching standings from Football-Data API: ${e.message}", e)
            null
        }
    }
}

data class FootballDataStandingsResponse(
    val competition: FootballDataCompetition? = null,
    val season: FootballDataSeasonInfo? = null,
    val standings: List<FootballDataStandingGroup> = emptyList()
)

data class FootballDataCompetition(
    val id: Long? = null,
    val name: String? = null,
    val code: String? = null,
    val emblem: String? = null
)

data class FootballDataSeasonInfo(
    val id: Long? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val currentMatchday: Int? = null
)

data class FootballDataStandingGroup(
    val stage: String? = null,
    val type: String? = null,
    val group: String? = null,
    val table: List<FootballDataStandingRow> = emptyList()
)

data class FootballDataStandingRow(
    val position: Int,
    val team: FootballDataTeam,
    val playedGames: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val points: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int
)


data class FootballDataResponse(
    val matches: List<FootballDataMatch> = emptyList()
)

data class FootballDataMatch(
    val id: Long,
    val utcDate: String,
    val status: String,
    val stage: String,
    val matchday: Int? = null,
    val homeTeam: FootballDataTeam,
    val awayTeam: FootballDataTeam,
    val score: FootballDataScore? = null
)

data class FootballDataTeam(
    val id: Long? = null,
    val name: String? = null,
    val shortName: String? = null,
    val crest: String? = null
)

data class FootballDataScore(
    val fullTime: FootballDataTeamScore? = null
)

data class FootballDataTeamScore(
    val home: Int? = null,
    val away: Int? = null
)
