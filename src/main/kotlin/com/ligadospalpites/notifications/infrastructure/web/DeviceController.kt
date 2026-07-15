package com.ligadospalpites.notifications.infrastructure.web

import com.ligadospalpites.notifications.application.usecases.RegisterDeviceRequest
import com.ligadospalpites.notifications.application.usecases.RegisterDeviceUseCase
import com.ligadospalpites.shared.identity.UserResolver
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications/devices")
class DeviceController(
    private val registerDeviceUseCase: RegisterDeviceUseCase,
    private val userResolver: UserResolver
) {

    @PostMapping("/register")
    fun registerDevice(
        @RequestBody request: DeviceRegistrationRequest,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Any> {
        val userUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        val deviceUUID = try {
            UUID.fromString(request.deviceId)
        } catch (e: IllegalArgumentException) {
            UUID.nameUUIDFromBytes(request.deviceId.toByteArray())
        }

        registerDeviceUseCase.registerDevice(
            userId = userUUID,
            request = RegisterDeviceRequest(
                deviceId = deviceUUID,
                fcmToken = request.fcmToken,
                deviceType = request.deviceType.uppercase()
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "status" to "SUCCESS",
                "message" to "Dispositivo e canais de comunicação registrados com sucesso!"
            )
        )
    }
}

data class DeviceRegistrationRequest(
    val deviceId: String,
    val fcmToken: String,
    val deviceType: String,
    val receiveEmail: Boolean? = true,
    val receiveSms: Boolean? = false,
    val receivePush: Boolean? = true
)
