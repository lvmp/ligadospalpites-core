package com.ligadospalpites.notifications.application.usecases

import com.ligadospalpites.notifications.domain.ports.Device
import com.ligadospalpites.notifications.domain.ports.DeviceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class RegisterDeviceRequest(
    val deviceId: UUID,
    val fcmToken: String,
    val deviceType: String
)

@Service
class RegisterDeviceUseCase(private val deviceRepository: DeviceRepository) {

    @Transactional
    fun registerDevice(userId: UUID, request: RegisterDeviceRequest) {
        // 1. Ownership Transfer check
        deviceRepository.findByFcmToken(request.fcmToken)?.let { existing ->
            if (existing.userId != userId) {
                deviceRepository.delete(existing)
            }
        }

        // 2. Upsert token linked to user
        val device = deviceRepository.findByDeviceId(request.deviceId)
            ?.copy(fcmToken = request.fcmToken, userId = userId)
            ?: Device(
                id = UUID.randomUUID(),
                userId = userId,
                deviceId = request.deviceId,
                fcmToken = request.fcmToken,
                deviceType = request.deviceType
            )
        deviceRepository.save(device)
    }
}
