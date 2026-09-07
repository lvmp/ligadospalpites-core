package com.ligadospalpites.notifications.infrastructure.web

import com.ligadospalpites.notifications.application.usecases.GetUserNotificationsUseCase
import com.ligadospalpites.notifications.application.usecases.MarkNotificationAsReadUseCase
import com.ligadospalpites.shared.identity.UserResolver
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class InAppNotificationResponse(
    val id: UUID,
    val userId: UUID,
    val title: String,
    val content: String,
    val isRead: Boolean,
    val createdAt: String
)

data class PagedUserNotificationsResponse(
    val content: List<InAppNotificationResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val unreadCount: Long,
    val totalPages: Int,
    val hasNext: Boolean
)

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val getUserNotificationsUseCase: GetUserNotificationsUseCase,
    private val markNotificationAsReadUseCase: MarkNotificationAsReadUseCase,
    private val userResolver: UserResolver
) {

    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @GetMapping
    fun getUserNotifications(
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?,
        authentication: Authentication?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedUserNotificationsResponse> {
        val userUUID = userResolver.resolveAuthenticatedUser(userIdHeader, authentication)
        val result = getUserNotificationsUseCase.execute(userUUID, page, size)

        val responseContent = result.content.map {
            InAppNotificationResponse(
                id = it.id,
                userId = it.userId,
                title = it.title,
                content = it.content,
                isRead = it.isRead,
                createdAt = it.createdAt.toString()
            )
        }

        return ResponseEntity.ok(
            PagedUserNotificationsResponse(
                content = responseContent,
                page = result.page,
                size = result.size,
                totalElements = result.totalElements,
                unreadCount = result.unreadCount,
                totalPages = result.totalPages,
                hasNext = result.hasNext
            )
        )
    }

    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @PatchMapping("/{id}/read")
    fun markNotificationAsRead(
        @PathVariable id: UUID,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?,
        authentication: Authentication?
    ): ResponseEntity<Map<String, Any>> {
        val userUUID = userResolver.resolveAuthenticatedUser(userIdHeader, authentication)
        val updated = markNotificationAsReadUseCase.markSingle(id, userUUID)

        return if (updated) {
            ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "Notification marked as read"))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @PostMapping("/read-all")
    fun markAllNotificationsAsRead(
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val userUUID = userResolver.resolveAuthenticatedUser(userIdHeader, authentication)
        markNotificationAsReadUseCase.markAll(userUUID)
        return ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "All notifications marked as read"))
    }
}
