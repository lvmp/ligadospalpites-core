package com.ligadospalpites.notifications

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.notifications.application.usecases.RegisterDeviceRequest
import com.ligadospalpites.notifications.application.usecases.RegisterDeviceUseCase
import com.ligadospalpites.notifications.domain.ports.DeviceRepository
import com.ligadospalpites.notifications.infrastructure.adapters.DeviceTokenExpiredEvent
import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class DeviceRegistrationIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var registerDeviceUseCase: RegisterDeviceUseCase

    @Autowired
    private lateinit var deviceRepository: DeviceRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Test
    fun `should register device and process ownership transfer when token is registered to another user`() {
        // Arrange
        val user1 = userRepository.save(User(UUID.randomUUID(), "firebase-uid-user1", "user1@test.com", "User One"))
        val user2 = userRepository.save(User(UUID.randomUUID(), "firebase-uid-user2", "user2@test.com", "User Two"))

        val sharedToken = "fcm_token_shared_between_logouts"
        val deviceId1 = UUID.randomUUID()
        val deviceId2 = UUID.randomUUID()

        // User 1 registers device with sharedToken first
        registerDeviceUseCase.registerDevice(
            userId = user1.id,
            request = RegisterDeviceRequest(deviceId = deviceId1, fcmToken = sharedToken, deviceType = "ANDROID")
        )

        val deviceForUser1 = deviceRepository.findByFcmToken(sharedToken)
        assertNotNull(deviceForUser1)
        assertEquals(user1.id, deviceForUser1?.userId)

        // Act - User 2 registers device with the same sharedToken (e.g. logging into same device)
        registerDeviceUseCase.registerDevice(
            userId = user2.id,
            request = RegisterDeviceRequest(deviceId = deviceId2, fcmToken = sharedToken, deviceType = "ANDROID")
        )

        // Assert
        // Shared token must be detached from User 1 and assigned exclusively to User 2
        val finalDeviceForUser2 = deviceRepository.findByFcmToken(sharedToken)
        assertNotNull(finalDeviceForUser2)
        assertEquals(user2.id, finalDeviceForUser2?.userId)
        assertEquals(deviceId2, finalDeviceForUser2?.deviceId)

        // No devices with sharedToken should be registered to User 1
        val finalDeviceForUser1 = deviceRepository.findByDeviceId(deviceId1)
        assertNull(finalDeviceForUser1)
    }

    @Test
    fun `should asynchronously prune expired fcm token when token expired event occurs`() {
        // Arrange
        val user = userRepository.save(User(UUID.randomUUID(), "firebase-uid-user3", "user3@test.com", "User Three"))
        val expiredToken = "fcm_token_expired_123"

        registerDeviceUseCase.registerDevice(
            userId = user.id,
            request = RegisterDeviceRequest(deviceId = UUID.randomUUID(), fcmToken = expiredToken, deviceType = "IOS")
        )

        val activeDevice = deviceRepository.findByFcmToken(expiredToken)
        assertNotNull(activeDevice)

        // Act - Publish Expired Token event
        eventPublisher.publishEvent(DeviceTokenExpiredEvent(expiredToken))

        // Wait brief moments for async execution to complete
        Thread.sleep(1000)

        // Assert
        val prunedDevice = deviceRepository.findByFcmToken(expiredToken)
        assertNull(prunedDevice)
    }
}
