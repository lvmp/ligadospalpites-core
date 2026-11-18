package com.ligadospalpites.sportsfeed.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class EspnBasketballClientTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `should serialize and deserialize PeriodScoresDto correctly`() {
        val dto = PeriodScoresDto(
            home = listOf(28, 30, 22, 25),
            away = listOf(24, 25, 29, 21),
            homeOvertime = 0,
            awayOvertime = 0
        )

        val json = objectMapper.writeValueAsString(dto)
        assertTrue(json.contains("\"home\":[28,30,22,25]"))
        assertTrue(json.contains("\"away\":[24,25,29,21]"))

        val read = objectMapper.readValue(json, PeriodScoresDto::class.java)
        assertEquals(dto.home, read.home)
        assertEquals(dto.away, read.away)
        assertEquals(0, read.homeOvertime)
        assertEquals(0, read.awayOvertime)
    }

    @Test
    fun `should instantiate EspnBasketballClient correctly`() {
        val client = EspnBasketballClient("https://site.api.espn.com/apis/site/v2/sports/basketball", objectMapper)
        assertNotNull(client)
    }

    @Test
    fun `should fetch official NBA standings from apis v2 endpoint and normalize team aliases`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/apis/v2/sports/basketball/nba/standings") { exchange ->
            val payload = """
                {
                  "children": [
                    {
                      "name": "Eastern Conference",
                      "standings": {
                        "entries": [
                          {
                            "team": { "displayName": "Boston Celtics" },
                            "stats": [
                              { "name": "wins", "displayValue": "12" },
                              { "name": "losses", "displayValue": "3" },
                              { "name": "winPercent", "displayValue": ".800" },
                              { "name": "gamesBehind", "displayValue": "-" },
                              { "name": "streak", "displayValue": "W4" },
                              { "name": "playoffSeed", "displayValue": "1" }
                            ]
                          }
                        ]
                      }
                    },
                    {
                      "name": "Western Conference",
                      "standings": {
                        "entries": [
                          {
                            "team": { "displayName": "LA Clippers" },
                            "stats": [
                              { "name": "wins", "displayValue": "10" },
                              { "name": "losses", "displayValue": "5" },
                              { "name": "winPercent", "displayValue": ".667" },
                              { "name": "gamesBehind", "displayValue": "1.0" },
                              { "name": "streak", "displayValue": "W2" },
                              { "name": "playoffSeed", "displayValue": "3" }
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
            """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            val port = server.address.port
            val client = EspnBasketballClient("http://localhost:$port/apis/site/v2/sports/basketball", objectMapper)
            val rows = client.fetchNbaStandings()

            assertEquals(2, rows.size)
            val celtics = rows.find { it.teamName == "Boston Celtics" }
            val clippers = rows.find { it.teamName == "Los Angeles Clippers" }
            assertNotNull(celtics)
            assertEquals("Eastern Conference", celtics?.groupName)
            assertEquals(1, celtics?.position)

            assertNotNull(clippers, "LA Clippers must be normalized to Los Angeles Clippers")
            assertEquals("Western Conference", clippers?.groupName)
            assertEquals(3, clippers?.position)
        } finally {
            server.stop(0)
        }
    }
}
