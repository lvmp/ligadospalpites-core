package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GoalApiSoccerClientTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `should deserialize GOAL API fixtures response format correctly`() {
        val jsonPayload = """
        {
          "success": true,
          "data": [
            {
              "id": 894101,
              "leagueId": 349,
              "season": 2026,
              "round": "Round of 32",
              "stage": "Round of 32",
              "status": "FINISHED",
              "kickoffUtc": "2026-04-29T22:30:00.000Z",
              "matchDate": "2026-04-29",
              "matchTime": "19:30",
              "homeTeam": {
                "id": 1001,
                "name": "Flamengo",
                "shortName": "FLA",
                "logo": "https://flamengo.png"
              },
              "awayTeam": {
                "id": 1002,
                "name": "Palmeiras",
                "shortName": "PAL",
                "logo": "https://palmeiras.png"
              },
              "score": {
                "home": 2,
                "away": 0,
                "homeHalfTime": 1,
                "awayHalfTime": 0,
                "penalties": {
                  "home": null,
                  "away": null
                }
              }
            }
          ],
          "pagination": {
            "page": 1,
            "limit": 50,
            "total": 1,
            "totalPages": 1
          }
        }
        """.trimIndent()

        val response: GoalApiResponse<List<GoalApiFixture>> = objectMapper.readValue(jsonPayload)

        assertTrue(response.success)
        assertNotNull(response.data)
        assertEquals(1, response.data!!.size)

        val fixture = response.data!!.first()
        assertEquals(894101L, fixture.id)
        assertEquals(349, fixture.leagueId)
        assertEquals(2026, fixture.season)
        assertEquals("Round of 32", fixture.round)
        assertEquals("Round of 32", fixture.stage)
        assertEquals("FINISHED", fixture.status)
        assertEquals("2026-04-29T22:30:00.000Z", fixture.kickoffUtc)
        assertEquals("2026-04-29", fixture.matchDate)
        assertEquals("19:30", fixture.matchTime)

        assertEquals("Flamengo", fixture.homeTeam?.name)
        assertEquals("https://flamengo.png", fixture.homeTeam?.logo)
        assertEquals("Palmeiras", fixture.awayTeam?.name)
        assertEquals("https://palmeiras.png", fixture.awayTeam?.logo)

        assertEquals(2, fixture.score?.home)
        assertEquals(0, fixture.score?.away)
        assertEquals(1, fixture.score?.homeHalfTime)
    }

    @Test
    fun `should instantiate GoalApiSoccerClient with default parameters`() {
        val client = GoalApiSoccerClient(
            baseUrl = "https://goal-api.com/api/v1",
            apiKey = "test-api-key"
        )
        assertNotNull(client)
    }

    @Test
    fun `should correctly deserialize live and scheduled fixtures without score`() {
        val jsonPayload = """
        {
          "success": true,
          "data": [
            {
              "id": 894102,
              "leagueId": 349,
              "season": 2026,
              "round": "Quarter-finals",
              "stage": "Quarter-finals",
              "status": "LIVE",
              "kickoffUtc": "2026-07-15T21:30:00.000Z",
              "homeTeam": {
                "name": "Corinthians"
              },
              "awayTeam": {
                "name": "São Paulo"
              },
              "score": {
                "home": 1,
                "away": 1
              }
            },
            {
              "id": 894103,
              "leagueId": 349,
              "season": 2026,
              "round": "Final",
              "stage": "Final",
              "status": "NS",
              "kickoffUtc": "2026-10-20T21:45:00.000Z",
              "homeTeam": {
                "name": "Atlético-MG"
              },
              "awayTeam": {
                "name": "Cruzeiro"
              },
              "score": null
            }
          ]
        }
        """.trimIndent()

        val response: GoalApiResponse<List<GoalApiFixture>> = objectMapper.readValue(jsonPayload)
        assertEquals(2, response.data?.size)

        val liveMatch = response.data!![0]
        assertEquals("LIVE", liveMatch.status)
        assertEquals(1, liveMatch.score?.home)

        val scheduledMatch = response.data!![1]
        assertEquals("NS", scheduledMatch.status)
        assertNull(scheduledMatch.score)
    }
}
