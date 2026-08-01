package com.ligadospalpites.users

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.shared.identity.UserResolver
import com.ligadospalpites.users.domain.ports.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID

import com.ligadospalpites.users.application.usecases.SyncUserProfileUseCase

class UserLastAccessIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var userResolver: UserResolver

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var syncUserProfileUseCase: SyncUserProfileUseCase

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

    @Test
    fun `should update placeholder email and name when resolve is called with real user data`() {
        val uid = "placeholder-uid-" + UUID.randomUUID()

        // 1. Auto-create user via header resolution (gets placeholder user_... name & email)
        val createdId = userResolver.resolveByUidOrUuid(uid)
        val placeholderUser = userRepository.findById(createdId)
        assertNotNull(placeholderUser)
        assertTrue(placeholderUser!!.email.contains(uid))
        assertTrue(placeholderUser.email.endsWith("@ligadospalpites.com"))

        // 2. Resolve with real user details
        val realEmail = "realuser_${UUID.randomUUID()}@example.com"
        val realName = "Maria Silva"
        val resolvedUser = userResolver.resolve(uid, realEmail, realName)

        // 3. Assert placeholder data was overwritten with real data
        assertEquals(createdId, resolvedUser.id)
        assertEquals(realEmail, resolvedUser.email)
        assertEquals(realName, resolvedUser.name)

        val updatedInDb = userRepository.findById(createdId)
        assertEquals(realEmail, updatedInDb?.email)
        assertEquals(realName, updatedInDb?.name)
    }

    @Test
    fun `should update profile using SyncUserProfileUseCase`() {
        val uid = "sync-uid-" + UUID.randomUUID()
        val createdUser = userResolver.resolve(uid, "old_email@test.com", "Old Name")

        val newEmail = "updated_email@test.com"
        val newName = "Updated Name"
        val newAvatar = "https://example.com/avatar.png"

        val updatedUser = syncUserProfileUseCase.execute(
            SyncUserProfileUseCase.Command(
                userId = createdUser.id,
                name = newName,
                email = newEmail,
                avatarUrl = newAvatar
            )
        )

        assertEquals(newEmail, updatedUser.email)
        assertEquals(newName, updatedUser.name)
        assertEquals(newAvatar, updatedUser.avatarUrl)

        val inDb = userRepository.findById(createdUser.id)
        assertEquals(newEmail, inDb?.email)
        assertEquals(newName, inDb?.name)
        assertEquals(newAvatar, inDb?.avatarUrl)
    }
}
