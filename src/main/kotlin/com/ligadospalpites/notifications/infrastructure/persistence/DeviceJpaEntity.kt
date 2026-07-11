package com.ligadospalpites.notifications.infrastructure.persistence

import com.ligadospalpites.notifications.domain.ports.Device
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_devices")
class DeviceJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Column(name = "device_id", nullable = false, unique = true)
    val deviceId: UUID = UUID.randomUUID(),

    @Column(name = "fcm_token", nullable = false, length = 255)
    val fcmToken: String = "",

    @Column(name = "device_type", nullable = false, length = 20)
    val deviceType: String = "",

    @Column(name = "registered_at", nullable = false)
    val registeredAt: Instant = Instant.now()
) {
    fun toDomain(): Device = Device(
        id = id,
        userId = userId,
        deviceId = deviceId,
        fcmToken = fcmToken,
        deviceType = deviceType
    )

    companion object {
        fun fromDomain(domain: Device): DeviceJpaEntity = DeviceJpaEntity(
            id = domain.id,
            userId = domain.userId,
            deviceId = domain.deviceId,
            fcmToken = domain.fcmToken,
            deviceType = domain.deviceType,
            registeredAt = Instant.now()
        )
    }
}
