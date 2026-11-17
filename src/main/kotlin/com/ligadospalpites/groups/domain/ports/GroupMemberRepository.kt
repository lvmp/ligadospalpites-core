package com.ligadospalpites.groups.domain.ports

import com.ligadospalpites.groups.domain.models.GroupMember
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId

interface GroupMemberRepository {
    fun findByGroupIdAndUserId(groupId: GroupId, userId: UserId): GroupMember?
    fun findByUserId(userId: UserId): List<GroupMember>
    fun existsById(groupId: GroupId, userId: UserId): Boolean
    fun countByGroupId(groupId: GroupId): Long
    fun delete(groupId: GroupId, userId: UserId)
    fun incrementUserPoints(groupId: GroupId, userId: UserId, points: Int): Int
    fun save(member: GroupMember): GroupMember
}
