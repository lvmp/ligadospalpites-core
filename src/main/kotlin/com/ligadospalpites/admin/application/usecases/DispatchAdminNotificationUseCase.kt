package com.ligadospalpites.admin.application.usecases

import com.ligadospalpites.admin.domain.models.AuditLog
import com.ligadospalpites.admin.domain.ports.AuditLogRepository
import com.ligadospalpites.notifications.application.usecases.NotificationDispatcherService
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.NotificationTarget
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DispatchAdminNotificationUseCase(
    private val notificationDispatcherService: NotificationDispatcherService,
    private val auditLogRepository: AuditLogRepository
) {
    operator fun invoke(
        targetStr: String,
        targetIdStr: String?,
        title: String,
        content: String,
        operatorId: String = "admin-master"
    ): Boolean {
        val target = when (targetStr.uppercase()) {
            "GLOBAL", "ALL" -> NotificationTarget.ALL
            "LEAGUE" -> NotificationTarget.LEAGUE
            "USER" -> NotificationTarget.USER
            "SPORT" -> NotificationTarget.SPORT
            else -> NotificationTarget.ALL
        }

        val targetId = targetIdStr?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

        val channels = listOf(NotificationChannel.PUSH, NotificationChannel.IN_APP)

        notificationDispatcherService.dispatch(
            target = target,
            targetId = targetId,
            title = title,
            content = content,
            channels = channels
        )

        auditLogRepository.save(
            AuditLog(
                operatorId = operatorId,
                action = "DISPATCH_NOTIFICATION",
                targetId = targetIdStr ?: "GLOBAL",
                details = "Notificação disparada ($targetStr). Título: $title"
            )
        )

        return true
    }
}
