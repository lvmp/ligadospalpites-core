package com.ligadospalpites.notifications.application.usecases

import com.ligadospalpites.notifications.domain.models.InAppNotification
import com.ligadospalpites.notifications.domain.ports.InAppNotificationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant
import java.util.UUID

class GetUserNotificationsUseCaseTest {

    private lateinit var inAppRepository: InAppNotificationRepository
    private lateinit var useCase: GetUserNotificationsUseCase

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        inAppRepository = mock(InAppNotificationRepository::class.java)
        useCase = GetUserNotificationsUseCase(inAppRepository)
    }

    @Test
    fun `should return paged user notifications with unread count`() {
        val notification1 = InAppNotification(
            id = UUID.randomUUID(),
            userId = userId,
            title = "📅 AGENDA DO DIA",
            content = "Confira os jogos de hoje",
            isRead = false,
            createdAt = Instant.now()
        )

        `when`(inAppRepository.findByUserId(userId, 0, 20)).thenReturn(listOf(notification1))
        `when`(inAppRepository.countTotalByUserId(userId)).thenReturn(1L)
        `when`(inAppRepository.countUnreadByUserId(userId)).thenReturn(1L)

        val result = useCase.execute(userId, 0, 20)

        assertEquals(1, result.content.size)
        assertEquals("📅 AGENDA DO DIA", result.content[0].title)
        assertEquals(1L, result.totalElements)
        assertEquals(1L, result.unreadCount)
        assertEquals(1, result.totalPages)
        assertFalse(result.hasNext)

        verify(inAppRepository).findByUserId(userId, 0, 20)
        verify(inAppRepository).countTotalByUserId(userId)
        verify(inAppRepository).countUnreadByUserId(userId)
    }
}
