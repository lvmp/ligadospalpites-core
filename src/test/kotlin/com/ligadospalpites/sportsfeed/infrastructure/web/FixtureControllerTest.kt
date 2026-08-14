package com.ligadospalpites.sportsfeed.infrastructure.web

import com.ligadospalpites.shared.identity.UserResolver
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.persistence.*
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.*

class FixtureControllerTest {

    private val sportRepository: SpringDataSportRepository = mock(SpringDataSportRepository::class.java)
    private val leagueRepository: SpringDataLeagueRepository = mock(SpringDataLeagueRepository::class.java)
    private val matchRepository: SpringDataMatchRepository = mock(SpringDataMatchRepository::class.java)
    private val seasonRepository: SpringDataSeasonRepository = mock(SpringDataSeasonRepository::class.java)
    private val entitlementRepository: SpringDataUserEntitlementRepository = mock(SpringDataUserEntitlementRepository::class.java)
    private val userResolver: UserResolver = mock(UserResolver::class.java)

    private lateinit var controller: FixtureController

    private val libertadoresLeagueId = UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de")
    private val footballSportId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val seasonId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        controller = FixtureController(
            sportRepository = sportRepository,
            leagueRepository = leagueRepository,
            matchRepository = matchRepository,
            seasonRepository = seasonRepository,
            entitlementRepository = entitlementRepository,
            userResolver = userResolver
        )

        val leagueEntity = LeagueJpaEntity(
            id = libertadoresLeagueId,
            name = "Copa Libertadores",
            sportId = footballSportId,
            isActive = true,
            format = "GROUPS_AND_KNOCKOUT"
        )
        `when`(leagueRepository.findById(libertadoresLeagueId)).thenReturn(Optional.of(leagueEntity))

