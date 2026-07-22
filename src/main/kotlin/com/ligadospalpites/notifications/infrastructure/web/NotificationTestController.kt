package com.ligadospalpites.notifications.infrastructure.web

import com.ligadospalpites.notifications.application.usecases.NotificationDispatcherService
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.NotificationTarget
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationTestController(
    private val dispatcherService: NotificationDispatcherService,
    @Value("\${app.admin.secret:teste-push-secret-123}") private val adminSecret: String
) {

    @PostMapping("/dispatch")
    fun dispatchNotification(
        @RequestHeader(value = "X-Admin-Secret", required = false) requestSecret: String?,
        @RequestBody request: DispatchNotificationRequest
    ): ResponseEntity<Any> {
        if (requestSecret != adminSecret) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf("error" to "Acesso não autorizado. Chave X-Admin-Secret incorreta ou ausente.")
            )
        }

        val targetEnum = try {
            NotificationTarget.valueOf(request.target.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to "Target inválido. Esperado: USER, LEAGUE, SPORT, ALL")
            )
        }

        val targetUUID = if (!request.targetId.isNullOrBlank()) {
            try {
                UUID.fromString(request.targetId)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().body(
                    mapOf("error" to "targetId inválido (formato UUID esperado)")
                )
            }
        } else {
            if (targetEnum != NotificationTarget.ALL) {
                return ResponseEntity.badRequest().body(
                    mapOf("error" to "targetId é obrigatório para targets diferentes de ALL")
                )
            }
            null
        }

        val channels = request.channels?.map {
            try {
                NotificationChannel.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().body(
                    mapOf("error" to "Canal inválido '$it'. Canais disponíveis: PUSH, IN_APP, EMAIL")
                )
            }
        } ?: listOf(NotificationChannel.PUSH)

        dispatcherService.dispatch(
            target = targetEnum,
            targetId = targetUUID,
            title = request.title,
            content = request.content,
            channels = channels
        )

        return ResponseEntity.accepted().body(
            mapOf(
                "status" to "ACCEPTED",
                "message" to "Notificação enviada para processamento em background."
            )
        )
    }
}

data class DispatchNotificationRequest(
    val target: String,
    val targetId: String?,
    val title: String,
    val content: String,
    val channels: List<String>? = listOf("PUSH")
)
