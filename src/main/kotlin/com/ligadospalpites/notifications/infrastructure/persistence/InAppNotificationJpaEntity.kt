package com.ligadospalpites.notifications.infrastructure.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_in_app_notifications")
class InAppNotificationJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 200)
    val title: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String = "",

    @Column(name = "is_read", nullable = false)
    val isRead: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
