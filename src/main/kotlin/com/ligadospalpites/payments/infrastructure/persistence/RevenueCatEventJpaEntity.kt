package com.ligadospalpites.payments.infrastructure.persistence

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "tbl_revenuecat_events")
class RevenueCatEventJpaEntity(
    @Id
    val id: String = "",

    @Column(nullable = false, length = 100)
    val type: String = "",

    @Column(name = "app_user_id", nullable = false, length = 128)
    val appUserId: String = "",

    @Column(name = "product_id", nullable = false, length = 255)
    val productId: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String = "",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
