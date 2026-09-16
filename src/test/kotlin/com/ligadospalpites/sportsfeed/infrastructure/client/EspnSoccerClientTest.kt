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

    @Test
    fun `should deserialize ESPN Libertadores standings response and map to StandingRow correctly`() {
        val sampleResponse = EspnSoccerStandingsResponse(
            children = listOf(
                EspnSoccerGroup(
                    name = "Group A",
                    standings = EspnSoccerGroupStandings(
                        entries = listOf(
                            EspnSoccerStandingEntry(
                                team = EspnSoccerStandingTeam(
                                    id = "819",
                                    displayName = "Flamengo",
                                    logos = listOf(EspnSoccerLogo(href = "https://flamengo.png"))
                                ),
                                stats = listOf(
                                    EspnSoccerStat(name = "rank", value = 1.0),
                                    EspnSoccerStat(name = "points", value = 16.0),
                                    EspnSoccerStat(name = "gamesPlayed", value = 6.0),
                                    EspnSoccerStat(name = "wins", value = 5.0),
                                    EspnSoccerStat(name = "ties", value = 1.0),
                                    EspnSoccerStat(name = "losses", value = 0.0),
                                    EspnSoccerStat(name = "pointsFor", value = 14.0),
                                    EspnSoccerStat(name = "pointsAgainst", value = 2.0),
                                    EspnSoccerStat(name = "pointDifferential", value = 12.0)
                                )
                            ),
                            EspnSoccerStandingEntry(
                                team = EspnSoccerStandingTeam(
                                    id = "8",
                                    displayName = "Estudiantes de La Plata",
                                    logos = listOf(EspnSoccerLogo(href = "https://estudiantes.png"))
                                ),
                                stats = listOf(
                                    EspnSoccerStat(name = "rank", value = 2.0),
                                    EspnSoccerStat(name = "points", value = 9.0),
                                    EspnSoccerStat(name = "gamesPlayed", value = 6.0),
                                    EspnSoccerStat(name = "wins", value = 2.0),
                                    EspnSoccerStat(name = "ties", value = 3.0),
                                    EspnSoccerStat(name = "losses", value = 1.0),
                                    EspnSoccerStat(name = "pointsFor", value = 6.0),
                                    EspnSoccerStat(name = "pointsAgainst", value = 5.0),
                                    EspnSoccerStat(name = "pointDifferential", value = 1.0)
                                )
                            )
                        )
                    )
                )
            )
        )

        assertNotNull(sampleResponse.children)
        assertEquals(1, sampleResponse.children.size)

        val group = sampleResponse.children.first()
        assertEquals("Group A", group.name)
        val entries = group.standings?.entries
        assertNotNull(entries)
        assertEquals(2, entries?.size)

        val fla = entries?.find { it.team.displayName == "Flamengo" }
        assertNotNull(fla)
        val statMap = fla!!.stats.associateBy { it.name }
        assertEquals(1, statMap["rank"]?.value?.toInt())
        assertEquals(16, statMap["points"]?.value?.toInt())
        assertEquals(6, statMap["gamesPlayed"]?.value?.toInt())
        assertEquals(5, statMap["wins"]?.value?.toInt())
        assertEquals(1, statMap["ties"]?.value?.toInt())
        assertEquals(0, statMap["losses"]?.value?.toInt())
        assertEquals(14, statMap["pointsFor"]?.value?.toInt())
        assertEquals(2, statMap["pointsAgainst"]?.value?.toInt())
        assertEquals(12, statMap["pointDifferential"]?.value?.toInt())
    }
}
