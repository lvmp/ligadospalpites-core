package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BalldontlieClientTest {

    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun `should deserialize BalldontlieGamesResponse JSON correctly`() {
        val json = """
            {
              "data": [
                {
                  "id": 101,
                  "date": "2026-10-22",
                  "datetime": "2026-10-22T23:30:00.000Z",
                  "season": 2026,
                  "status": "Final",
                  "period": 4,
                  "time": "",
                  "postseason": false,
                  "home_team_score": 112,
                  "visitor_team_score": 105,
                  "home_team": {
                    "id": 1,
                    "full_name": "Boston Celtics",
                    "name": "Celtics",
                    "abbreviation": "BOS",
                    "city": "Boston"
                  },
                  "visitor_team": {
                    "id": 2,
                    "full_name": "New York Knicks",
                    "name": "Knicks",
                    "abbreviation": "NYK",
                    "city": "New York"
                  }
                }
              ]
            }
        """.trimIndent()

        val response = objectMapper.readValue(json, BalldontlieGamesResponse::class.java)
        assertNotNull(response)
        assertEquals(1, response.data.size)

        val game = response.data[0]
        assertEquals(101, game.id)
        assertEquals("Boston Celtics", game.homeTeam.fullName)
        assertEquals("New York Knicks", game.visitorTeam.fullName)
        assertEquals(112, game.homeTeamScore)
        assertEquals(105, game.visitorTeamScore)
        assertEquals("Final", game.status)
        assertFalse(game.postseason)
    }

    @Test
    fun `should instantiate BalldontlieClient correctly`() {
        val client = BalldontlieClient("https://api.balldontlie.io", "test-api-key")
        assertNotNull(client)
    }
}
