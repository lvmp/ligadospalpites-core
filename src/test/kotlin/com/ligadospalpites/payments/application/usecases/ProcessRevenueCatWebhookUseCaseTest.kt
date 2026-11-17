package com.ligadospalpites.payments.application.usecases

import com.ligadospalpites.payments.domain.models.RevenueCatEvent
import com.ligadospalpites.payments.domain.models.Subscription
import com.ligadospalpites.payments.domain.models.SubscriptionStatus
import com.ligadospalpites.payments.domain.ports.RevenueCatEventRepository
import com.ligadospalpites.payments.domain.ports.SubscriptionRepository
import com.ligadospalpites.payments.infrastructure.web.RevenueCatEventDto
import com.ligadospalpites.payments.infrastructure.web.RevenueCatWebhookRequest
import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.models.UserEntitlement
import com.ligadospalpites.users.domain.ports.UserEntitlementRepository
import com.ligadospalpites.users.domain.ports.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ProcessRevenueCatWebhookUseCaseTest {

    private lateinit var eventRepository: FakeRevenueCatEventRepository
    private lateinit var subscriptionRepository: FakeSubscriptionRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var entitlementRepository: FakeUserEntitlementRepository
    private lateinit var useCase: ProcessRevenueCatWebhookUseCase

    private val userId = UUID.randomUUID()
    private val firebaseUid = "firebase-user-123"

    @BeforeEach
    fun setUp() {
        eventRepository = FakeRevenueCatEventRepository()
        subscriptionRepository = FakeSubscriptionRepository()
        userRepository = FakeUserRepository()
        entitlementRepository = FakeUserEntitlementRepository()

        userRepository.save(User(id = userId, firebaseUid = firebaseUid, email = "test@user.com", name = "Test User"))

        useCase = ProcessRevenueCatWebhookUseCase(
            eventRepository = eventRepository,
            subscriptionRepository = subscriptionRepository,
            userRepository = userRepository,
            entitlementRepository = entitlementRepository
        )
    }

    @Test
    fun `should return true and ignore when event id is null`() {
        val request = RevenueCatWebhookRequest(
            apiVersion = "1.0",
            event = RevenueCatEventDto(id = null, type = "INITIAL_PURCHASE")
        )

        val result = useCase(request)

        assertTrue(result)
        assertTrue(eventRepository.events.isEmpty())
    }

    @Test
    fun `should be idempotent and ignore duplicate events`() {
        val eventId = "evt_duplicate_001"
        eventRepository.save(RevenueCatEvent(id = eventId, type = "INITIAL_PURCHASE", appUserId = firebaseUid, productId = "pro_monthly", payload = "{}"))

        val request = RevenueCatWebhookRequest(
            apiVersion = "1.0",
            event = RevenueCatEventDto(id = eventId, type = "INITIAL_PURCHASE", appUserId = firebaseUid)
        )

        val result = useCase(request)

        assertTrue(result)
        assertEquals(1, eventRepository.events.size)
    }

    @Test
    fun `should discard event when user is not found locally`() {
        val eventId = "evt_unknown_user_001"
        val request = RevenueCatWebhookRequest(
            apiVersion = "1.0",
            event = RevenueCatEventDto(id = eventId, type = "INITIAL_PURCHASE", appUserId = "non_existent_uid", productId = "pro_monthly")
        )

        val result = useCase(request)

        assertTrue(result)
        val saved = eventRepository.events[eventId]
        assertNotNull(saved)
        assertEquals("DISCARDED", saved?.status)
    }

    @Test
    fun `should activate premium entitlement and save subscription on INITIAL_PURCHASE`() {
        val eventId = "evt_purchase_001"
        val expiryMs = Instant.now().plusSeconds(86400 * 30).toEpochMilli()
        val request = RevenueCatWebhookRequest(
            apiVersion = "1.0",
            event = RevenueCatEventDto(
                id = eventId,
                type = "INITIAL_PURCHASE",
                appUserId = firebaseUid,
                productId = "monthly_pro",
                entitlementId = "premium",
                store = "PLAY_STORE",
                expirationAtMs = expiryMs
            )
        )

        val result = useCase(request)

        assertTrue(result)
        val savedEvent = eventRepository.events[eventId]
        assertEquals("PROCESSED", savedEvent?.status)

        val userEntitlements = entitlementRepository.findByUserId(userId)
        assertEquals(1, userEntitlements.size)
        assertEquals(EntitlementType.PREMIUM, userEntitlements[0].entitlementType)
        assertNull(userEntitlements[0].sportId)

        val sub = subscriptionRepository.findByTransactionId(eventId)
        assertNotNull(sub)
        assertEquals(SubscriptionStatus.ACTIVE, sub?.status)
    }

    @Test
    fun `should expire entitlement on EXPIRATION event`() {
        // Initial setup with an active entitlement
        entitlementRepository.save(
            UserEntitlement(
                id = UUID.randomUUID(),
                userId = userId,
                entitlementType = EntitlementType.PREMIUM,
                sportId = null,
                expiresAt = Instant.now().plusSeconds(3600)
            )
        )

        val eventId = "evt_expiration_001"
        val expiredMs = Instant.now().minusSeconds(60).toEpochMilli()
        val request = RevenueCatWebhookRequest(
            apiVersion = "1.0",
            event = RevenueCatEventDto(
                id = eventId,
                type = "EXPIRATION",
                appUserId = firebaseUid,
                productId = "monthly_pro",
                entitlementId = "premium",
                expirationAtMs = expiredMs
            )
        )

        val result = useCase(request)

        assertTrue(result)
        val userEntitlements = entitlementRepository.findByUserId(userId)
        assertEquals(1, userEntitlements.size)
        assertTrue(userEntitlements[0].isExpired())

        val sub = subscriptionRepository.findByTransactionId(eventId)
        assertNotNull(sub)
        assertEquals(SubscriptionStatus.EXPIRED, sub?.status)
    }

    @Test
    fun `should mark subscription as CANCELLED on CANCELLATION event`() {
        val eventId = "evt_cancel_001"
        val request = RevenueCatWebhookRequest(
            apiVersion = "1.0",
            event = RevenueCatEventDto(
                id = eventId,
                type = "CANCELLATION",
                appUserId = firebaseUid,
                productId = "monthly_pro"
            )
        )

        val result = useCase(request)

        assertTrue(result)
        val sub = subscriptionRepository.findByTransactionId(eventId)
        assertNotNull(sub)
        assertEquals(SubscriptionStatus.CANCELLED, sub?.status)
    }
}

