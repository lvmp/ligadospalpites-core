package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ligadospalpites.sportsfeed.infrastructure.web.StandingRow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

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

    fun fetchLibertadoresStandings(seasonYear: Int = 2026): List<StandingRow> {
        logger.info("Fetching official Copa Libertadores Standings from ESPN Public API (season: $seasonYear)")
        return try {
            val response = restClient.get()
                .uri("/apis/v2/sports/soccer/conmebol.libertadores/standings?season=$seasonYear")
                .retrieve()
                .body(EspnSoccerStandingsResponse::class.java)

            val rows = mutableListOf<StandingRow>()
            response?.children?.forEach { group ->
                val groupName = (group.name ?: "Grupo A")
                    .replace("Group", "Grupo")
                    .trim()
                val entries = group.standings?.entries ?: emptyList()
                val groupRows = entries.mapIndexed { idx, entry ->
                    val teamName = entry.team.displayName ?: entry.team.name ?: "Time"
                    val logo = entry.team.logos.firstOrNull()?.href
                    val statMap = entry.stats.associateBy { it.name }

                    val rank = statMap["rank"]?.value?.toInt()
                        ?: statMap["rank"]?.displayValue?.toIntOrNull()
                        ?: (idx + 1)
                    val points = statMap["points"]?.value?.toInt()
                        ?: statMap["points"]?.displayValue?.toIntOrNull()
                        ?: 0
                    val played = statMap["gamesPlayed"]?.value?.toInt()
                        ?: statMap["gamesPlayed"]?.displayValue?.toIntOrNull()
                        ?: 0
                    val wins = statMap["wins"]?.value?.toInt()
                        ?: statMap["wins"]?.displayValue?.toIntOrNull()
                        ?: 0
                    val ties = statMap["ties"]?.value?.toInt()
                        ?: statMap["ties"]?.displayValue?.toIntOrNull()
                        ?: 0
                    val losses = statMap["losses"]?.value?.toInt()
                        ?: statMap["losses"]?.displayValue?.toIntOrNull()
                        ?: 0
                    val goalsFor = statMap["pointsFor"]?.value?.toInt()
                        ?: statMap["pointsFor"]?.displayValue?.toIntOrNull()
                        ?: 0
                    val goalsAgainst = statMap["pointsAgainst"]?.value?.toInt()
                        ?: statMap["pointsAgainst"]?.displayValue?.toIntOrNull()
                        ?: 0
                    val goalDifference = statMap["pointDifferential"]?.value?.toInt()
                        ?: statMap["pointDifferential"]?.displayValue?.toIntOrNull()
                        ?: (goalsFor - goalsAgainst)
                    val winRate = if (played > 0) Math.round((wins.toDouble() / played) * 100.0) / 100.0 else 0.0

                    StandingRow(
                        position = rank,
                        teamId = UUID.nameUUIDFromBytes(teamName.toByteArray()),
                        teamName = teamName,
                        points = points,
                        played = played,
                        won = wins,
                        drawn = ties,
                        lost = losses,
                        goalsFor = goalsFor,
                        goalsAgainst = goalsAgainst,
                        goalDifference = goalDifference,
                        groupName = groupName,
                        winRate = winRate,
                        teamLogoUrl = logo
                    )
                }.sortedBy { it.position }
                rows.addAll(groupRows)
            }
            logger.info("Successfully fetched ${rows.size} official standing rows for Libertadores from ESPN")
            rows
        } catch (e: Exception) {
            logger.error("Failed to fetch official ESPN Libertadores standings: ${e.message}", e)
            emptyList()
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class EspnSoccerStandingsResponse(
    val children: List<EspnSoccerGroup> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EspnSoccerGroup(
    val id: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
    val standings: EspnSoccerGroupStandings? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EspnSoccerGroupStandings(
    val id: String? = null,
    val name: String? = null,
    val season: Int? = null,
    val entries: List<EspnSoccerStandingEntry> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EspnSoccerStandingEntry(
    val team: EspnSoccerStandingTeam = EspnSoccerStandingTeam(),
    val stats: List<EspnSoccerStat> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EspnSoccerStandingTeam(
    val id: String? = null,
    val uid: String? = null,
    val name: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val logos: List<EspnSoccerLogo> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EspnSoccerLogo(
    val href: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EspnSoccerStat(
    val name: String = "",
    val value: Double? = null,
    val displayValue: String? = null
)

