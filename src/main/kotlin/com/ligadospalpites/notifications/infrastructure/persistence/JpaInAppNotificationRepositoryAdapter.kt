package com.ligadospalpites.notifications.infrastructure.persistence

import com.ligadospalpites.notifications.domain.ports.InAppNotificationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class JpaInAppNotificationRepositoryAdapter(
    private val springDataRepository: SpringDataInAppNotificationRepository
) : InAppNotificationRepository {

    @Transactional
    override fun save(userId: UUID, title: String, content: String) {
        val entity = InAppNotificationJpaEntity(
            id = UUID.randomUUID(),
            userId = userId,
            title = title,
            content = content
        )
        springDataRepository.save(entity)
    }
}
