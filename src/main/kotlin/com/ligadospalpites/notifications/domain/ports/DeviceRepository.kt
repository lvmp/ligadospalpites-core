package com.ligadospalpites.notifications.domain.ports

import java.util.UUID

interface DeviceRepository {
    fun deleteByFcmToken(fcmToken: String)
    fun findByFcmToken(fcmToken: String): Device?
    fun findByDeviceId(deviceId: UUID): Device?
    fun delete(device: Device)
    fun save(device: Device): Device
}

data class Device(
    val id: UUID,
    val userId: UUID,
    val deviceId: UUID,
    val fcmToken: String,
    val deviceType: String
)
