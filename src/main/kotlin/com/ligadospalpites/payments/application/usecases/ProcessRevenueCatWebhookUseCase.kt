package com.ligadospalpites.payments.application.usecases

import com.ligadospalpites.payments.domain.models.StorePlatform
import com.ligadospalpites.payments.domain.models.Subscription
import com.ligadospalpites.payments.domain.models.SubscriptionStatus
import com.ligadospalpites.payments.infrastructure.persistence.*
import com.ligadospalpites.payments.infrastructure.web.RevenueCatWebhookRequest
import com.ligadospalpites.users.domain.models.EntitlementType
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserEntitlementRepository
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import com.ligadospalpites.users.infrastructure.persistence.UserEntitlementJpaEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProcessRevenueCatWebhookUseCase(
    private val eventRepository: SpringDataRevenueCatEventRepository,
    private val subscriptionRepository: SpringDataSubscriptionRepository,
    private val userRepository: SpringDataUserRepository,
    private val entitlementRepository: SpringDataUserEntitlementRepository
) {
    private val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(ProcessRevenueCatWebhookUseCase::class.java)

    @Transactional
    operator fun invoke(request: RevenueCatWebhookRequest): Boolean {
        val event = request.event
        val eventId = event.id

        if (eventId == null) {
            log.warn("ID do evento do RevenueCat está nulo. Ignorando processamento de regras.")
            return true
        }

        // 1. Idempotência: Se já processamos esse evento, ignora
        if (eventRepository.existsById(eventId)) {
            log.info("Evento do RevenueCat já processado (duplicado): {}", eventId)
            return true
        }

        val eventType = event.type ?: "UNKNOWN"
        val appUserIdStr = event.appUserId ?: ""
        val productIdStr = event.productId ?: ""

        // 2. Persistir log bruto do evento
        val payloadJson = objectMapper.writeValueAsString(request)
        val eventEntity = RevenueCatEventJpaEntity(
            id = eventId,
            type = eventType,
            appUserId = appUserIdStr,
            productId = productIdStr,
            payload = payloadJson,
            createdAt = Instant.now()
        )
        eventRepository.save(eventEntity)

        // 3. Obter o User ID local (UUID) a partir do app_user_id (Firebase UID ou UUID)
        val userEntity = userRepository.findByFirebaseUid(appUserIdStr)
            ?: try {
                val uuid = UUID.fromString(appUserIdStr)
                userRepository.findById(uuid).orElse(null)
            } catch (e: IllegalArgumentException) {
                null
            }

        if (userEntity == null) {
            log.warn("Usuário com Firebase UID ou UUID '{}' não encontrado localmente. Ignorando processamento de regras.", appUserIdStr)
            return true // Retorna true para evitar retries de id inválido do RevenueCat
        }

        val userId = userEntity.id


        // 5. Mapear plataforma da loja
        val storeStr = event.store ?: ""
        val storePlatform = when (storeStr.uppercase()) {
            "APP_STORE" -> StorePlatform.APPLE_APP_STORE
            "PLAY_STORE" -> StorePlatform.GOOGLE_PLAY
            else -> StorePlatform.STRIPE
        }

        // 6. Processar de acordo com o tipo de evento
        when (eventType) {
            "INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION" -> {
                processActiveAccess(userId, eventId, event, storePlatform)
            }
            "EXPIRATION" -> {
                processExpiredAccess(userId, eventId, event)
            }
            "CANCELLATION" -> {
                processCancelledSubscription(userId, eventId, event)
            }
            else -> {
                log.info("Evento do tipo {} recebido mas não necessita de tratamento adicional.", eventType)
            }
        }

        return true
    }

    private fun processActiveAccess(userId: UUID, eventId: String, event: com.ligadospalpites.payments.infrastructure.web.RevenueCatEventDto, store: StorePlatform) {
        val entitlementId = event.entitlementId ?: event.entitlementIds?.firstOrNull()
        if (entitlementId == null) {
            log.warn("Nenhum entitlement_id encontrado no evento do RevenueCat: {}. Ignorando.", eventId)
            return
        }

        val expirationInstant = event.expirationAtMs?.let { Instant.ofEpochMilli(it) }

        // Mapeia o ID do Entitlement do RevenueCat para o EntitlementType e SportId local
        val (entitlementType, sportId) = mapEntitlement(entitlementId)

        // Upsert no UserEntitlement
        val existingEntitlements = entitlementRepository.findByUserId(userId)
        val matchingEntitlement = existingEntitlements.find {
            it.entitlementType == entitlementType && it.sportId == sportId
        }

        if (matchingEntitlement != null) {
            val updated = UserEntitlementJpaEntity(
                id = matchingEntitlement.id,
                userId = userId,
                entitlementType = entitlementType,
                sportId = sportId,
                expiresAt = expirationInstant,
                createdAt = matchingEntitlement.createdAt
            )
            entitlementRepository.save(updated)
            log.info("Entitlement atualizado para o usuário {}: tipo={}, sportId={}, expiração={}", userId, entitlementType, sportId, expirationInstant)
        } else {
            val newEntitlement = UserEntitlementJpaEntity(
                id = UUID.randomUUID(),
                userId = userId,
                entitlementType = entitlementType,
                sportId = sportId,
                expiresAt = expirationInstant,
                createdAt = Instant.now()
            )
            entitlementRepository.save(newEntitlement)
            log.info("Novo Entitlement criado para o usuário {}: tipo={}, sportId={}, expiração={}", userId, entitlementType, sportId, expirationInstant)
        }

        // Upsert na Subscription
        val transactionId = eventId
        val existingSub = subscriptionRepository.findByTransactionId(transactionId).orElse(null)
        val productIdStr = event.productId ?: ""

        if (existingSub != null) {
            val updatedSub = SubscriptionJpaEntity(
                id = existingSub.id,
                userId = userId,
                store = store,
                transactionId = transactionId,
                productId = productIdStr,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = expirationInstant,
                createdAt = existingSub.createdAt,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updatedSub)
        } else {
            val newSub = SubscriptionJpaEntity(
                id = UUID.randomUUID(),
                userId = userId,
                store = store,
                transactionId = transactionId,
                productId = productIdStr,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodEnd = expirationInstant,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(newSub)
        }
    }

    private fun processExpiredAccess(userId: UUID, eventId: String, event: com.ligadospalpites.payments.infrastructure.web.RevenueCatEventDto) {
        val entitlementId = event.entitlementId ?: event.entitlementIds?.firstOrNull() ?: return
        val (entitlementType, sportId) = mapEntitlement(entitlementId)

        // Localiza e expira o entitlement
        val existing = entitlementRepository.findByUserId(userId)
        val matching = existing.find {
            it.entitlementType == entitlementType && it.sportId == sportId
        }

        if (matching != null) {
            // Seta a expiração no passado (horário atual ou do evento) para cortar o acesso
            val expiredAt = event.expirationAtMs?.let { Instant.ofEpochMilli(it) } ?: Instant.now()
            val updated = UserEntitlementJpaEntity(
                id = matching.id,
                userId = userId,
                entitlementType = entitlementType,
                sportId = sportId,
                expiresAt = expiredAt,
                createdAt = matching.createdAt
            )
            entitlementRepository.save(updated)
            log.info("Entitlement expirado com sucesso no banco para o usuário {}: {}", userId, entitlementId)
        }

        // Atualiza log de assinatura
        val transactionId = eventId
        val existingSub = subscriptionRepository.findByTransactionId(transactionId).orElse(null)
        val productIdStr = event.productId ?: ""

        val userSubs = subscriptionRepository.findByUserId(userId)
        val originalSub = userSubs.find { it.productId == productIdStr } ?: userSubs.maxByOrNull { it.updatedAt }

        if (existingSub != null) {
            val updatedSub = SubscriptionJpaEntity(
                id = existingSub.id,
                userId = userId,
                store = existingSub.store,
                transactionId = transactionId,
                productId = productIdStr,
                status = SubscriptionStatus.EXPIRED,
                currentPeriodEnd = existingSub.currentPeriodEnd,
                createdAt = existingSub.createdAt,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updatedSub)
        } else {
            val newSub = SubscriptionJpaEntity(
                id = UUID.randomUUID(),
                userId = userId,
                store = originalSub?.store ?: StorePlatform.STRIPE,
                transactionId = transactionId,
                productId = productIdStr,
                status = SubscriptionStatus.EXPIRED,
                currentPeriodEnd = originalSub?.currentPeriodEnd,
                createdAt = originalSub?.createdAt ?: Instant.now(),
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(newSub)
        }

        // Atualizar também a assinatura original se houver
        if (originalSub != null && originalSub.transactionId != transactionId) {
            val updatedOriginal = SubscriptionJpaEntity(
                id = originalSub.id,
                userId = userId,
                store = originalSub.store,
                transactionId = originalSub.transactionId,
                productId = originalSub.productId,
                status = SubscriptionStatus.EXPIRED,
                currentPeriodEnd = originalSub.currentPeriodEnd,
                createdAt = originalSub.createdAt,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updatedOriginal)
        }
    }

    private fun processCancelledSubscription(userId: UUID, eventId: String, event: com.ligadospalpites.payments.infrastructure.web.RevenueCatEventDto) {
        val transactionId = eventId
        val existingSub = subscriptionRepository.findByTransactionId(transactionId).orElse(null)
        val productIdStr = event.productId ?: ""

        val userSubs = subscriptionRepository.findByUserId(userId)
        val originalSub = userSubs.find { it.productId == productIdStr } ?: userSubs.maxByOrNull { it.updatedAt }

        if (existingSub != null) {
            val updatedSub = SubscriptionJpaEntity(
                id = existingSub.id,
                userId = userId,
                store = existingSub.store,
                transactionId = transactionId,
                productId = productIdStr,
                status = SubscriptionStatus.CANCELLED,
                currentPeriodEnd = existingSub.currentPeriodEnd,
                createdAt = existingSub.createdAt,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updatedSub)
        } else {
            val newSub = SubscriptionJpaEntity(
                id = UUID.randomUUID(),
                userId = userId,
                store = originalSub?.store ?: StorePlatform.STRIPE,
                transactionId = transactionId,
                productId = productIdStr,
                status = SubscriptionStatus.CANCELLED,
                currentPeriodEnd = originalSub?.currentPeriodEnd,
                createdAt = originalSub?.createdAt ?: Instant.now(),
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(newSub)
        }

        // Atualizar também a assinatura original se houver
        if (originalSub != null && originalSub.transactionId != transactionId) {
            val updatedOriginal = SubscriptionJpaEntity(
                id = originalSub.id,
                userId = userId,
                store = originalSub.store,
                transactionId = originalSub.transactionId,
                productId = originalSub.productId,
                status = SubscriptionStatus.CANCELLED,
                currentPeriodEnd = originalSub.currentPeriodEnd,
                createdAt = originalSub.createdAt,
                updatedAt = Instant.now()
            )
            subscriptionRepository.save(updatedOriginal)
        }
        log.info("Assinatura do usuário {} marcada como CANCELADA (acesso mantido até expiração).", userId)
    }

    private fun mapEntitlement(entitlementId: String): Pair<EntitlementType, UUID?> {
        return if (entitlementId.equals("premium", ignoreCase = true)) {
            Pair(EntitlementType.PREMIUM, null)
        } else {
            // Mapeia por exemplo "sport_pass_football" para o ID de futebol
            // Futebol ID: f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c
            val footballId = UUID.fromString("f3b3b44b-6f81-42cb-b1b7-d1a1005a8f4c")
            Pair(EntitlementType.SPORT_PASS, footballId)
        }
    }
}
