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
    private val espnSoccerClient: com.ligadospalpites.sportsfeed.infrastructure.client.EspnSoccerClient = mock(com.ligadospalpites.sportsfeed.infrastructure.client.EspnSoccerClient::class.java)

    private lateinit var controller: FixtureController

    private val libertadoresLeagueId = UUID.fromString("4acdf011-fbde-4122-83bc-c46b1ba847de")
    private val copaDoBrasilLeagueId = UUID.fromString("b3cdf011-fbde-4122-83bc-c46b1ba847de")
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
            userResolver = userResolver,
            espnSoccerClient = espnSoccerClient
        )

        val leagueEntity = LeagueJpaEntity(
            id = libertadoresLeagueId,
            name = "Copa Libertadores",
            sportId = footballSportId,
            isActive = true,
            format = "GROUPS_AND_KNOCKOUT"
        )
        `when`(leagueRepository.findById(libertadoresLeagueId)).thenReturn(Optional.of(leagueEntity))

        val copaDoBrasilEntity = LeagueJpaEntity(
            id = copaDoBrasilLeagueId,
            name = "Copa do Brasil",
            sportId = footballSportId,
            isActive = true,
            format = "KNOCKOUT"
        )
        `when`(leagueRepository.findById(copaDoBrasilLeagueId)).thenReturn(Optional.of(copaDoBrasilEntity))

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
    fun `should return official ESPN standings for Copa Libertadores without in-service calculation`() {
        val officialEspnRows = listOf(
            StandingRow(1, UUID.randomUUID(), "Flamengo", points = 16, played = 6, won = 5, drawn = 1, lost = 0, goalsFor = 14, goalsAgainst = 2, goalDifference = 12, groupName = "Grupo A"),
            StandingRow(2, UUID.randomUUID(), "Estudiantes de La Plata", points = 9, played = 6, won = 2, drawn = 3, lost = 1, goalsFor = 6, goalsAgainst = 5, goalDifference = 1, groupName = "Grupo A")
        )
        `when`(espnSoccerClient.fetchLibertadoresStandings()).thenReturn(officialEspnRows)

        val response = controller.getStandings(libertadoresLeagueId)

        assertEquals(200, response.statusCode.value())
        val rows = response.body
        assertNotNull(rows)
        assertEquals(2, rows!!.size)
        assertEquals("Flamengo", rows[0].teamName)
        assertEquals(16, rows[0].points)
        assertEquals("Estudiantes de La Plata", rows[1].teamName)
        assertEquals(9, rows[1].points)
        verify(espnSoccerClient, times(1)).fetchLibertadoresStandings()
    }

    @Test
    fun `should return all 8 groups as fallback when ESPN client returns empty for Libertadores`() {
        `when`(espnSoccerClient.fetchLibertadoresStandings()).thenReturn(emptyList())

        val response = controller.getStandings(libertadoresLeagueId)

        assertEquals(200, response.statusCode.value())
        val rows = response.body
        assertNotNull(rows)

        val groupNames = rows!!.mapNotNull { it.groupName }.distinct().sorted()
        val expectedGroups = listOf("Grupo A", "Grupo B", "Grupo C", "Grupo D", "Grupo E", "Grupo F", "Grupo G", "Grupo H")

        assertEquals(expectedGroups, groupNames, "Standings response must contain all 8 groups (Grupos A ao H)")
        assertEquals(32, rows.size, "Total standings fallback rows for Libertadores must be exactly 32 (4 per group)")
    }

    @Test
    fun `should return empty standings list for knockout tournament like Copa do Brasil`() {
        val response = controller.getStandings(copaDoBrasilLeagueId)

        assertEquals(200, response.statusCode.value())
        val rows = response.body
        assertNotNull(rows)
        assertTrue(rows!!.isEmpty(), "Standings for KNOCKOUT tournament like Copa do Brasil must be empty")
    }

    @Test
    fun `should return populated bracket stages for Copa do Brasil without calculation`() {
        val matches = listOf(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballSportId,
                leagueId = copaDoBrasilLeagueId,
                seasonId = seasonId,
                homeTeamName = "Flamengo",
                awayTeamName = "Amazonas FC",
                kickoffTime = Instant.now(),
                status = MatchStatus.FINISHED,
                homeScore = 1,
                awayScore = 0,
                phase = "Terceira Fase"
            ),
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballSportId,
                leagueId = copaDoBrasilLeagueId,
                seasonId = seasonId,
                homeTeamName = "Flamengo",
                awayTeamName = "Palmeiras",
                kickoffTime = Instant.now(),
                status = MatchStatus.FINISHED,
                homeScore = 2,
                awayScore = 0,
                phase = "Oitavas de Final"
            ),
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballSportId,
                leagueId = copaDoBrasilLeagueId,
                seasonId = seasonId,
                homeTeamName = "Flamengo",
                awayTeamName = "Atlético-MG",
                kickoffTime = Instant.now(),
                status = MatchStatus.FINISHED,
                homeScore = 3,
                awayScore = 1,
                phase = "Grande Final"
            )
        )
        `when`(matchRepository.findByLeagueId(copaDoBrasilLeagueId)).thenReturn(matches)

        val response = controller.getBrackets(copaDoBrasilLeagueId)

        assertEquals(200, response.statusCode.value())
        val bracket = response.body
        assertNotNull(bracket)
        assertEquals(copaDoBrasilLeagueId, bracket!!.leagueId)

        val stages = bracket.phases
        assertTrue(stages.containsKey("ROUND_OF_32"))
        assertTrue(stages.containsKey("OITAVAS"))
        assertTrue(stages.containsKey("FINAL"))

        assertEquals(1, stages["ROUND_OF_32"]?.size)
        assertEquals("Flamengo", stages["ROUND_OF_32"]?.first()?.homeTeam)
        assertEquals(1, stages["OITAVAS"]?.size)
        assertEquals(1, stages["FINAL"]?.size)
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
        assertEquals(1, furiaRow?.played)
        assertEquals(1.0, furiaRow?.winRate)
        assertEquals("W1", furiaRow?.streak)
        assertEquals(2, furiaRow?.mapsWon)
        assertEquals(1, furiaRow?.mapsLost)
    }

    @Test
    fun `should return NBA standings partitioned into Eastern and Western conferences`() {
        val nbaLeagueId = UUID.fromString("5c1e3a11-b9db-44ab-ba02-411a0c0bcf14")
        val basketballSportId = UUID.fromString("e5284bf1-d576-4740-97cc-f06bca181cb2")
        val nbaLeagueEntity = LeagueJpaEntity(
            id = nbaLeagueId,
            name = "NBA",
            sportId = basketballSportId,
            isActive = true,
            format = "POINTS"
        )
        val basketballSportEntity = SportJpaEntity(id = basketballSportId, name = "Basquete")

        val nbaMatches = listOf(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = basketballSportId,
                leagueId = nbaLeagueId,
                seasonId = UUID.randomUUID(),
                homeTeamName = "Boston Celtics",
                awayTeamName = "Miami Heat",
                kickoffTime = Instant.now(),
                status = MatchStatus.FINISHED,
                homeScore = 110,
                awayScore = 100,
                phase = "Temporada Regular"
            ),
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = basketballSportId,
                leagueId = nbaLeagueId,
                seasonId = UUID.randomUUID(),
                homeTeamName = "Los Angeles Lakers",
                awayTeamName = "Golden State Warriors",
                kickoffTime = Instant.now(),
                status = MatchStatus.FINISHED,
                homeScore = 105,
                awayScore = 108,
                phase = "Temporada Regular"
            )
        )

        `when`(leagueRepository.findById(nbaLeagueId)).thenReturn(Optional.of(nbaLeagueEntity))
        `when`(sportRepository.findById(basketballSportId)).thenReturn(Optional.of(basketballSportEntity))
        `when`(matchRepository.findByLeagueId(nbaLeagueId)).thenReturn(nbaMatches)

        val response = controller.getStandings(nbaLeagueId)

        assertEquals(200, response.statusCode.value())
        val rows = response.body
        assertNotNull(rows)
        assertTrue(rows!!.isNotEmpty())

        val groups = rows.mapNotNull { it.groupName }.distinct()
        assertTrue(groups.contains("Eastern Conference"), "Should contain Eastern Conference")
        assertTrue(groups.contains("Western Conference"), "Should contain Western Conference")

        val celtics = rows.find { it.teamName == "Boston Celtics" }
        assertNotNull(celtics)
        assertEquals("Eastern Conference", celtics?.groupName)
        assertEquals(1, celtics?.won)
        assertEquals(1, celtics?.position)

        val warriors = rows.find { it.teamName == "Golden State Warriors" }
        assertNotNull(warriors)
        assertEquals("Western Conference", warriors?.groupName)
        assertEquals(1, warriors?.won)
        assertEquals(1, warriors?.position)
    }

    @Test
    fun `should return 403 Forbidden when user is not premium on timeline endpoint`() {
        val matchId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val useCase = mock(com.ligadospalpites.sportsfeed.application.usecases.GetMatchTimelineUseCase::class.java)
        `when`(userResolver.resolveByUidOrUuid("non-premium")).thenReturn(userId)
        `when`(useCase.execute(matchId, userId)).thenThrow(org.springframework.security.access.AccessDeniedException("PREMIUM_REQUIRED"))

        val testController = FixtureController(
            sportRepository = sportRepository,
            leagueRepository = leagueRepository,
            matchRepository = matchRepository,
            seasonRepository = seasonRepository,
            entitlementRepository = entitlementRepository,
            userResolver = userResolver,
            getMatchTimelineUseCase = useCase
        )

        val response = testController.getMatchTimeline(matchId, "non-premium")
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("PREMIUM_REQUIRED", body["error"])
    }

    @Test
    fun `should return 200 OK with timeline events when user is premium`() {
        val matchId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val useCase = mock(com.ligadospalpites.sportsfeed.application.usecases.GetMatchTimelineUseCase::class.java)
        `when`(userResolver.resolveByUidOrUuid("premium-user")).thenReturn(userId)

        val sampleEvent = com.ligadospalpites.sportsfeed.domain.models.MatchTimelineEvent(
            matchId = matchId,
            minute = 10,
            eventType = com.ligadospalpites.sportsfeed.domain.models.TimelineEventType.GOAL,
            description = "Golaço do Mengão!"
        )
        `when`(useCase.execute(matchId, userId)).thenReturn(listOf(sampleEvent))

        val testController = FixtureController(
            sportRepository = sportRepository,
            leagueRepository = leagueRepository,
            matchRepository = matchRepository,
            seasonRepository = seasonRepository,
            entitlementRepository = entitlementRepository,
            userResolver = userResolver,
            getMatchTimelineUseCase = useCase
        )

        val response = testController.getMatchTimeline(matchId, "premium-user")
        assertEquals(org.springframework.http.HttpStatus.OK, response.statusCode)
        val body = response.body as List<*>
        assertEquals(1, body.size)
        val eventRes = body[0] as MatchTimelineEventResponse
        assertEquals(10, eventRes.minute)
        assertEquals("GOAL", eventRes.eventType)
        assertEquals("Golaço do Mengão!", eventRes.description)
    }

    @Test
    fun `should return eSports standings fallback for new leagues ESL Pro League and BLAST Premier`() {
        val eslLeagueId = UUID.fromString("bc1e3a11-b9db-44ab-ba02-411a0c0bcf14")
        val esportsSportId = UUID.fromString("9b1e3a11-b9db-44ab-ba02-411a0c0bcf14")
        val eslLeagueEntity = LeagueJpaEntity(
            id = eslLeagueId,
            name = "Counter-Strike 2 - ESL Pro League",
            sportId = esportsSportId,
            isActive = true,
            format = "POINTS"
        )
        val esportsSportEntity = SportJpaEntity(id = esportsSportId, name = "eSports")

        `when`(leagueRepository.findById(eslLeagueId)).thenReturn(Optional.of(eslLeagueEntity))
        `when`(sportRepository.findById(esportsSportId)).thenReturn(Optional.of(esportsSportEntity))
        `when`(matchRepository.findByLeagueId(eslLeagueId)).thenReturn(emptyList())

        val response = controller.getStandings(eslLeagueId)
        assertEquals(200, response.statusCode.value())
        val rows = response.body
        assertNotNull(rows)
        val teamNames = rows!!.map { it.teamName }
        assertTrue(teamNames.contains("MOUZ"), "ESL Pro League standings must contain MOUZ")
        assertTrue(teamNames.contains("Eternal Fire"), "ESL Pro League standings must contain Eternal Fire")
    }
}
