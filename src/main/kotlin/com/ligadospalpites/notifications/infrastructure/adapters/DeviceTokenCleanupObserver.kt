package com.ligadospalpites.notifications.infrastructure.adapters

import com.ligadospalpites.notifications.domain.ports.DeviceRepository
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DeviceTokenCleanupObserver(private val deviceRepository: DeviceRepository) {

    @Async
    @EventListener
    @Transactional
    fun handleExpiredToken(event: DeviceTokenExpiredEvent) {
        deviceRepository.deleteByFcmToken(event.fcmToken)
    }
}
