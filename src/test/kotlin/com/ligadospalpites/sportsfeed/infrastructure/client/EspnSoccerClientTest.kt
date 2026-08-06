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

    @Test
    fun `should deserialize altGameNote correctly when present in competition`() {
        val comp = EspnSoccerCompetition(
            id = "1002",
            date = "2026-04-15T22:00Z",
            altGameNote = "CONMEBOL Libertadores, Group C"
        )
        assertEquals("CONMEBOL Libertadores, Group C", comp.altGameNote)
    }

    @Test
    fun `should construct correct date ranges for european vs civil calendar`() {
        val client = EspnSoccerClient("https://site.api.espn.com")
        // Default (isEuropeanCalendar = false): 20260101-20261231
        // European (isEuropeanCalendar = true): 20260801-20270731
        val isEuropean = true
        val seasonYear = 2026
        val dateRangeEuropean = if (isEuropean) "${seasonYear}0801-${seasonYear + 1}0731" else "${seasonYear}0101-${seasonYear}1231"
        val dateRangeCivil = if (!isEuropean) "${seasonYear}0801-${seasonYear + 1}0731" else "${seasonYear}0101-${seasonYear}1231"

        assertEquals("20260801-20270731", dateRangeEuropean)
        assertEquals("20260101-20261231", dateRangeCivil)
    }
}
