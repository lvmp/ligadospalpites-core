package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StatsNbaClientTest {

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun `should deserialize StatsNbaResponse JSON and parse standings correctly`() {
        val json = """
            {
              "resource": "leaguestandingsv3",
              "parameters": {
                "LeagueID": "00",
                "Season": "2026-27",
                "SeasonType": "Regular Season"
              },
              "resultSets": [
                {
                  "name": "Standings",
                  "headers": [
                    "LeagueID", "SeasonID", "TeamID", "TeamCity", "TeamName", "Conference", "ConferenceRecord",
                    "PlayoffRank", "ClinchIndicator", "Division", "DivisionRecord", "DivisionRank", "WINS", "LOSSES",
                    "WinPCT", "LeagueRank", "Record", "HOME", "ROAD", "L10", "Last10Home", "Last10Road", "OT",
                    "TotalPoints", "OpponentTotalPoints", "DiffPoints", "vsEast", "vsWest", "ConferenceGamesBehind",
                    "DivisionGamesBehind", "ClinchedConferenceTitle", "ClinchedDivisionTitle", "ClinchedPlayoffCode",
                    "EliminatedConference", "EliminatedDivision", "PointDifferential", "strCurrentStreak"
                  ],
                  "rowSet": [
                    [
                      "00", "22026", 1610612738, "Boston", "Celtics", "East", "41-11", 1, " - e", "Atlantic", "15-2", 1,
                      64, 18, 0.78, 1, "64-18", "37-4", "27-14", "8-2", "9-1", "7-3", "4-1", 9887, 9037, 850, "41-11",
                      "23-7", "0.0", "0.0", 1, 1, "1", 0, 0, 10.4, "W 5"
                    ],
                    [
                      "00", "22026", 1610612760, "Oklahoma City", "Thunder", "West", "36-16", 1, " - w", "Northwest", "12-4", 1,
                      57, 25, 0.695, 2, "57-25", "33-8", "24-17", "7-3", "8-2", "6-4", "3-2", 9845, 9245, 600, "21-9",
                      "36-16", "0.0", "0.0", 1, 1, "1", 0, 0, 7.3, "W 3"
                    ]
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = objectMapper.readValue(json, StatsNbaResponse::class.java)
        assertNotNull(response)
        assertEquals(1, response.resultSets.size)

        val client = StatsNbaClient("https://stats.nba.com", objectMapper)
        assertNotNull(client)
    }
}
