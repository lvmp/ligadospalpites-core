package com.ligadospalpites.sportsfeed.infrastructure.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class EspnSoccerClient(
    @Value("\${app.sportsfeed.espn-soccer.url:https://site.api.espn.com}") private val baseUrl: String
) {
    private val logger = LoggerFactory.getLogger(EspnSoccerClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(15000)
        })
        .build()

    fun fetchLibertadoresMatches(seasonYear: Int = 2026): List<EspnSoccerEvent> {
        val dateRange = "${seasonYear}0101-${seasonYear}1231"
        logger.info("Fetching full-season Libertadores matches from ESPN Public API (season: $seasonYear, dates: $dateRange)")
        return try {
            val response = restClient.get()
                .uri("/apis/site/v2/sports/soccer/conmebol.libertadores/scoreboard?dates=$dateRange&limit=500")
                .retrieve()
                .body(EspnSoccerResponse::class.java)
            response?.events ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with ESPN Soccer API: ${e.message}", e)
            throw e
        }
    }
}

data class EspnSoccerResponse(
    val events: List<EspnSoccerEvent> = emptyList()
)

data class EspnSoccerEvent(
    val id: String,
    val date: String,
    val name: String? = null,
    val shortName: String? = null,
    val competitions: List<EspnSoccerCompetition> = emptyList()
)

data class EspnSoccerCompetition(
    val id: String,
    val date: String,
    val status: EspnSoccerStatus? = null,
    val notes: List<EspnSoccerNote> = emptyList(),
    val competitors: List<EspnSoccerCompetitor> = emptyList()
)

data class EspnSoccerNote(
    val type: String? = null,
    val headline: String? = null
)

data class EspnSoccerStatus(
    val type: EspnSoccerStatusType? = null
)

data class EspnSoccerStatusType(
    val id: String? = null,
    val name: String? = null,
    val state: String? = null, // "pre", "in", "post"
    val completed: Boolean = false,
    val description: String? = null,
    val detail: String? = null,
    val shortDetail: String? = null
)

data class EspnSoccerCompetitor(
    val id: String,
    val homeAway: String, // "home" or "away"
    val winner: Boolean? = false,
    val team: EspnSoccerTeam,
    val score: String? = null
)

data class EspnSoccerTeam(
    val id: String,
    val name: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val logo: String? = null
)
