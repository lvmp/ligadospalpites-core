package com.ligadospalpites.sportsfeed.infrastructure.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_leagues")
class LeagueJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 150)
    val name: String = "",

    @Column(name = "sport_id", nullable = false)
    val sportId: UUID = UUID.randomUUID(),

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
