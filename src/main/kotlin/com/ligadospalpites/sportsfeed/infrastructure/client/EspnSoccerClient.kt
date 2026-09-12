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

    fun fetchSoccerMatches(
        leagueCode: String,
        seasonYear: Int = 2026,
        isEuropeanCalendar: Boolean = false
    ): List<EspnSoccerEvent> {
        val dateRange = if (isEuropeanCalendar) {
            "${seasonYear}0801-${seasonYear + 1}0731"
        } else {
            "${seasonYear}0101-${seasonYear}1231"
        }
        logger.info("Fetching full-season matches for $leagueCode from ESPN Public API (season: $seasonYear, dates: $dateRange, EuropeanCalendar: $isEuropeanCalendar)")
        return try {
            val response = restClient.get()
                .uri("/apis/site/v2/sports/soccer/$leagueCode/scoreboard?dates=$dateRange&limit=500")
                .retrieve()
                .body(EspnSoccerResponse::class.java)
            response?.events ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error communicating with ESPN Soccer API for $leagueCode: ${e.message}", e)
            throw e
        }
    }

    fun fetchLibertadoresMatches(seasonYear: Int = 2026): List<EspnSoccerEvent> {
        return fetchSoccerMatches("conmebol.libertadores", seasonYear)
    }

    fun fetchCopaDoBrasilMatches(seasonYear: Int = 2026): List<EspnSoccerEvent> {
        return fetchSoccerMatches("bra.copa_do_brazil", seasonYear)
    }

    fun fetchMatchSummary(leagueCode: String, eventId: String): EspnMatchSummaryResponse? {
        logger.info("Fetching match summary from ESPN Soccer API for league $leagueCode, event $eventId")
        return try {
            restClient.get()
                .uri("/apis/site/v2/sports/soccer/$leagueCode/summary?event=$eventId")
                .retrieve()
                .body(EspnMatchSummaryResponse::class.java)
        } catch (e: Exception) {
            logger.warn("Could not fetch match summary from ESPN for event $eventId (${e.message})")
            null
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
    val altGameNote: String? = null,
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

data class EspnMatchSummaryResponse(
    val header: EspnSummaryHeader? = null,
    val keyEvents: List<EspnKeyEvent> = emptyList(),
    val commentary: List<EspnCommentaryItem> = emptyList()
)

data class EspnSummaryHeader(
    val id: String? = null,
    val season: EspnSummarySeason? = null,
    val competitions: List<EspnSoccerCompetition> = emptyList()
)

data class EspnSummarySeason(
    val year: Int? = null,
    val type: Int? = null
)

data class EspnKeyEvent(
    val id: String? = null,
    val type: EspnKeyEventType? = null,
    val text: String? = null,
    val clock: EspnClock? = null,
    val team: EspnSoccerTeam? = null,
    val participants: List<EspnParticipant> = emptyList()
)

data class EspnKeyEventType(
    val id: String? = null,
    val text: String? = null
)

data class EspnClock(
    val value: Double? = null,
    val displayValue: String? = null
)

data class EspnParticipant(
    val athlete: EspnAthlete? = null,
    val type: String? = null
)

data class EspnAthlete(
    val id: String? = null,
    val displayName: String? = null,
    val shortName: String? = null
)

data class EspnCommentaryItem(
    val id: String? = null,
    val text: String? = null,
    val time: EspnClock? = null,
    val play: Boolean = false
)
