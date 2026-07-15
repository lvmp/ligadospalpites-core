package com.ligadospalpites.payments.infrastructure.web

import com.ligadospalpites.payments.application.usecases.ProcessRevenueCatWebhookUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/payments/revenuecat")
class RevenueCatWebhookController(
    private val processRevenueCatWebhookUseCase: ProcessRevenueCatWebhookUseCase,
    @Value("\${revenuecat.webhook-secret:supersecret}")
    private val webhookSecret: String
) {
    private val log = LoggerFactory.getLogger(RevenueCatWebhookController::class.java)

    @PostMapping("/webhook")
    fun receiveWebhook(
        @RequestHeader(value = "Authorization", required = false) authorizationHeader: String?,
        @RequestBody request: RevenueCatWebhookRequest
    ): ResponseEntity<Any> {
        log.info("Recebida requisição de Webhook do RevenueCat: ID={}", request.event.id)

        // Validação simples de token estático de segurança
        val expectedToken = "Bearer $webhookSecret"
        if (authorizationHeader == null || authorizationHeader != expectedToken) {
            log.warn("Tentativa de chamada de webhook não autorizada: Token Header inválido.")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "UNAUTHORIZED"))
        }

        return try {
            val processed = processRevenueCatWebhookUseCase(request)
            if (processed) {
                ResponseEntity.ok(mapOf("status" to "SUCCESS"))
            } else {
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("status" to "ERROR"))
            }
        } catch (e: Exception) {
            log.error("Erro interno ao processar o webhook do RevenueCat", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("status" to "ERROR", "message" to e.message))
        }
    }
}
