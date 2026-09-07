package com.ligadospalpites.notifications.infrastructure.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataInAppNotificationRepository : JpaRepository<InAppNotificationJpaEntity, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, pageable: Pageable): Page<InAppNotificationJpaEntity>
    fun countByUserId(userId: UUID): Long
    fun countByUserIdAndIsReadFalse(userId: UUID): Long
    fun existsByUserIdAndIsReadFalse(userId: UUID): Boolean

    @Modifying
    @Query("UPDATE InAppNotificationJpaEntity n SET n.isRead = true WHERE n.id = :id AND n.userId = :userId")
    fun markAsRead(@Param("id") id: UUID, @Param("userId") userId: UUID): Int

    @Modifying
    @Query("UPDATE InAppNotificationJpaEntity n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    fun markAllAsRead(@Param("userId") userId: UUID): Int
}
