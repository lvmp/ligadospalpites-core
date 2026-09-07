package com.ligadospalpites.notifications.infrastructure.persistence

import com.ligadospalpites.notifications.domain.models.InAppNotification
import com.ligadospalpites.notifications.domain.ports.InAppNotificationRepository
import org.springframework.data.domain.PageRequest
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

    @Transactional(readOnly = true)
    override fun findByUserId(userId: UUID, page: Int, size: Int): List<InAppNotification> {
        val pageable = PageRequest.of(page, size)
        return springDataRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .content
            .map { it.toDomain() }
    }

    @Transactional(readOnly = true)
    override fun countTotalByUserId(userId: UUID): Long {
        return springDataRepository.countByUserId(userId)
    }

    @Transactional(readOnly = true)
    override fun countUnreadByUserId(userId: UUID): Long {
        return springDataRepository.countByUserIdAndIsReadFalse(userId)
    }

    @Transactional
    override fun markAsRead(id: UUID, userId: UUID): Boolean {
        return springDataRepository.markAsRead(id, userId) > 0
    }

    @Transactional
    override fun markAllAsRead(userId: UUID) {
        springDataRepository.markAllAsRead(userId)
    }

    private fun InAppNotificationJpaEntity.toDomain(): InAppNotification {
        return InAppNotification(
            id = this.id,
            userId = this.userId,
            title = this.title,
            content = this.content,
            isRead = this.isRead,
            createdAt = this.createdAt
        )
    }
}
