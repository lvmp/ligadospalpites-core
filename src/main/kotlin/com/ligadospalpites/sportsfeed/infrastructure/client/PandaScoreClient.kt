package com.ligadospalpites.sportsfeed.infrastructure.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class PandaScoreClient(
    @Value("\${app.sportsfeed.pandascore.url:https://api.pandascore.co}") private val baseUrl: String,
    @Value("\${app.sportsfeed.pandascore.token:}") private val apiToken: String
) {
    private val logger = LoggerFactory.getLogger(PandaScoreClient::class.java)

    private val restClient: RestClient by lazy {
        val builder = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(5000)
                setReadTimeout(10000)
            })

        if (apiToken.isNotBlank()) {
            builder.defaultHeader("Authorization", "Bearer $apiToken")
        }
        builder.build()
    }

    fun fetchMatches(leagueSlug: String? = null, page: Int = 1, size: Int = 50): List<PandaScoreMatchResponse> {
        if (apiToken.isBlank()) {
            logger.warn("PandaScore API token is empty. Skipping external API call.")
            return emptyList()
        }

        return try {
            val uri = if (!leagueSlug.isNullOrBlank()) {
                "/leagues/$leagueSlug/matches?page[number]=$page&page[size]=$size&sort=-begin_at"
            } else {
                "/matches?page[number]=$page&page[size]=$size&sort=-begin_at"
            }

            logger.info("Fetching eSports matches from PandaScore: $uri")
            val response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(Array<PandaScoreMatchResponse>::class.java)

            response?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with PandaScore API: ${e.message}", e)
            emptyList()
        }
    }

    fun fetchStandings(tournamentSlugOrId: String): List<PandaScoreStandingResponse> {
        if (apiToken.isBlank()) {
            logger.warn("PandaScore API token is empty. Skipping standings API call.")
            return emptyList()
        }

        return try {
            val uri = "/tournaments/$tournamentSlugOrId/standings"
            logger.info("Fetching tournament standings from PandaScore: $uri")
            val response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(Array<PandaScoreStandingResponse>::class.java)

            response?.toList() ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with PandaScore Standings API: ${e.message}", e)
            emptyList()
        }
    }
}

data class PandaScoreStandingResponse(
    val rank: Int,
    val team: PandaScoreTeam,
    val wins: Int? = 0,
    val losses: Int? = 0,
    val ties: Int? = 0,
    val points: Int? = null,
    val matches_played: Int? = 0
)

data class PandaScoreMatchResponse(
    val id: Long,
    val name: String? = null,
    val begin_at: String? = null,
    val status: String? = null,
    val number_of_games: Int? = 1,
    val league: PandaScoreLeague? = null,
    val serie: PandaScoreSerie? = null,
    val opponents: List<PandaScoreOpponentWrapper> = emptyList(),
    val results: List<PandaScoreResult> = emptyList(),
    val streams_list: List<PandaScoreStream> = emptyList()
)

data class PandaScoreLeague(
    val id: Long,
    val name: String,
    val image_url: String? = null,
    val slug: String? = null
)

data class PandaScoreSerie(
    val full_name: String? = null
)

data class PandaScoreOpponentWrapper(
    val opponent: PandaScoreTeam? = null
)

data class PandaScoreTeam(
    val id: Long,
    val name: String,
    val image_url: String? = null,
    val acronym: String? = null
)

data class PandaScoreResult(
    val team_id: Long,
    val score: Int
)

data class PandaScoreStream(
    val raw_url: String? = null,
    val embed_url: String? = null,
    val main: Boolean = false
)