// In-Memory Fakes
class FakeRevenueCatEventRepository : RevenueCatEventRepository {
    val events = mutableMapOf<String, RevenueCatEvent>()

    override fun existsById(id: String): Boolean = events.containsKey(id)

    override fun save(event: RevenueCatEvent): RevenueCatEvent {
        events[event.id] = event
        return event
    }
}

class FakeSubscriptionRepository : SubscriptionRepository {
    val subscriptions = mutableMapOf<UUID, Subscription>()

    override fun findByTransactionId(transactionId: String): Subscription? {
        return subscriptions.values.find { it.transactionId == transactionId }
    }

    override fun findByUserId(userId: UUID): List<Subscription> {
        return subscriptions.values.filter { it.userId == userId }
    }

    override fun save(subscription: Subscription): Subscription {
        subscriptions[subscription.id] = subscription
        return subscription
    }
}

class FakeUserRepository : UserRepository {
    val users = mutableMapOf<UUID, User>()

    override fun findByFirebaseUid(firebaseUid: String): User? {
        return users.values.find { it.firebaseUid == firebaseUid }
    }

    override fun findById(id: UUID): User? = users[id]

    override fun findAllById(ids: Collection<UUID>): List<User> {
        return users.values.filter { it.id in ids }
    }

    override fun save(user: User): User {
        users[user.id] = user
        return user
    }
}

class FakeUserEntitlementRepository : UserEntitlementRepository {
    val entitlements = mutableMapOf<UUID, UserEntitlement>()

    override fun findByUserId(userId: UUID): List<UserEntitlement> {
        return entitlements.values.filter { it.userId == userId }
    }

    override fun save(entitlement: UserEntitlement): UserEntitlement {
        entitlements[entitlement.id] = entitlement
        return entitlement
    }
}
