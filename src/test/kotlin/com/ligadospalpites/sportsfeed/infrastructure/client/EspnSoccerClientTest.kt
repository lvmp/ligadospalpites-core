package com.ligadospalpites.sportsfeed.infrastructure.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class EspnSoccerClientTest {

    @Test
    fun `should deserialize ESPN Libertadores json response format correctly`() {
        val sampleEvent = EspnSoccerEvent(
            id = "1001",
            date = "2026-04-10T22:00Z",
            name = "Flamengo at Palmeiras",
            shortName = "FLA @ PAL",
            competitions = listOf(
                EspnSoccerCompetition(
                    id = "1001",
                    date = "2026-04-10T22:00Z",
                    status = EspnSoccerStatus(
                        type = EspnSoccerStatusType(
                            id = "1",
                            name = "STATUS_SCHEDULED",
                            state = "pre",
                            completed = false,
                            description = "Fase de Grupos"
                        )
                    ),
                    competitors = listOf(
                        EspnSoccerCompetitor(
                            id = "123",
                            homeAway = "home",
                            team = EspnSoccerTeam(id = "123", displayName = "Palmeiras", logo = "https://logo.png"),
                            score = "2"
                        ),
                        EspnSoccerCompetitor(
                            id = "456",
                            homeAway = "away",
                            team = EspnSoccerTeam(id = "456", displayName = "Flamengo", logo = "https://logo2.png"),
                            score = "1"
                        )
                    )
                )
            )
        )

        assertEquals("1001", sampleEvent.id)
        assertEquals(1, sampleEvent.competitions.size)

        val comp = sampleEvent.competitions.first()
        assertEquals("pre", comp.status?.type?.state)

        val home = comp.competitors.find { it.homeAway == "home" }
        assertNotNull(home)
        assertEquals("Palmeiras", home?.team?.displayName)
        assertEquals("2", home?.score)

        val away = comp.competitors.find { it.homeAway == "away" }
        assertNotNull(away)
        assertEquals("Flamengo", away?.team?.displayName)
        assertEquals("1", away?.score)
    }
}
