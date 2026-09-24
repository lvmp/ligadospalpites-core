package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PandaScoreClientTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `should return empty list when apiToken is blank`() {
        val client = PandaScoreClient("https://api.pandascore.co", "")
        val matches = client.fetchMatches(leagueIdOrSlug = "cblol")
        val standings = client.fetchStandings("cblol-split-2-2024")
        val resolvedId = client.resolveLeagueId("CBLOL")

        assertTrue(matches.isEmpty())
        assertTrue(standings.isEmpty())
        assertNull(resolvedId)
    }

    @Test
    fun `should deserialize PandaScore match response format correctly`() {
        val jsonPayload = """
        [
          {
            "id": 998877,
            "name": "LOUD vs paiN Gaming",
            "begin_at": "2026-09-20T16:00:00Z",
            "status": "finished",
            "number_of_games": 3,
            "league": {
              "id": 100,
              "name": "CBLOL",
              "slug": "cblol",
              "image_url": "https://cdn.pandascore.co/cblol.png"
            },
            "videogame": {
              "id": 1,
              "name": "LoL",
              "slug": "league-of-legends"
            },
            "serie": {
              "id": 200,
              "full_name": "Split 2 2026",
              "name": "Split 2"
            },
            "opponents": [
              {
                "type": "Team",
                "opponent": {
                  "id": 101,
                  "name": "LOUD",
                  "acronym": "LLL",
                  "image_url": "https://cdn.pandascore.co/loud.png"
                }
              },
              {
                "type": "Team",
                "opponent": {
                  "id": 102,
                  "name": "paiN Gaming",
                  "acronym": "PNG",
                  "image_url": "https://cdn.pandascore.co/pain.png"
                }
              }
            ],
            "results": [
              { "team_id": 101, "score": 2 },
              { "team_id": 102, "score": 1 }
            ],
            "streams_list": [
              {
                "raw_url": "https://twitch.tv/cblol",
                "main": true,
                "language": "pt"
              }
            ]
          }
        ]
        """.trimIndent()

        val matches: List<PandaScoreMatchResponse> = objectMapper.readValue(jsonPayload)

        assertEquals(1, matches.size)
        val match = matches.first()
        assertEquals(998877L, match.id)
        assertEquals("finished", match.status)
        assertEquals("league-of-legends", match.videogame?.slug)
        assertEquals("LoL", match.videogame?.name)
        assertEquals(3, match.number_of_games)
        assertEquals(2, match.opponents.size)
        assertEquals("LOUD", match.opponents[0].opponent?.name)
        assertEquals("paiN Gaming", match.opponents[1].opponent?.name)
        assertEquals(2, match.results.find { it.team_id == 101L }?.score)
        assertEquals(1, match.results.find { it.team_id == 102L }?.score)
        assertEquals("https://twitch.tv/cblol", match.streams_list.firstOrNull()?.raw_url)
    }

    @Test
    fun `should deserialize PandaScore standings response format correctly`() {
        val jsonPayload = """
        [
          {
            "rank": 1,
            "wins": 14,
            "losses": 4,
            "ties": 0,
            "points": 42,
            "matches_played": 18,
            "team": {
              "id": 101,
              "name": "LOUD",
              "acronym": "LLL",
              "image_url": "https://cdn.pandascore.co/loud.png"
            }
          }
        ]
        """.trimIndent()

        val standings: List<PandaScoreStandingResponse> = objectMapper.readValue(jsonPayload)

        assertEquals(1, standings.size)
        val standing = standings.first()
        assertEquals(1, standing.rank)
        assertEquals(14, standing.wins)
        assertEquals(4, standing.losses)
        assertEquals(42, standing.points)
        assertEquals("LOUD", standing.team.name)
    }
}
