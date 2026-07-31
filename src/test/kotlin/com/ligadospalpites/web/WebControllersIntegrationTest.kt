package com.ligadospalpites.web

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.groups.infrastructure.persistence.GroupJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.GroupMemberJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.*
import com.ligadospalpites.predictions.infrastructure.persistence.*
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
    private lateinit var seasonRepository: SpringDataSeasonRepository

    @Autowired
    private lateinit var groupRepository: SpringDataGroupRepository

    @Autowired
    private lateinit var groupMemberRepository: SpringDataGroupMemberRepository

    @Autowired
    private lateinit var entitlementRepository: SpringDataUserEntitlementRepository

    @Autowired
    private lateinit var deviceRepository: SpringDataDeviceRepository

    @Autowired
    private lateinit var predictionRepository: com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository

    @Autowired
    private lateinit var specialPredictionRepository: com.ligadospalpites.predictions.infrastructure.persistence.SpringDataSpecialPredictionRepository



    private val testUserId = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
    private val worldCupLeagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")
    private val testSeasonId = UUID.fromString("50c22998-33b2-4d9a-ba02-4be71a1be992")

    @BeforeEach
    fun setUpData() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        // Clear old database records
        predictionRepository.deleteAll()
        specialPredictionRepository.deleteAll()
        groupMemberRepository.deleteAll()
        groupRepository.deleteAll()
        matchRepository.deleteAll()
        seasonRepository.deleteAll()
        leagueRepository.deleteAll()
        sportRepository.deleteAll()
        entitlementRepository.deleteAll()
        userRepository.deleteAll()

        // Create Default Test User
        userRepository.save(UserJpaEntity(id = testUserId, firebaseUid = "firebase-123", email = "vinicius@test.com", name = "Vinicius"))

        // Create Sport and Leagues
        sportRepository.save(SportJpaEntity(id = footballId, name = "Futebol"))
        leagueRepository.save(LeagueJpaEntity(id = worldCupLeagueId, name = "Copa do Mundo", sportId = footballId, isActive = true, logoUrl = "https://media.api-sports.io/football/leagues/1.png"))
        
        // Create Active Season
        seasonRepository.save(
            SeasonJpaEntity(
                id = testSeasonId,
                leagueId = worldCupLeagueId,
                name = "2026",
                startDate = Instant.now().minus(30, ChronoUnit.DAYS),
                endDate = Instant.now().plus(30, ChronoUnit.DAYS),
                isActive = true,
                externalSeasonCode = 2026
            )
        )
    }

    @Test
    fun `should list active leagues grouped by sport`() {
        mockMvc.perform(get("/api/v1/sports/leagues"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Int>(1)))
            .andExpect(jsonPath("$[0].sportName", equalTo("Futebol")))
            .andExpect(jsonPath("$[0].leagues[0].name", equalTo("Copa do Mundo")))
            .andExpect(jsonPath("$[0].leagues[0].logoUrl", equalTo("https://media.api-sports.io/football/leagues/1.png")))
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
                seasonId = testSeasonId,
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
                seasonId = testSeasonId,
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
    fun `should return real user names and avatar urls from database in leaderboard rows`() {
        val groupId = UUID.randomUUID()
        val user1Id = UUID.randomUUID()
        val user2Id = UUID.randomUUID()

        // 1. Save users in database with custom names and avatar URLs
        userRepository.save(
            UserJpaEntity(
                id = user1Id,
                firebaseUid = "firebase-uid-1",
                email = "user1@test.com",
                name = "Vitor Palpiteiro",
                avatarUrl = "https://example.com/vitor.png"
            )
        )
        userRepository.save(
            UserJpaEntity(
                id = user2Id,
                firebaseUid = "firebase-uid-2",
                email = "user2@test.com",
                name = "Juliana Pontas",
                avatarUrl = "https://example.com/juliana.png"
            )
        )

        // 2. Add users to the group and populate score in Redis sorted set
        val leaderboardKey = "leaderboard:group:$groupId:overall"
        redisTemplate.opsForZSet().add(leaderboardKey, user1Id.toString(), 250.0)
        redisTemplate.opsForZSet().add(leaderboardKey, user2Id.toString(), 180.0)

        // 3. Perform GET request and assert real database details are returned
        mockMvc.perform(get("/api/v1/groups/$groupId/leaderboard")
            .param("phase", "overall"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Int>(2)))
            .andExpect(jsonPath("$[0].userId", equalTo(user1Id.toString())))
            .andExpect(jsonPath("$[0].displayName", equalTo("Vitor Palpiteiro")))
            .andExpect(jsonPath("$[0].avatarUrl", equalTo("https://example.com/vitor.png")))
            .andExpect(jsonPath("$[0].score", equalTo(250)))
            .andExpect(jsonPath("$[0].position", equalTo(1)))
            .andExpect(jsonPath("$[1].userId", equalTo(user2Id.toString())))
            .andExpect(jsonPath("$[1].displayName", equalTo("Juliana Pontas")))
            .andExpect(jsonPath("$[1].avatarUrl", equalTo("https://example.com/juliana.png")))
            .andExpect(jsonPath("$[1].score", equalTo(180)))
            .andExpect(jsonPath("$[1].position", equalTo(2)))
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
                seasonId = testSeasonId,
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
    fun `should return paginated news from Redis cache`() {
        val targetSportId = footballId
        val cacheKey = "news:$targetSportId"
        
        // 1. Preparar dados de notícias simulados no Redis (25 artigos)
        val mockArticles = (1..25).map { index ->
            mapOf(
                "title" to "Artigo Sincronizado $index",
                "url" to "https://ge.globo.com/copa/news$index.html",
                "urlToImage" to "https://ge.globo.com/image$index.png",
                "author" to "Liga dos Palpites",
                "description" to "Descrição $index",
                "category" to "Copa do Mundo"
            )
        }
        val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(mockArticles))

        // 2. Chamar a primeira página (page=0, size=10)
        var mvcResult = mockMvc.perform(get("/api/v1/home/news")
            .param("page", "0")
            .param("size", "10")
            .param("sportId", targetSportId.toString()))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", hasSize<Int>(10)))
            .andExpect(jsonPath("$.content[0].title", equalTo("Artigo Sincronizado 1")))
            .andExpect(jsonPath("$.content[9].title", equalTo("Artigo Sincronizado 10")))
            .andExpect(jsonPath("$.page", equalTo(0)))
            .andExpect(jsonPath("$.size", equalTo(10)))
            .andExpect(jsonPath("$.totalElements", equalTo(25)))
            .andExpect(jsonPath("$.totalPages", equalTo(3)))
            .andExpect(jsonPath("$.hasNext", equalTo(true)))

        // 3. Chamar a segunda página (page=1, size=10)
        mvcResult = mockMvc.perform(get("/api/v1/home/news")
            .param("page", "1")
            .param("size", "10")
            .param("sportId", targetSportId.toString()))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", hasSize<Int>(10)))
            .andExpect(jsonPath("$.content[0].title", equalTo("Artigo Sincronizado 11")))
            .andExpect(jsonPath("$.content[9].title", equalTo("Artigo Sincronizado 20")))
            .andExpect(jsonPath("$.page", equalTo(1)))
            .andExpect(jsonPath("$.hasNext", equalTo(true)))

        // 4. Chamar a terceira e última página (page=2, size=10)
        mvcResult = mockMvc.perform(get("/api/v1/home/news")
            .param("page", "2")
            .param("size", "10")
            .param("sportId", targetSportId.toString()))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content", hasSize<Int>(5)))
            .andExpect(jsonPath("$.content[0].title", equalTo("Artigo Sincronizado 21")))
            .andExpect(jsonPath("$.content[4].title", equalTo("Artigo Sincronizado 25")))
            .andExpect(jsonPath("$.page", equalTo(2)))
            .andExpect(jsonPath("$.hasNext", equalTo(false)))
            
        // Limpar Redis após o teste
        redisTemplate.delete(cacheKey)
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
                seasonId = testSeasonId,
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
    fun `should filter dashboard next matches by sportId when sportId parameter is provided`() {
        val otherSportId = UUID.randomUUID()
        sportRepository.save(SportJpaEntity(id = otherSportId, name = "Basquete"))

        // Save a Football Match
        matchRepository.save(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = worldCupLeagueId,
                seasonId = testSeasonId,
                homeTeamName = "Brasil",
                awayTeamName = "Argentina",
                kickoffTime = Instant.now().plus(1, ChronoUnit.DAYS),
                status = MatchStatus.SCHEDULED,
                phase = "Fase de Grupos"
            )
        )

        // Save a Basketball Match
        matchRepository.save(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = otherSportId,
                leagueId = worldCupLeagueId,
                seasonId = testSeasonId,
                homeTeamName = "Lakers",
                awayTeamName = "Celtics",
                kickoffTime = Instant.now().plus(2, ChronoUnit.DAYS),
                status = MatchStatus.SCHEDULED,
                phase = "Temporada Regular"
            )
        )

        // Query only Basketball (otherSportId)
        val mvcResult = mockMvc.perform(get("/api/v1/home/dashboard")
            .header("X-User-Id", testUserId.toString())
            .param("sportId", otherSportId.toString()))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextMatches", hasSize<Int>(1)))
            .andExpect(jsonPath("$.nextMatches[0].homeTeam", equalTo("Lakers")))
    }

    @Test
    fun `should filter dashboard next matches by leagueId when leagueId represents a real league`() {
        val otherLeagueId = UUID.randomUUID()
        leagueRepository.save(LeagueJpaEntity(id = otherLeagueId, name = "Brasileirão", sportId = footballId, isActive = true))

        // Save a World Cup Match
        matchRepository.save(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = worldCupLeagueId,
                seasonId = testSeasonId,
                homeTeamName = "Brasil",
                awayTeamName = "Argentina",
                kickoffTime = Instant.now().plus(1, ChronoUnit.DAYS),
                status = MatchStatus.SCHEDULED,
                phase = "Fase de Grupos"
            )
        )

        // Save a Brasileirao Match
        matchRepository.save(
            MatchJpaEntity(
                id = UUID.randomUUID(),
                sportId = footballId,
                leagueId = otherLeagueId,
                seasonId = testSeasonId,
                homeTeamName = "Flamengo",
                awayTeamName = "Palmeiras",
                kickoffTime = Instant.now().plus(2, ChronoUnit.DAYS),
                status = MatchStatus.SCHEDULED,
                phase = "Rodada 1"
            )
        )

        // Query only Brasileirao (otherLeagueId)
        val mvcResult = mockMvc.perform(get("/api/v1/home/dashboard")
            .header("X-User-Id", testUserId.toString())
            .param("leagueId", otherLeagueId.toString()))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nextMatches", hasSize<Int>(1)))
            .andExpect(jsonPath("$.nextMatches[0].homeTeam", equalTo("Flamengo")))
    }

    @Test
    fun `should return group ranking and points when leagueId represents a group`() {
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupJpaEntity(id = groupId, name = "Grupo dos Amigos", creatorId = testUserId, scoringRulesJson = "{}"))
        groupMemberRepository.save(GroupMemberJpaEntity(groupId = groupId, userId = testUserId, accumulatedPoints = 450))

        // Setup Redis Leaderboard for Group
        val groupLeaderboardKey = "leaderboard:group:$groupId:overall"
        redisTemplate.opsForZSet().add(groupLeaderboardKey, testUserId.toString(), 450.0)

        // Setup Redis Leaderboard for Global
        val globalLeaderboardKey = "leaderboard:global"
        redisTemplate.opsForZSet().add(globalLeaderboardKey, testUserId.toString(), 250.0)

        // Perform GET request with group as leagueId
        val mvcResult = mockMvc.perform(get("/api/v1/home/dashboard")
            .header("X-User-Id", testUserId.toString())
            .param("leagueId", groupId.toString()))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.points", equalTo(450)))
            .andExpect(jsonPath("$.rankGlobal", equalTo(1)))
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
                seasonId = testSeasonId,
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

    @Test
    fun `should get normal and special predictions of the user via GET endpoints`() {
        // Arrange: save some match predictions
        val matchId = UUID.randomUUID()
        matchRepository.save(
            MatchJpaEntity(
                id = matchId,
                sportId = footballId,
                leagueId = worldCupLeagueId,
                seasonId = testSeasonId,
                homeTeamName = "Brasil",
                awayTeamName = "Gana",
                kickoffTime = Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS),
                status = MatchStatus.SCHEDULED
            )
        )

        predictionRepository.save(
            com.ligadospalpites.predictions.infrastructure.persistence.PredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = testUserId,
                matchId = matchId,
                leagueId = worldCupLeagueId,
                predictedHomeScore = 2,
                predictedAwayScore = 0,
                pointsAwarded = 0,
                calculatedAt = null,
                isProcessed = false,
                createdAt = Instant.now()
            )
        )

        // Arrange: save some special predictions
        specialPredictionRepository.save(
            com.ligadospalpites.predictions.infrastructure.persistence.SpecialPredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = testUserId,
                leagueId = worldCupLeagueId,
                type = "CHAMPION",
                predictionValue = "BRA",
                pointsAwarded = 0,
                isProcessed = false,
                createdAt = Instant.now()
            )
        )

        // Act & Assert: Match Predictions GET
        mockMvc.perform(get("/api/v1/predictions")
            .header("X-User-Id", testUserId.toString())
            .param("leagueId", worldCupLeagueId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Int>(1)))
            .andExpect(jsonPath("$[0].predictedHomeScore", equalTo(2)))

        // Act & Assert: Special Predictions GET
        mockMvc.perform(get("/api/v1/special-predictions")
            .header("X-User-Id", testUserId.toString())
            .param("leagueId", worldCupLeagueId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Int>(1)))
            .andExpect(jsonPath("$[0].predictionValue", equalTo("BRA")))
    }

    @Test
    fun `should allow administrators to evaluate special predictions and update rankings`() {
        // Arrange: save a special prediction for user
        specialPredictionRepository.save(
            com.ligadospalpites.predictions.infrastructure.persistence.SpecialPredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = testUserId,
                leagueId = worldCupLeagueId,
                type = "CHAMPION",
                predictionValue = "BRA",
                pointsAwarded = 0,
                isProcessed = false,
                createdAt = Instant.now()
            )
        )

        // Register user in a group to verify propagation
        val groupId = UUID.randomUUID()
        groupRepository.save(GroupJpaEntity(id = groupId, name = "Bolão de Copa", creatorId = testUserId, scoringRulesJson = "{}"))
        groupMemberRepository.save(GroupMemberJpaEntity(groupId = groupId, userId = testUserId, joinedAt = Instant.now(), accumulatedPoints = 0))

        val evaluationPayload = """
            {
                "leagueId": "$worldCupLeagueId",
                "championTeamId": "BRA",
                "secondPlaceTeamId": "FRA",
                "thirdPlaceTeamId": "ARG",
                "fourthPlaceTeamId": "MAR"
            }
        """.trimIndent()

        // Act: Evaluate Special Predictions
        mockMvc.perform(post("/api/v1/internal/special-predictions/evaluate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(evaluationPayload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("SUCCESS")))
            .andExpect(jsonPath("$.predictionsProcessed", equalTo(1)))

        // Verify points awarded
        val updatedPredictions = specialPredictionRepository.findByUserId(testUserId)
        assertEquals(1, updatedPredictions.size)
        val pred = updatedPredictions[0]
        assertTrue(pred.isProcessed)
        assertEquals(50, pred.pointsAwarded)

        // Wait brief moments for asynchronous event listener to update SQL groups
        Thread.sleep(1000)

        val updatedMember = groupMemberRepository.findById(com.ligadospalpites.groups.infrastructure.persistence.GroupMemberId(groupId, testUserId)).orElse(null)
        assertNotNull(updatedMember)
        assertEquals(50, updatedMember.accumulatedPoints)
    }

    @Test
    fun `should isolate predictions and special predictions by active league when leagueId is null`() {
        // Arrange: 
        // 1. Tornar Copa do Mundo inativa
        val worldCupLeague = leagueRepository.findById(worldCupLeagueId).orElse(null)
        assertNotNull(worldCupLeague)
        leagueRepository.save(com.ligadospalpites.sportsfeed.infrastructure.persistence.LeagueJpaEntity(
            id = worldCupLeague.id,
            sportId = worldCupLeague.sportId,
            name = worldCupLeague.name,
            isActive = false
        ))

        // 2. Criar uma nova liga (Brasileirão) e marcá-la como ativa
        val brasileiraoLeagueId = UUID.randomUUID()
        leagueRepository.save(com.ligadospalpites.sportsfeed.infrastructure.persistence.LeagueJpaEntity(
            id = brasileiraoLeagueId,
            sportId = footballId,
            name = "Campeonato Brasileiro",
            isActive = true
        ))

        // 2.1 Criar partidas válidas no banco de dados para evitar violação de Foreign Key
        val worldCupMatchId = UUID.randomUUID()
        matchRepository.save(
            MatchJpaEntity(
                id = worldCupMatchId,
                sportId = footballId,
                leagueId = worldCupLeagueId,
                seasonId = testSeasonId,
                homeTeamName = "Brasil",
                awayTeamName = "França",
                kickoffTime = Instant.now().plus(2, ChronoUnit.HOURS),
                status = MatchStatus.SCHEDULED
            )
        )

        val brasileiraoMatchId = UUID.randomUUID()
        matchRepository.save(
            MatchJpaEntity(
                id = brasileiraoMatchId,
                sportId = footballId,
                leagueId = brasileiraoLeagueId,
                seasonId = testSeasonId,
                homeTeamName = "Palmeiras",
                awayTeamName = "Flamengo",
                kickoffTime = Instant.now().plus(2, ChronoUnit.HOURS),
                status = MatchStatus.SCHEDULED
            )
        )

        // 3. Cadastrar palpites normais para ambas as ligas
        // Palpite Copa do Mundo (Inativa)
        predictionRepository.save(
            PredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = testUserId,
                matchId = worldCupMatchId,
                leagueId = worldCupLeagueId,
                predictedHomeScore = 1,
                predictedAwayScore = 0,
                pointsAwarded = 25,
                isProcessed = true
            )
        )

        // Palpite Brasileirão (Ativa)
        predictionRepository.save(
            PredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = testUserId,
                matchId = brasileiraoMatchId,
                leagueId = brasileiraoLeagueId,
                predictedHomeScore = 3,
                predictedAwayScore = 2,
                pointsAwarded = 10,
                isProcessed = true
            )
        )

        // 4. Cadastrar superpalpites para ambas as ligas
        // Superpalpite Copa (Inativa)
        specialPredictionRepository.save(
            com.ligadospalpites.predictions.infrastructure.persistence.SpecialPredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = testUserId,
                leagueId = worldCupLeagueId,
                type = "CHAMPION",
                predictionValue = "ARG",
                pointsAwarded = 50,
                isProcessed = true
            )
        )

        // Superpalpite Brasileirão (Ativa)
        specialPredictionRepository.save(
            com.ligadospalpites.predictions.infrastructure.persistence.SpecialPredictionJpaEntity(
                id = UUID.randomUUID(),
                userId = testUserId,
                leagueId = brasileiraoLeagueId,
                type = "CHAMPION",
                predictionValue = "PAL",
                pointsAwarded = 15,
                isProcessed = true
            )
        )

        // Act & Assert: Match Predictions GET (Sem leagueId)
        // Deve retornar apenas o palpite do Brasileirão (Ativo)
        mockMvc.perform(get("/api/v1/predictions")
            .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Int>(1)))
            .andExpect(jsonPath("$[0].leagueId", equalTo(brasileiraoLeagueId.toString())))
            .andExpect(jsonPath("$[0].predictedHomeScore", equalTo(3)))

        // Act & Assert: Special Predictions GET (Sem leagueId)
        // Deve retornar apenas o superpalpite do Brasileirão (Ativo)
        mockMvc.perform(get("/api/v1/special-predictions")
            .header("X-User-Id", testUserId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Int>(1)))
            .andExpect(jsonPath("$[0].leagueId", equalTo(brasileiraoLeagueId.toString())))
            .andExpect(jsonPath("$[0].predictionValue", equalTo("PAL")))

        // Act & Assert: Home Dashboard (Sem leagueId)
        // Deve retornar os pontos da liga ativa (Brasileirão = 10 do palpite + 15 do superpalpite = 25 pontos)
        val mvcResult = mockMvc.perform(get("/api/v1/home/dashboard")
            .header("X-User-Id", testUserId.toString()))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.points", equalTo(25)))
    }
}
