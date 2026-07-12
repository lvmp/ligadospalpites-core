package com.ligadospalpites.groups.infrastructure.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tbl_groups")
class GroupJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 100)
    val name: String = "",

    @Column(name = "creator_id", nullable = false)
    val creatorId: UUID = UUID.randomUUID(),

    @Column(name = "scoring_rules_json", nullable = false, length = 1000)
    val scoringRulesJson: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
