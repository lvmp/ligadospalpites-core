package com.ligadospalpites.web

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.payments.domain.models.SubscriptionStatus
import com.ligadospalpites.payments.infrastructure.persistence.SpringDataRevenueCatEventRepository
import com.ligadospalpites.payments.infrastructure.persistence.SpringDataSubscriptionRepository
import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import com.ligadospalpites.users.infrastructure.persistence.UserJpaEntity
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.*

class RevenueCatWebhookIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: SpringDataUserRepository

    @Autowired
    private lateinit var entitlementRepository: SpringDataUserEntitlementRepository

    @Autowired
    private lateinit var subscriptionRepository: SpringDataSubscriptionRepository

    @Autowired
    private lateinit var eventRepository: SpringDataRevenueCatEventRepository

    private val testUserId = UUID.fromString("0a359871-3329-4979-9944-772fa8ea652d")
    private val webhookSecret = "supersecret" // Match default value in Controller fallback

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        
        // Limpar dados para isolamento dos testes
        subscriptionRepository.deleteAll()
        eventRepository.deleteAll()
        entitlementRepository.deleteAll()
        userRepository.deleteAll()

        // Criar usuário padrão de teste no banco
        userRepository.save(
            UserJpaEntity(
                id = testUserId,
                firebaseUid = "firebase-test-revenuecat",
                email = "user-rc@test.com",
                name = "RevenueCat User"
            )
        )
    }

    @Test
    fun `should return 401 Unauthorized when security token is missing or invalid`() {
        val payload = """
            {
              "api_version": "1.0",
              "event": {
                "id": "event-111",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "$testUserId",
                "product_id": "com.ligadospalpites.premium.monthly",
                "store": "PLAY_STORE"
              }
            }
        """.trimIndent()

        // Sem Header
        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isUnauthorized)

        // Header Inválido
        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer invalid-token-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should return 200 OK when user does not exist in local database`() {
        val unknownUserId = UUID.randomUUID()
        val payload = """
            {
              "api_version": "1.0",
              "event": {
                "id": "event-222",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "$unknownUserId",
                "product_id": "com.ligadospalpites.premium.monthly",
                "store": "PLAY_STORE"
              }
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer $webhookSecret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("SUCCESS")))

        // Evento deve ser persistido como log de auditoria
        val savedEvent = eventRepository.findById("event-222").orElse(null)
        assertNotNull(savedEvent)
        assertEquals("INITIAL_PURCHASE", savedEvent.type)
        assertEquals(unknownUserId.toString(), savedEvent.appUserId)

        // Assinatura não deve ser criada
        assertEquals(0, subscriptionRepository.count())
    }

    @Test
    fun `should process INITIAL_PURCHASE and create active entitlement and subscription`() {
        val eventId = "event-333"
        val futureExpirationTimeMs = Instant.now().plusSeconds(86400 * 30).toEpochMilli() // 30 dias

        val payload = """
            {
              "api_version": "1.0",
              "event": {
                "id": "$eventId",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "$testUserId",
                "product_id": "com.ligadospalpites.premium.monthly",
                "entitlement_id": "premium",
                "expiration_at_ms": $futureExpirationTimeMs,
                "store": "PLAY_STORE"
              }
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer $webhookSecret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status", equalTo("SUCCESS")))

        // 1. Verificar persistência de evento
        val savedEvent = eventRepository.findById(eventId).orElse(null)
        assertNotNull(savedEvent)

        // 2. Verificar se o Entitlement correspondente foi criado no banco
        val entitlements = entitlementRepository.findByUserId(testUserId)
        assertEquals(1, entitlements.size)
        val entitlement = entitlements.first()
        assertEquals(EntitlementType.PREMIUM, entitlement.entitlementType)
        assertNull(entitlement.sportId)
        assertNotNull(entitlement.expiresAt)
        assertTrue(entitlement.expiresAt!!.isAfter(Instant.now()))

        // 3. Verificar criação de assinatura
        val subscription = subscriptionRepository.findByTransactionId(eventId).orElse(null)
        assertNotNull(subscription)
        assertEquals(SubscriptionStatus.ACTIVE, subscription.status)
        assertEquals("com.ligadospalpites.premium.monthly", subscription.productId)
    }

    @Test
    fun `should process CANCELLATION without immediately revoking active entitlement`() {
        val purchaseEventId = "event-444"
        val futureExpirationTimeMs = Instant.now().plusSeconds(86400 * 5).toEpochMilli() // 5 dias ainda válidos

        // 1. Simular Compra Inicial
        val purchasePayload = """
            {
              "api_version": "1.0",
              "event": {
                "id": "$purchaseEventId",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "$testUserId",
                "product_id": "com.ligadospalpites.premium.monthly",
                "entitlement_id": "premium",
                "expiration_at_ms": $futureExpirationTimeMs,
                "store": "PLAY_STORE"
              }
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer $webhookSecret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(purchasePayload))
            .andExpect(status().isOk)

        // 2. Enviar Cancelamento (CANCELLATION)
        val cancellationEventId = "event-444-cancel"
        val cancellationPayload = """
            {
              "api_version": "1.0",
              "event": {
                "id": "$cancellationEventId",
                "type": "CANCELLATION",
                "app_user_id": "$testUserId",
                "product_id": "com.ligadospalpites.premium.monthly",
                "store": "PLAY_STORE"
              }
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer $webhookSecret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(cancellationPayload))
            .andExpect(status().isOk)

        // 3. Garantir que a assinatura foi marcada como CANCELLED
        val cancelledSub = subscriptionRepository.findByTransactionId(cancellationEventId).orElse(null)
        assertNotNull(cancelledSub)
        assertEquals(SubscriptionStatus.CANCELLED, cancelledSub.status)

        // 4. Garantir que o entitlement CONTINUA ativo (expires_at continua no futuro)
        val entitlements = entitlementRepository.findByUserId(testUserId)
        assertEquals(1, entitlements.size)
        val ent = entitlements.first()
        assertTrue(ent.expiresAt!!.isAfter(Instant.now()), "O direito de acesso deve continuar ativo até expirar naturalmente")
    }

    @Test
    fun `should revoke access immediately on EXPIRATION event`() {
        val purchaseEventId = "event-555"
        val futureExpirationTimeMs = Instant.now().plusSeconds(86400 * 10).toEpochMilli()

        // 1. Compra Inicial
        val purchasePayload = """
            {
              "api_version": "1.0",
              "event": {
                "id": "$purchaseEventId",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "$testUserId",
                "product_id": "com.ligadospalpites.premium.monthly",
                "entitlement_id": "premium",
                "expiration_at_ms": $futureExpirationTimeMs,
                "store": "PLAY_STORE"
              }
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer $webhookSecret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(purchasePayload))
            .andExpect(status().isOk)

        // 2. Receber Expiração (EXPIRATION)
        val expiredTimeMs = Instant.now().minusSeconds(10).toEpochMilli() // Expirou há 10 segundos
        val expirationEventId = "event-555-expire"
        val expirationPayload = """
            {
              "api_version": "1.0",
              "event": {
                "id": "$expirationEventId",
                "type": "EXPIRATION",
                "app_user_id": "$testUserId",
                "product_id": "com.ligadospalpites.premium.monthly",
                "entitlement_id": "premium",
                "expiration_at_ms": $expiredTimeMs,
                "store": "PLAY_STORE"
              }
            }
        """.trimIndent()

        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer $webhookSecret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(expirationPayload))
            .andExpect(status().isOk)

        // 3. Garantir que a assinatura foi marcada como EXPIRED
        val expiredSub = subscriptionRepository.findByTransactionId(expirationEventId).orElse(null)
        assertNotNull(expiredSub)
        assertEquals(SubscriptionStatus.EXPIRED, expiredSub.status)

        // 4. Garantir que o entitlement foi marcado como expirado no banco (expires_at no passado)
        val entitlements = entitlementRepository.findByUserId(testUserId)
        assertEquals(1, entitlements.size)
        val ent = entitlements.first()
        assertTrue(ent.expiresAt!!.isBefore(Instant.now()), "O direito de acesso deve ser revogado (expires_at no passado)")
    }

    @Test
    fun `should guarantee idempotency when processing duplicate events`() {
        val eventId = "event-idempotent-100"
        val futureExpirationTimeMs = Instant.now().plusSeconds(86400 * 30).toEpochMilli()

        val payload = """
            {
              "api_version": "1.0",
              "event": {
                "id": "$eventId",
                "type": "INITIAL_PURCHASE",
                "app_user_id": "$testUserId",
                "product_id": "com.ligadospalpites.premium.monthly",
                "entitlement_id": "premium",
                "expiration_at_ms": $futureExpirationTimeMs,
                "store": "PLAY_STORE"
              }
            }
        """.trimIndent()

        // Primeira chamada
        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer $webhookSecret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isOk)

        assertEquals(1, eventRepository.count())
        assertEquals(1, subscriptionRepository.count())

        // Segunda chamada (duplicada)
        mockMvc.perform(post("/api/v1/payments/revenuecat/webhook")
            .header("Authorization", "Bearer $webhookSecret")
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isOk)

        // Nada deve ter mudado no banco além da garantia do retorno 200 OK
        assertEquals(1, eventRepository.count())
        assertEquals(1, subscriptionRepository.count())
    }
}
