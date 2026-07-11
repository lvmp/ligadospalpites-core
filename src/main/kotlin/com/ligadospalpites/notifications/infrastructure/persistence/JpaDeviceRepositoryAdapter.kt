package com.ligadospalpites.notifications.infrastructure.persistence

import com.ligadospalpites.notifications.domain.ports.Device
import com.ligadospalpites.notifications.domain.ports.DeviceRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class JpaDeviceRepositoryAdapter(
    private val springDataRepository: SpringDataDeviceRepository
) : DeviceRepository {

    @Transactional
    override fun deleteByFcmToken(fcmToken: String) {
        springDataRepository.deleteByFcmToken(fcmToken)
    }

    override fun findByFcmToken(fcmToken: String): Device? {
        return springDataRepository.findByFcmToken(fcmToken)?.toDomain()
    }

    override fun findByDeviceId(deviceId: UUID): Device? {
        return springDataRepository.findByDeviceId(deviceId)?.toDomain()
    }

    @Transactional
    override fun delete(device: Device) {
        springDataRepository.delete(DeviceJpaEntity.fromDomain(device))
    }

    @Transactional
    override fun save(device: Device): Device {
        val entity = DeviceJpaEntity.fromDomain(device)
        return springDataRepository.save(entity).toDomain()
    }
}
