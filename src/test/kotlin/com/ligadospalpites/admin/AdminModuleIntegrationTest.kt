package com.ligadospalpites.admin

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.admin.infrastructure.persistence.repositories.SpringDataAuditLogRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.LeagueJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataSportRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SportJpaEntity
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import com.ligadospalpites.users.infrastructure.persistence.UserJpaEntity
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class AdminModuleIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var auditLogRepository: SpringDataAuditLogRepository

    @Autowired
    private lateinit var leagueRepository: SpringDataLeagueRepository

    @Autowired
    private lateinit var sportRepository: SpringDataSportRepository

    @Autowired
    private lateinit var userRepository: SpringDataUserRepository

    private val adminApiKey = "lp_ws_live_secret_key_2026_x89f"

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(wac)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
            .build()
        auditLogRepository.deleteAll()
    }

    @Test
    fun `should return 401 Unauthorized when admin api key header is missing or invalid`() {
        // Missing key
        mockMvc.perform(get("/api/v1/admin/leagues/stats"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error", equalTo("UNAUTHORIZED")))

        // Invalid key
        mockMvc.perform(get("/api/v1/admin/leagues/stats")
            .header("X-Admin-Api-Key", "invalid_key"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should allow access with X-Admin-Api-Key or X-Workspace-Api-Key`() {
        mockMvc.perform(get("/api/v1/admin/leagues/stats")
            .header("X-Admin-Api-Key", adminApiKey))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/workspace/leagues/stats")
            .header("X-Workspace-Api-Key", adminApiKey))
            .andExpect(status().isOk)
    }

    @Test
    fun `should fetch league stats and update league status with audit log`() {
        val sportId = UUID.randomUUID()
        sportRepository.save(SportJpaEntity(id = sportId, name = "Futebol Test"))

        val leagueId = UUID.randomUUID()
        leagueRepository.save(LeagueJpaEntity(id = leagueId, name = "Liga Teste Admin", sportId = sportId, isActive = true))

        // Get Stats
        mockMvc.perform(get("/api/v1/admin/leagues/stats")
            .header("X-Admin-Api-Key", adminApiKey))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.activeLeagues", greaterThanOrEqualTo(1)))

        // Patch League Status to INACTIVE
        val patchPayload = """{ "status": "INACTIVE" }"""
        mockMvc.perform(patch("/api/v1/admin/leagues/$leagueId/status")
            .header("X-Admin-Api-Key", adminApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(patchPayload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))
            .andExpect(jsonPath("$.status", equalTo("INACTIVE")))

        // Verify Audit Log written
        val logs = auditLogRepository.findAll()
        val statusLog = logs.find { it.action == "UPDATE_LEAGUE_STATUS" && it.targetId == leagueId.toString() }
        assertNotNull(statusLog)
    }

    @Test
    fun `should grant user plan and log audit event`() {
        val userId = UUID.randomUUID()
        userRepository.save(UserJpaEntity(id = userId, firebaseUid = "fb-admin-test-$userId", email = "user@admin.com", name = "Admin User"))

        val payload = """
            {
                "plan": "PREMIUM",
                "durationDays": 30,
                "reason": "Cortesia para teste"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/admin/users/$userId/grant-plan")
            .header("X-Admin-Api-Key", adminApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))

        // Verify Audit Log
        val logs = auditLogRepository.findAll()
        val grantLog = logs.find { it.action == "GRANT_USER_PLAN" && it.targetId == userId.toString() }
        assertNotNull(grantLog)
    }

    @Test
    fun `should dispatch notification and return health telemetry`() {
        val dispatchPayload = """
            {
                "target": "GLOBAL",
                "title": "Alerta Admin",
                "content": "Mensagem de teste"
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/admin/notifications/dispatch")
            .header("X-Admin-Api-Key", adminApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(dispatchPayload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success", equalTo(true)))

        // Get Health
        mockMvc.perform(get("/api/v1/admin/health")
            .header("X-Admin-Api-Key", adminApiKey))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", notNullValue()))
            .andExpect(jsonPath("$.providers.SOCCER.status", equalTo("HEALTHY")))

        // Get Audit Logs
        mockMvc.perform(get("/api/v1/admin/audit-logs")
            .header("X-Admin-Api-Key", adminApiKey))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.logs", notNullValue()))
    }
}
