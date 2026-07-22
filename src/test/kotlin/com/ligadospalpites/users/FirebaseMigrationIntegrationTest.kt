package com.ligadospalpites.users

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupRepository
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

class FirebaseMigrationIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: SpringDataUserRepository

    @Autowired
    private lateinit var groupRepository: SpringDataGroupRepository

    @Autowired
    private lateinit var groupMemberRepository: SpringDataGroupMemberRepository

    @Autowired
    private lateinit var predictionRepository: SpringDataPredictionRepository

    @Autowired
    private lateinit var matchRepository: SpringDataMatchRepository

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()

        // Limpa tabelas de teste antes de rodar a migração
        predictionRepository.deleteAll()
        groupMemberRepository.deleteAll()
        groupRepository.deleteAll()
        matchRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `deve executar a migracao simulada de ponta a ponta gravando no Postgres e populando Redis`() {
        // Executa o endpoint de migração simulada
        mockMvc.perform(post("/api/v1/internal/migrations/firebase")
            .param("simulate", "true")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.simulated").value(true))
            .andExpect(jsonPath("$.usersProcessed").value(5))
            .andExpect(jsonPath("$.usersCreated").value(5))
            .andExpect(jsonPath("$.groupsMigrated").value(2))
            .andExpect(jsonPath("$.groupMembersMigrated").value(10)) // 2 grupos x 5 membros
            .andExpect(jsonPath("$.predictionsMigrated").value(10)) // 5 membros x 2 palpites

        // Validar integridade dos usuários salvos no Postgres
        val users = userRepository.findAll()
        assertEquals(5, users.size)
        val carlos = users.find { it.name == "Carlos Silva" }
        assertNotNull(carlos)
        assertEquals("fb-uid-sim-1", carlos?.firebaseUid)

        // Validar integridade dos grupos salvos no Postgres
        val groups = groupRepository.findAll()
        assertEquals(2, groups.size)
        assertTrue(groups.any { it.name == "Liga dos Campeões Premium" })

        // Validar integridade das associações em tbl_group_members
        val members = groupMemberRepository.findAll()
        assertEquals(10, members.size)

        // Validar integridade dos palpites em tbl_predictions
        val predictions = predictionRepository.findAll()
        assertEquals(10, predictions.size)
        val firstPred = predictions.first()
        assertNotNull(firstPred.updatedAt)
        assertNotNull(firstPred.createdAt)
        assertEquals(firstPred.createdAt, firstPred.updatedAt) // Mesmo timestamp conforme especificado

        // Validar se rankings foram salvos nos Sorted Sets correspondentes no Redis
        val pgGroupId = UUID.nameUUIDFromBytes("group_simulated_premium".toByteArray())
        val carlosId = carlos!!.id

        // Ranking Geral do Grupo
        val overallKey = "leaderboard:group:$pgGroupId:overall"
        val overallScore = redisTemplate.opsForZSet().score(overallKey, carlosId.toString())
        assertNotNull(overallScore)
        assertEquals(10.0, overallScore) // Score do Carlos para simulação é 10

        // Ranking da Fase de Grupos
        val stageKey = "leaderboard:group:$pgGroupId:group-stage"
        val stageScore = redisTemplate.opsForZSet().score(stageKey, carlosId.toString())
        assertNotNull(stageScore)
        assertEquals(5.0, stageScore) // Stage score do Carlos para simulação é 5

        // Ranking do Mata-Mata
        val knockoutKey = "leaderboard:group:$pgGroupId:knockout"
        val knockoutScore = redisTemplate.opsForZSet().score(knockoutKey, carlosId.toString())
        assertNotNull(knockoutScore)
        assertEquals(5.0, knockoutScore) // Knockout score do Carlos para simulação é 5

        // Ranking Global
        val globalKey = "leaderboard:global"
        val globalScore = redisTemplate.opsForZSet().score(globalKey, carlosId.toString())
        assertNotNull(globalScore)
        assertEquals(20.0, globalScore) // 10 de cada grupo (Premium + Friends) = 20 acumulados
    }
}
