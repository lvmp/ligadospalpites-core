package com.ligadospalpites.notifications.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataDeviceRepository : JpaRepository<DeviceJpaEntity, UUID> {
    fun findByFcmToken(fcmToken: String): DeviceJpaEntity?
    fun findByDeviceId(deviceId: UUID): DeviceJpaEntity?
    fun deleteByFcmToken(fcmToken: String)
    fun findAllByUserId(userId: UUID): List<DeviceJpaEntity>
    fun findByUserIdIn(userIds: List<UUID>): List<DeviceJpaEntity>
}
