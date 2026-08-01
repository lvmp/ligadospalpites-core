package com.ligadospalpites.users.infrastructure.web

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import com.ligadospalpites.users.infrastructure.persistence.UserEntitlementJpaEntity
import com.ligadospalpites.users.infrastructure.persistence.UserJpaEntity
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class UserControllerTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var wac: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: SpringDataUserRepository

    @Autowired
    private lateinit var entitlementRepository: SpringDataUserEntitlementRepository

    private val testUserId = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private val firebaseUid = "user_test_firebase_uid_123"

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(wac)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

        entitlementRepository.deleteAll()
        userRepository.deleteAll()

        userRepository.save(
            UserJpaEntity(
                id = testUserId,
                firebaseUid = firebaseUid,
                email = "test@ligadospalpites.com",
                name = "Test User",
                createdAt = Instant.now(),
                lastAccess = Instant.now()
            )
        )
    }

    @Test
    fun `should return user entitlements via X-User-Id header`() {
        val sportId = UUID.randomUUID()
        entitlementRepository.save(
            UserEntitlementJpaEntity(
                userId = testUserId,
                entitlementType = EntitlementType.SPORT_PASS,
                sportId = sportId,
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )
        )

        mockMvc.perform(
            get("/api/v1/users/me/entitlements")
                .header("X-User-Id", testUserId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId", `is`(testUserId.toString())))
            .andExpect(jsonPath("$.hasPremium", `is`(false)))
            .andExpect(jsonPath("$.entitlements.length()", `is`(1)))
            .andExpect(jsonPath("$.entitlements[0].entitlementType", `is`("SPORT_PASS")))
            .andExpect(jsonPath("$.entitlements[0].active", `is`(true)))
    }

    @Test
    fun `should return user entitlements via JWT principal`() {
        entitlementRepository.save(
            UserEntitlementJpaEntity(
                userId = testUserId,
                entitlementType = EntitlementType.PREMIUM,
                sportId = null,
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )
        )

        val auth = UsernamePasswordAuthenticationToken(firebaseUid, "credentials", emptyList())

        mockMvc.perform(
            get("/api/v1/users/me/entitlements")
                .with(authentication(auth))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId", `is`(testUserId.toString())))
            .andExpect(jsonPath("$.hasPremium", `is`(true)))
            .andExpect(jsonPath("$.entitlements[0].entitlementType", `is`("PREMIUM")))
    }

    @Test
    fun `should deny access when JWT subject and X-User-Id belong to different users`() {
        val anotherUserId = UUID.randomUUID()
        val auth = UsernamePasswordAuthenticationToken(firebaseUid, "credentials", emptyList())

        mockMvc.perform(
            get("/api/v1/users/me/entitlements")
                .header("X-User-Id", anotherUserId.toString())
                .with(authentication(auth))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `should return user state via X-User-Id`() {
        mockMvc.perform(
            get("/api/v1/users/me/state")
                .header("X-User-Id", testUserId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId", `is`(testUserId.toString())))
            .andExpect(jsonPath("$.name", `is`("Test User")))
            .andExpect(jsonPath("$.plan", `is`("FREE")))
    }
}
