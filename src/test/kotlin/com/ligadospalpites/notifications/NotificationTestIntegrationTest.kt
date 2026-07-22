package com.ligadospalpites.notifications

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.groups.infrastructure.persistence.GroupJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.GroupMemberJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupRepository
import com.ligadospalpites.notifications.domain.ports.Device
import com.ligadospalpites.notifications.domain.ports.DeviceRepository
import com.ligadospalpites.predictions.infrastructure.persistence.PredictionJpaEntity
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.*

class NotificationTestIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var deviceRepository: DeviceRepository

    @Autowired
    private lateinit var groupRepository: SpringDataGroupRepository

    @Autowired
    private lateinit var groupMemberRepository: SpringDataGroupMemberRepository

    @Autowired
    private lateinit var matchRepository: SpringDataMatchRepository

    @Autowired
    private lateinit var predictionRepository: SpringDataPredictionRepository

    private val adminSecret = "teste-push-secret-123"

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()

        // Clean tables
        predictionRepository.deleteAll()
        matchRepository.deleteAll()
        groupMemberRepository.deleteAll()
        groupRepository.deleteAll()

        // Clear devices by deleting them through repository if supported or direct SQL if needed
        // Since deviceRepository doesn't have a deleteAll, we can delete them one by one if found
        deviceRepository.findAll().forEach { deviceRepository.delete(it) }
    }

    @Test
    fun `should return 401 Unauthorized when admin secret is missing or invalid`() {
        val payload = """
            {
                "target": "ALL",
                "title": "Aviso Geral",
                "content": "Aviso Importante!"
            }
        """.trimIndent()

        // No header
        mockMvc.perform(post("/api/v1/notifications/dispatch")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isUnauthorized)

        // Invalid header
        mockMvc.perform(post("/api/v1/notifications/dispatch")
            .header("X-Admin-Secret", "errado-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should return 400 Bad Request when payload is invalid`() {
        // Target invalid
        mockMvc.perform(post("/api/v1/notifications/dispatch")
            .header("X-Admin-Secret", adminSecret)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "target": "INVALID_TARGET",
                    "title": "Aviso",
                    "content": "Conteudo"
                }
            """.trimIndent()))
            .andExpect(status().isBadRequest)

        // Missing targetId for USER target
        mockMvc.perform(post("/api/v1/notifications/dispatch")
            .header("X-Admin-Secret", adminSecret)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "target": "USER",
                    "targetId": "",
                    "title": "Aviso",
                    "content": "Conteudo"
                }
            """.trimIndent()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should successfully accept push dispatch for single USER target`() {
        val user = userRepository.save(User(UUID.randomUUID(), "firebase-uid-u1", "u1@test.com", "User One"))
        deviceRepository.save(Device(id = UUID.randomUUID(), userId = user.id, deviceId = UUID.randomUUID(), fcmToken = "token-u1", deviceType = "ANDROID"))

        val payload = """
            {
                "target": "USER",
                "targetId": "${user.id}",
                "title": "Alerta Individual",
                "content": "Olá, você recebeu uma notificação!",
                "channels": ["PUSH"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/notifications/dispatch")
            .header("X-Admin-Secret", adminSecret)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `should successfully accept push dispatch for LEAGUE target`() {
        val user = userRepository.save(User(UUID.randomUUID(), "firebase-uid-u2", "u2@test.com", "User Two"))
        deviceRepository.save(Device(id = UUID.randomUUID(), userId = user.id, deviceId = UUID.randomUUID(), fcmToken = "token-u2", deviceType = "ANDROID"))

        val group = groupRepository.save(GroupJpaEntity(UUID.randomUUID(), "Minha Liga", user.id, "{}"))
        groupMemberRepository.save(GroupMemberJpaEntity(group.id, user.id))

        val payload = """
            {
                "target": "LEAGUE",
                "targetId": "${group.id}",
                "title": "Alerta da Liga",
                "content": "Seus rankings foram atualizados!",
                "channels": ["PUSH"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/notifications/dispatch")
            .header("X-Admin-Secret", adminSecret)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `should successfully accept push dispatch for SPORT target`() {
        val user = userRepository.save(User(UUID.randomUUID(), "firebase-uid-u3", "u3@test.com", "User Three"))
        deviceRepository.save(Device(id = UUID.randomUUID(), userId = user.id, deviceId = UUID.randomUUID(), fcmToken = "token-u3", deviceType = "ANDROID"))

        val sportId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
        val leagueId = UUID.fromString("e7b0a8f9-4b2e-4b67-8890-a54b3d7c588e")
        val seasonId = UUID.fromString("50c22998-33b2-4d9a-ba02-4be71a1be992")

        val match = matchRepository.save(MatchJpaEntity(
            id = UUID.randomUUID(),
            sportId = sportId,
            leagueId = leagueId,
            seasonId = seasonId
        ))

        predictionRepository.save(PredictionJpaEntity(
            id = UUID.randomUUID(),
            userId = user.id,
            matchId = match.id,
            leagueId = match.leagueId
        ))

        val payload = """
            {
                "target": "SPORT",
                "targetId": "$sportId",
                "title": "Alerta de Esporte",
                "content": "Nova rodada de Futebol disponível!",
                "channels": ["PUSH"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/notifications/dispatch")
            .header("X-Admin-Secret", adminSecret)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `should successfully accept push dispatch for ALL target`() {
        val user1 = userRepository.save(User(UUID.randomUUID(), "firebase-uid-u4", "u4@test.com", "User Four"))
        val user2 = userRepository.save(User(UUID.randomUUID(), "firebase-uid-u5", "u5@test.com", "User Five"))

        deviceRepository.save(Device(id = UUID.randomUUID(), userId = user1.id, deviceId = UUID.randomUUID(), fcmToken = "token-u4", deviceType = "ANDROID"))
        deviceRepository.save(Device(id = UUID.randomUUID(), userId = user2.id, deviceId = UUID.randomUUID(), fcmToken = "token-u5", deviceType = "IOS"))

        val payload = """
            {
                "target": "ALL",
                "title": "Alerta Geral",
                "content": "Bem-vindos à nova temporada!",
                "channels": ["PUSH"]
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/notifications/dispatch")
            .header("X-Admin-Secret", adminSecret)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isAccepted)
    }
}
