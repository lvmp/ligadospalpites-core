package com.ligadospalpites.groups.infrastructure.persistence

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

class GroupMemberId(
    val groupId: UUID = UUID.randomUUID(),
    val userId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "tbl_group_members")
@IdClass(GroupMemberId::class)
class GroupMemberJpaEntity(
    @Id
    @Column(name = "group_id")
    val groupId: UUID = UUID.randomUUID(),

    @Id
    @Column(name = "user_id")
    val userId: UUID = UUID.randomUUID(),

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "accumulated_points", nullable = false)
    val accumulatedPoints: Int = 0
)