        val seasonEntity = SeasonJpaEntity(
            id = seasonId,
            leagueId = libertadoresLeagueId,
            name = "2026",
            startDate = Instant.now(),
            endDate = Instant.now().plusSeconds(86400 * 30),
            isActive = true,
            externalSeasonCode = 2026
        )
        `when`(seasonRepository.findByLeagueIdAndIsActiveTrue(libertadoresLeagueId)).thenReturn(seasonEntity)
    }

    @Test
    fun `should return all 8 groups for Copa Libertadores standings when matches have generic phase`() {
        // Matches with generic phase "Fase de Grupos"
        val sampleMatches = listOf(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballSportId,
                leagueId = libertadoresLeagueId,
                seasonId = seasonId,
                homeTeamName = "Flamengo",
                awayTeamName = "Estudiantes de La Plata",
                kickoffTime = Instant.now(),
                status = MatchStatus.FINISHED,
                homeScore = 2,
                awayScore = 1,
                phase = "Fase de Grupos"
            ),
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballSportId,
                leagueId = libertadoresLeagueId,
                seasonId = seasonId,
                homeTeamName = "Palmeiras",
                awayTeamName = "Cerro Porteño",
                kickoffTime = Instant.now(),
                status = MatchStatus.FINISHED,
                homeScore = 3,
                awayScore = 0,
                phase = "Fase de Grupos"
            )
        )
        `when`(matchRepository.findBySeasonId(seasonId)).thenReturn(sampleMatches)

        val response = controller.getStandings(libertadoresLeagueId)

        assertEquals(200, response.statusCode.value())
        val rows = response.body
        assertNotNull(rows)

        val groupNames = rows!!.mapNotNull { it.groupName }.distinct().sorted()
        val expectedGroups = listOf("Grupo A", "Grupo B", "Grupo C", "Grupo D", "Grupo E", "Grupo F", "Grupo G", "Grupo H")

        assertEquals(expectedGroups, groupNames, "Standings response must contain all 8 groups (Grupos A ao H)")
        assertTrue(rows.size >= 32, "Should contain at least 32 team rows (4 per group)")

        // Validate that Flamengo match in Grupo A was dynamically computed
        val flamengoRow = rows.find { it.teamName == "Flamengo" }
        assertNotNull(flamengoRow)
        assertEquals("Grupo A", flamengoRow?.groupName)
        assertEquals(3, flamengoRow?.points)
        assertEquals(1, flamengoRow?.played)
        assertEquals(1, flamengoRow?.won)

        // Validate that Palmeiras match in Grupo F was dynamically computed
        val palmeirasRow = rows.find { it.teamName == "Palmeiras" }
        assertNotNull(palmeirasRow)
        assertEquals("Grupo F", palmeirasRow?.groupName)
        assertEquals(3, palmeirasRow?.points)
        assertEquals(1, palmeirasRow?.played)

        // Validate each group has exactly 4 teams and total rows is exactly 32
        assertEquals(32, rows.size, "Total standings rows for Libertadores must be exactly 32")
        expectedGroups.forEach { grp ->
            val teamsInGrp = rows.filter { it.groupName == grp }
            assertEquals(4, teamsInGrp.size, "Each group must contain exactly 4 teams, but $grp had ${teamsInGrp.size}")
        }
    }

    @Test
    fun `should return eSports standings fallback for CS2 Major and never return Libertadores football teams`() {
        val cs2LeagueId = UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
        val esportsSportId = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")
        val cs2LeagueEntity = LeagueJpaEntity(
            id = cs2LeagueId,
            name = "Counter-Strike 2 - Major",
            sportId = esportsSportId,
            isActive = true,
            format = "GROUPS_AND_KNOCKOUT"
        )
        val esportsSportEntity = SportJpaEntity(id = esportsSportId, name = "eSports")

        `when`(leagueRepository.findById(cs2LeagueId)).thenReturn(Optional.of(cs2LeagueEntity))
        `when`(sportRepository.findById(esportsSportId)).thenReturn(Optional.of(esportsSportEntity))
        `when`(matchRepository.findByLeagueId(cs2LeagueId)).thenReturn(emptyList())

        val response = controller.getStandings(cs2LeagueId)

        assertEquals(200, response.statusCode.value())
        val rows = response.body
        assertNotNull(rows)
        assertTrue(rows!!.isNotEmpty())

        val teamNames = rows.map { it.teamName }
        assertTrue(teamNames.contains("FaZe Clan"), "CS2 standings must contain FaZe Clan")
        assertTrue(teamNames.contains("Natus Vincere"), "CS2 standings must contain Natus Vincere")
        assertFalse(teamNames.contains("Flamengo"), "CS2 standings must NEVER contain football team Flamengo")
        assertFalse(teamNames.contains("Palmeiras"), "CS2 standings must NEVER contain football team Palmeiras")
    }

    @Test
    fun `should compute eSports standings dynamically when matches are present`() {
        val cs2LeagueId = UUID.fromString("9c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
        val esportsSportId = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")
        val cs2LeagueEntity = LeagueJpaEntity(
            id = cs2LeagueId,
            name = "Counter-Strike 2 - Major",
            sportId = esportsSportId,
            isActive = true,
            format = "GROUPS_AND_KNOCKOUT"
        )
        val esportsSportEntity = SportJpaEntity(id = esportsSportId, name = "eSports")

        val cs2Matches = listOf(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = esportsSportId,
                leagueId = cs2LeagueId,
                seasonId = UUID.randomUUID(),
                homeTeamName = "FURIA",
                awayTeamName = "Natus Vincere",
                kickoffTime = Instant.now(),
                status = MatchStatus.FINISHED,
                homeScore = 2,
                awayScore = 1,
                phase = "Stage 1"
            )
        )

        `when`(leagueRepository.findById(cs2LeagueId)).thenReturn(Optional.of(cs2LeagueEntity))
        `when`(sportRepository.findById(esportsSportId)).thenReturn(Optional.of(esportsSportEntity))
        `when`(matchRepository.findByLeagueId(cs2LeagueId)).thenReturn(cs2Matches)

        val response = controller.getStandings(cs2LeagueId)

        assertEquals(200, response.statusCode.value())
        val rows = response.body
        assertNotNull(rows)
        val furiaRow = rows!!.find { it.teamName == "FURIA" }
        assertNotNull(furiaRow)
        assertEquals(1, furiaRow?.seriesWon)
        assertEquals(0, furiaRow?.seriesLost)
        assertEquals(2, furiaRow?.mapsWon)
        assertEquals(1, furiaRow?.mapsLost)
    }
}
