package com.ligadospalpites.users

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.shared.identity.UserResolver
import com.ligadospalpites.users.domain.ports.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

class UserLastAccessIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var userResolver: UserResolver

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `should initialize lastAccess on user creation and update it on next login resolution`() {
        val firebaseUid = "test-uid-" + UUID.randomUUID()
        val email = "lastaccess_test@example.com"
        val name = "Test Last Access User"

        // 1. Initial creation via userResolver.resolve
        val createdUser = userResolver.resolve(firebaseUid, email, name)
        assertNotNull(createdUser.id)
        assertNotNull(createdUser.createdAt)
        assertNotNull(createdUser.lastAccess)

        val initialCreatedAt = createdUser.createdAt
        val initialLastAccess = createdUser.lastAccess

        // Allow some time to pass
        Thread.sleep(100)

        // 2. Next login/resolution
        val resolvedUser = userResolver.resolve(firebaseUid, email, name)
        assertEquals(createdUser.id, resolvedUser.id)
        assertEquals(initialCreatedAt.toEpochMilli(), resolvedUser.createdAt.toEpochMilli())

        // Verify lastAccess was updated
        assertTrue(
            resolvedUser.lastAccess.isAfter(initialLastAccess),
            "Expected resolvedUser.lastAccess (${resolvedUser.lastAccess}) to be after initial lastAccess ($initialLastAccess)"
        )

        // 3. Verify directly from repository
        val fetchedFromDb = userRepository.findByFirebaseUid(firebaseUid)
        assertNotNull(fetchedFromDb)
        assertEquals(resolvedUser.lastAccess.toEpochMilli(), fetchedFromDb?.lastAccess?.toEpochMilli())
    }

    @Test
    fun `should update lastAccess when resolved by Uid or UUID`() {
        val uid = "test-uid-header-" + UUID.randomUUID()

        // Create initial user
        val userId = userResolver.resolveByUidOrUuid(uid)
        val initialUser = userRepository.findById(userId)
        assertNotNull(initialUser)
        val firstLastAccess = initialUser!!.lastAccess

        Thread.sleep(100)

        // Re-resolve by UUID string
        val secondResolvedId = userResolver.resolveByUidOrUuid(userId.toString())
        assertEquals(userId, secondResolvedId)
        val userAfterUuidResolve = userRepository.findById(userId)
        assertNotNull(userAfterUuidResolve)
        assertTrue(
            userAfterUuidResolve!!.lastAccess.isAfter(firstLastAccess),
            "Expected lastAccess to be updated after resolveByUidOrUuid using UUID"
        )
    }
}
