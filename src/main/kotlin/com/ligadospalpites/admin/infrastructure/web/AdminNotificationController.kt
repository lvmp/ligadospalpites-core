package com.ligadospalpites.admin.infrastructure.web

import com.ligadospalpites.admin.application.usecases.DispatchAdminNotificationUseCase
import com.ligadospalpites.admin.infrastructure.web.dtos.DispatchNotificationRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/notifications", "/api/v1/workspace/notifications")
class AdminNotificationController(
    private val dispatchAdminNotificationUseCase: DispatchAdminNotificationUseCase
) {

    @PostMapping("/dispatch")
    fun dispatch(
        @RequestBody @Valid request: DispatchNotificationRequest,
        @RequestHeader("X-User-Id", required = false, defaultValue = "admin-master") operatorId: String
    ): ResponseEntity<Map<String, Any>> {
        val success = dispatchAdminNotificationUseCase(
            targetStr = request.target,
            targetIdStr = request.targetId,
            title = request.title,
            content = request.content,
            operatorId = operatorId
        )
        return ResponseEntity.ok(mapOf("success" to success, "message" to "Notification dispatch initiated"))
    }
}
