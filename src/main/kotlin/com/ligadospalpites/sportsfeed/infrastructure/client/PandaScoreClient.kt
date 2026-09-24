package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

@Component
class PandaScoreClient(
    @Value("\${app.sportsfeed.pandascore.url:https://api.pandascore.co}") private val baseUrl: String,
    @Value("\${app.sportsfeed.pandascore.token:}") private val apiToken: String
) {
    private val logger = LoggerFactory.getLogger(PandaScoreClient::class.java)
    private val leagueIdCache = ConcurrentHashMap<String, Long>()

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

    fun resolveLeagueId(searchTerm: String): Long? {
        if (apiToken.isBlank() || searchTerm.isBlank()) return null
        val key = searchTerm.lowercase().trim()
        val cached = leagueIdCache[key]
        if (cached != null) return cached

        return try {
            val encoded = URLEncoder.encode(searchTerm.trim(), StandardCharsets.UTF_8)
            val uri = "/leagues?token=$apiToken&search[name]=$encoded&page[size]=10"
            logger.info("Resolving PandaScore league ID for '$searchTerm': $uri")
            val response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(Array<PandaScoreLeague>::class.java)

            val leagues = response?.toList() ?: emptyList()
            val match = leagues.firstOrNull { it.name.contains(searchTerm.trim(), ignoreCase = true) }
                ?: leagues.firstOrNull()
            val id = match?.id
            if (id != null) {
                logger.info("Resolved PandaScore league '$searchTerm' to ID: $id (${match.name})")
                leagueIdCache[key] = id
            } else {
                logger.warn("Could not find any PandaScore league matching search term '$searchTerm'")
            }
            id
        } catch (e: Exception) {
            logger.warn("Failed to resolve PandaScore league ID for '$searchTerm': ${e.message}")
            null
        }
    }

    fun fetchMatches(
        leagueIdOrSlug: String? = null,
        videogameSlug: String? = null,
        searchTerm: String? = null,
        page: Int = 1,
        size: Int = 50
    ): List<PandaScoreMatchResponse> {
        if (apiToken.isBlank()) {
            logger.warn("PandaScore API token is empty. Skipping external API call.")
            return emptyList()
        }

        // 1. Determine target league ID (numeric if resolved or provided)
        var targetLeagueIdentifier = leagueIdOrSlug
        if (targetLeagueIdentifier.isNullOrBlank() && !searchTerm.isNullOrBlank()) {
            val resolvedId = resolveLeagueId(searchTerm)
            if (resolvedId != null) {
                targetLeagueIdentifier = resolvedId.toString()
            }
        }

        // 2. Try fetching from league endpoint if we have an identifier
        if (!targetLeagueIdentifier.isNullOrBlank()) {
            try {
                val uri = "/leagues/$targetLeagueIdentifier/matches?token=$apiToken&page[number]=$page&page[size]=$size&sort=-begin_at"
                logger.info("Fetching eSports matches from PandaScore league endpoint: $uri")
                val response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(Array<PandaScoreMatchResponse>::class.java)

                val matches = response?.toList() ?: emptyList()
                if (matches.isNotEmpty()) {
                    return matches
                }
            } catch (e: Exception) {
                logger.warn("Could not fetch matches for league '$targetLeagueIdentifier' from PandaScore: ${e.message}. Attempting fallback...")
            }
        }

        // 3. Fallback to /matches?filter[videogame]=... or /matches
        return try {
            val baseEndpoint = if (!videogameSlug.isNullOrBlank()) {
                "/matches?filter[videogame]=$videogameSlug&token=$apiToken&page[number]=$page&page[size]=$size&sort=-begin_at"
            } else {
                "/matches?token=$apiToken&page[number]=$page&page[size]=$size&sort=-begin_at"
            }
            logger.info("Fetching eSports matches from PandaScore fallback endpoint: $baseEndpoint")
            val pastResponse = restClient.get()
                .uri(baseEndpoint)
                .retrieve()
                .body(Array<PandaScoreMatchResponse>::class.java)
                ?.toList() ?: emptyList()

            // Also check upcoming matches
            val upcomingEndpoint = if (!videogameSlug.isNullOrBlank()) {
                "/matches/upcoming?filter[videogame]=$videogameSlug&token=$apiToken&page[size]=25&sort=begin_at"
            } else {
                "/matches/upcoming?token=$apiToken&page[size]=25&sort=begin_at"
            }
            val upcomingResponse = try {
                restClient.get()
                    .uri(upcomingEndpoint)
                    .retrieve()
                    .body(Array<PandaScoreMatchResponse>::class.java)
                    ?.toList() ?: emptyList()
            } catch (e: Exception) {
                logger.warn("Could not fetch upcoming matches from $upcomingEndpoint: ${e.message}")
                emptyList()
            }

            (upcomingResponse + pastResponse).distinctBy { it.id }
        } catch (e: Exception) {
            logger.error("Error communicating with PandaScore fallback API: ${e.message}", e)
            emptyList()
        }
    }

    fun fetchStandings(tournamentSlugOrId: String): List<PandaScoreStandingResponse> {
        if (apiToken.isBlank()) {
            logger.warn("PandaScore API token is empty. Skipping standings API call.")
            return emptyList()
        }

        return try {
            val uri = "/tournaments/$tournamentSlugOrId/standings?token=$apiToken"
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreStandingResponse(
    val rank: Int,
    val team: PandaScoreTeam,
    val wins: Int? = 0,
    val losses: Int? = 0,
    val ties: Int? = 0,
    val points: Int? = null,
    val matches_played: Int? = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreMatchResponse(
    val id: Long,
    val name: String? = null,
    val begin_at: String? = null,
    val status: String? = null,
    val number_of_games: Int? = 1,
    val league: PandaScoreLeague? = null,
    val serie: PandaScoreSerie? = null,
    val videogame: PandaScoreVideogame? = null,
    val opponents: List<PandaScoreOpponentWrapper> = emptyList(),
    val results: List<PandaScoreResult> = emptyList(),
    val streams_list: List<PandaScoreStream> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreVideogame(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreLeague(
    val id: Long,
    val name: String,
    val image_url: String? = null,
    val slug: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreSerie(
    val full_name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreOpponentWrapper(
    val opponent: PandaScoreTeam? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreTeam(
    val id: Long,
    val name: String,
    val image_url: String? = null,
    val acronym: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreResult(
    val team_id: Long,
    val score: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PandaScoreStream(
    val raw_url: String? = null,
    val embed_url: String? = null,
    val main: Boolean = false
)

