package com.ligadospalpites.web

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.groups.infrastructure.persistence.GroupJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.GroupMemberJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.*
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import com.ligadospalpites.users.infrastructure.persistence.UserEntitlementJpaEntity
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import com.ligadospalpites.users.infrastructure.persistence.UserJpaEntity
import com.ligadospalpites.notifications.infrastructure.persistence.SpringDataDeviceRepository
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.web.context.WebApplicationContext
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class WebControllersIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: SpringDataUserRepository

    @Autowired
    private lateinit var sportRepository: SpringDataSportRepository

    @Autowired
    private lateinit var leagueRepository: SpringDataLeagueRepository

    @Autowired
    private lateinit var matchRepository: SpringDataMatchRepository

    @Autowired
    private lateinit var groupRepository: SpringDataGroupRepository

    @Autowired
    private lateinit var groupMemberRepository: SpringDataGroupMemberRepository

    @Autowired
    private lateinit var entitlementRepository: SpringDataUserEntitlementRepository

    @Autowired
    private lateinit var deviceRepository: SpringDataDeviceRepository

    private val testUserId = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val worldCupLeagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")

    @BeforeEach
    fun setUpData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        // Clear old database records
        groupMemberRepository.deleteAll()
        groupRepository.deleteAll()
        matchRepository.deleteAll()
        leagueRepository.deleteAll()
        sportRepository.deleteAll()
        entitlementRepository.deleteAll()
        userRepository.deleteAll()

        // Create Default Test User
        userRepository.save(UserJpaEntity(id = testUserId, firebaseUid = "firebase-123", email = "vinicius@test.com", name = "Vinicius"))

        // Create Sport and Leagues
        sportRepository.save(SportJpaEntity(id = footballId, name = "Futebol"))
        leagueRepository.save(LeagueJpaEntity(id = worldCupLeagueId, name = "Copa do Mundo", sportId = footballId, isActive = true))
    }

    @Test
    fun `should list active leagues grouped by sport`() {
        mockMvc.perform(get("/api/v1/sports/leagues"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Int>(1)))
            .andExpect(jsonPath("$[0].sportName", equalTo("Futebol")))
            .andExpect(jsonPath("$[0].leagues[0].name", equalTo("Copa do Mundo")))
    }

    @Test
    fun `should restrict premium sports to users with MULTI_SPORT entitlement`() {
        val premiumSportId = UUID.randomUUID()
        val premiumLeagueId = UUID.randomUUID()

        sportRepository.save(SportJpaEntity(id = premiumSportId, name = "Fórmula 1"))
        leagueRepository.save(LeagueJpaEntity(id = premiumLeagueId, name = "F1 GP", sportId = premiumSportId, isActive = true))

        // Without entitlement, Formula 1 should be locked
        mockMvc.perform(get("/api/v1/sports/fixtures")
            .header("X-User-Id", testUserId.toString())
            .param("sportId", premiumSportId.toString()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error", equalTo("SPORT_LOCKED")))

        // Grant entitlement (PREMIUM unlocks all sports)
        entitlementRepository.save(
            UserEntitlementJpaEntity(
                id = UUID.randomUUID(),
                userId = testUserId,
                entitlementType = com.ligadospalpites.users.domain.models.EntitlementType.PREMIUM
            )
        )

        // Now it should access successfully
        mockMvc.perform(get("/api/v1/sports/fixtures")
            .header("X-User-Id", testUserId.toString())
            .param("sportId", premiumSportId.toString()))
            .andExpect(status().isOk)
    }

    @Test
    fun `should prevent predictions after kickoff time`() {
        val pastMatchId = UUID.randomUUID()
        matchRepository.save(
            MatchJpaEntity(
                id = pastMatchId,
                sportId = footballId,
                leagueId = worldCupLeagueId,
                homeTeamName = "Brasil",
                awayTeamName = "França",
                kickoffTime = Instant.now().minus(10, ChronoUnit.MINUTES),
                status = MatchStatus.SCHEDULED
            )
        )

        val payload = """
            {
                "matchId": "$pastMatchId",
                "predictedHomeScore": 2,
                "predictedAwayScore": 1
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/predictions")
            .header("X-User-Id", testUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error", equalTo("PREDICTION_LOCKED")))
    }

    @Test
    fun `should accept and update match prediction before kickoff time`() {
        val futureMatchId = UUID.randomUUID()
        matchRepository.save(
            MatchJpaEntity(
                id = futureMatchId,
                sportId = footballId,
                leagueId = worldCupLeagueId,
                homeTeamName = "Brasil",
                awayTeamName = "Alemanha",
                kickoffTime = Instant.now().plus(2, ChronoUnit.HOURS),
                status = MatchStatus.SCHEDULED
            )
        )

        val payload = """
            {
                "matchId": "$futureMatchId",
                "predictedHomeScore": 3,
                "predictedAwayScore": 1
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/predictions")
            .header("X-User-Id", testUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("SUCCESS")))
    }

    @Test
    fun `should allow group creator to expel members and restrict non-admin from doing so`() {
        val groupId = UUID.randomUUID()
        val memberUserId = UUID.randomUUID()

        // Create Member User first to respect ForeignKey constraint
        userRepository.save(UserJpaEntity(id = memberUserId, firebaseUid = "member-123", email = "member@test.com", name = "Member"))

        // Test user is creator
        groupRepository.save(GroupJpaEntity(id = groupId, name = "Bolão Beneficente", creatorId = testUserId, scoringRulesJson = "{}"))
        groupMemberRepository.save(GroupMemberJpaEntity(groupId = groupId, userId = memberUserId, accumulatedPoints = 10))

        // Non-admin tries to expel
        mockMvc.perform(delete("/api/v1/groups/$groupId/members/$memberUserId")
            .header("X-User-Id", memberUserId.toString()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error", equalTo("NOT_GROUP_ADMIN")))

        // Admin expels
        mockMvc.perform(delete("/api/v1/groups/$groupId/members/$memberUserId")
            .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("SUCCESS")))
    }

    @Test
    fun `should aggregate dashboard items correctly`() {
        // Prepare some data
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupJpaEntity(id = groupId, name = "Turma do Futebol", creatorId = testUserId, scoringRulesJson = "{}"))
        groupMemberRepository.save(GroupMemberJpaEntity(groupId = groupId, userId = testUserId, accumulatedPoints = 250))

        // Save a future match
        matchRepository.save(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = worldCupLeagueId,
                homeTeamName = "Brasil",
                awayTeamName = "Argentina",
                kickoffTime = Instant.now().plus(1, ChronoUnit.DAYS),
                status = MatchStatus.SCHEDULED,
                phase = "Fase de Grupos"
            )
        )

        val mvcResult = mockMvc.perform(get("/api/v1/home/dashboard")
            .header("X-User-Id", testUserId.toString()))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId", equalTo(testUserId.toString())))
            .andExpect(jsonPath("$.nextMatches[0].homeTeam", equalTo("Brasil")))
            .andExpect(jsonPath("$.nextMatches[0].phase", equalTo("Fase de Grupos")))
            .andExpect(jsonPath("$.myGroupsHighlight[0].groupName", equalTo("Turma do Futebol")))
            .andExpect(jsonPath("$.news[0].title", containsString("Brasil se prepara")))
    }

    @Test
    fun `should resolve user when X-User-Id is a Firebase UID instead of standard UUID`() {
        val firebaseUid = "OiItOeIXzxa6u3j28LS6HKIpxSe2"

        // Save a future match so the dashboard response has enough matches
        matchRepository.save(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = worldCupLeagueId,
                homeTeamName = "Brasil",
                awayTeamName = "Argentina",
                kickoffTime = Instant.now().plus(1, ChronoUnit.DAYS),
                status = MatchStatus.SCHEDULED,
                phase = "Fase de Grupos"
            )
        )

        val mvcResult = mockMvc.perform(get("/api/v1/home/dashboard")
            .header("X-User-Id", firebaseUid))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId", notNullValue()))
    }

    @Test
    fun `should register and update device fcm token successfully`() {
        val deviceId = UUID.randomUUID().toString()
        val fcmToken = "test-fcm-token-12345"

        val payload = """
            {
              "deviceId": "$deviceId",
              "fcmToken": "$fcmToken",
              "deviceType": "ANDROID",
              "receiveEmail": true,
              "receiveSms": false,
              "receivePush": true
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/notifications/devices/register")
            .header("X-User-Id", testUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("SUCCESS")))

        // Verify it persists in database
        val registered = deviceRepository.findByDeviceId(UUID.fromString(deviceId))
        assertNotNull(registered)
        assertEquals(fcmToken, registered?.fcmToken)
        assertEquals("ANDROID", registered?.deviceType)
    }

    @Test
    fun `should reuse and update existing token on new device to prevent duplicates`() {
        val deviceId1 = UUID.randomUUID()
        val deviceId2 = UUID.randomUUID()
        val fcmToken = "shared-token-xyz"

        // Register first device
        val payload1 = """
            {
              "deviceId": "$deviceId1",
              "fcmToken": "$fcmToken",
              "deviceType": "IOS",
              "receiveEmail": true,
              "receiveSms": false,
              "receivePush": true
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/notifications/devices/register")
            .header("X-User-Id", testUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload1))
            .andExpect(status().isOk)

        assertNotNull(deviceRepository.findByDeviceId(deviceId1))

        // Register second device with SAME token (deviceId changed)
        val payload2 = """
            {
              "deviceId": "$deviceId2",
              "fcmToken": "$fcmToken",
              "deviceType": "IOS",
              "receiveEmail": true,
              "receiveSms": false,
              "receivePush": true
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/notifications/devices/register")
            .header("X-User-Id", testUserId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload2))
            .andExpect(status().isOk)

        // Verify first device record was removed to maintain token uniqueness, and second device is saved
        assertNull(deviceRepository.findByDeviceId(deviceId1))
        assertNotNull(deviceRepository.findByDeviceId(deviceId2))
    }

    @Test
    fun `should return brackets grouped by phase correctly including DECIMOSEXTO and ROUND_OF_32`() {
        // Save a match for Dezesseis-avos de Final
        matchRepository.save(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = worldCupLeagueId,
                homeTeamName = "México (Dezesseis)",
                awayTeamName = "Suécia (Dezesseis)",
                kickoffTime = Instant.now().plus(5, ChronoUnit.DAYS),
                status = MatchStatus.SCHEDULED,
                phase = "Dezesseis-avos de Final"
            )
        )

        mockMvc.perform(get("/api/v1/sports/brackets")
            .header("X-User-Id", testUserId.toString())
            .param("leagueId", worldCupLeagueId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.phases.DECIMOSEXTO[0].homeTeam", equalTo("México (Dezesseis)")))
            .andExpect(jsonPath("$.phases.ROUND_OF_32[0].homeTeam", equalTo("México (Dezesseis)")))
    }
}
