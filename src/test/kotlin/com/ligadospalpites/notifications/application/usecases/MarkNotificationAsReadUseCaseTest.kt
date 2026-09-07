package com.ligadospalpites.notifications.application.usecases

import com.ligadospalpites.notifications.domain.ports.InAppNotificationRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.UUID

class MarkNotificationAsReadUseCaseTest {

    private lateinit var inAppRepository: InAppNotificationRepository
    private lateinit var useCase: MarkNotificationAsReadUseCase

    private val userId = UUID.randomUUID()
    private val notificationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        inAppRepository = mock(InAppNotificationRepository::class.java)
        useCase = MarkNotificationAsReadUseCase(inAppRepository)
    }

    @Test
    fun `should mark single notification as read`() {
        `when`(inAppRepository.markAsRead(notificationId, userId)).thenReturn(true)

        val result = useCase.markSingle(notificationId, userId)

        assertTrue(result)
        verify(inAppRepository).markAsRead(notificationId, userId)
    }

    @Test
    fun `should mark all notifications as read`() {
        useCase.markAll(userId)
        verify(inAppRepository).markAllAsRead(userId)
    }
}
