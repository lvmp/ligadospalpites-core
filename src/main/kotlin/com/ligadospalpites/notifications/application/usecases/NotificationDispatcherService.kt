package com.ligadospalpites.notifications.application.usecases

import com.ligadospalpites.notifications.domain.models.Notification
import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.NotificationTarget
import com.ligadospalpites.notifications.domain.models.RecipientContactInfo
import com.ligadospalpites.notifications.domain.ports.DeviceRepository
import com.ligadospalpites.notifications.domain.ports.NotificationSender
import com.ligadospalpites.predictions.infrastructure.persistence.SpringDataPredictionRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NotificationDispatcherService(
    private val deviceRepository: DeviceRepository,
    private val predictionRepository: SpringDataPredictionRepository,
    private val groupMemberRepository: SpringDataGroupMemberRepository,
    private val senders: List<NotificationSender>,
    private val userRepository: com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository? = null
) {
    private val log = LoggerFactory.getLogger(NotificationDispatcherService::class.java)

    @Async
    fun dispatch(
        target: NotificationTarget,
        targetId: UUID?,
        title: String,
        content: String,
        channels: List<NotificationChannel>,
        metadata: Map<String, String> = emptyMap()
    ) {
        log.info("Starting background notification dispatch. Target: $target, TargetId: $targetId, Channels: $channels, Metadata: $metadata")

        // 1. Resolve userIds to notify
        val userIds = when (target) {
            NotificationTarget.USER -> {
                if (targetId == null) {
                    log.error("targetId cannot be null for USER targeting")
                    return
                }
                listOf(targetId)
            }
            NotificationTarget.LEAGUE -> {
                if (targetId == null) {
                    log.error("targetId cannot be null for LEAGUE targeting")
                    return
                }
                (predictionRepository.findUserIdsByLeagueId(targetId) + groupMemberRepository.findUserIdsByGroupId(targetId)).distinct()
            }
            NotificationTarget.SPORT -> {
                if (targetId == null) {
                    log.error("targetId cannot be null for SPORT targeting")
                    return
                }
                predictionRepository.findUserIdsBySportId(targetId)
            }
            NotificationTarget.ALL -> {
                val dbUsers = userRepository?.findAll()?.map { it.id } ?: emptyList()
                val deviceUsers = deviceRepository.findAll().map { it.userId }
                (dbUsers + deviceUsers).distinct()
            }
        }

        // 2. Fetch associated devices/tokens
        val devices = if (target == NotificationTarget.ALL) {
            deviceRepository.findAll()
        } else {
            deviceRepository.findAllByUserIds(userIds)
        }

        if (userIds.isEmpty() && devices.isEmpty()) {
            log.warn("No active users/devices found for target $target ($targetId)")
            return
        }

        val devicesByUserId = devices.groupBy { it.userId }
        val targetUserIds = if (target == NotificationTarget.ALL) userIds else userIds.ifEmpty { devicesByUserId.keys.toList() }

        targetUserIds.forEach { userId ->
            val userDevices = devicesByUserId[userId] ?: emptyList()
            val recipient = RecipientContactInfo(
                email = null,
                activeFcmTokens = userDevices.map { it.fcmToken }.distinct()
            )

            val notification = Notification(
                id = UUID.randomUUID(),
                recipientUserId = userId,
                title = title,
                content = content,
                metadata = metadata
            )

            channels.forEach { channel ->
                senders.filter { it.supports(channel) }.forEach { sender ->
                    try {
                        sender.send(notification, recipient)
                    } catch (e: Exception) {
                        log.error("Failed to send notification via channel $channel to user $userId: ${e.message}")
                    }
                }
            }
        }

        log.info("Finished background notification dispatch successfully!")
    }
}
