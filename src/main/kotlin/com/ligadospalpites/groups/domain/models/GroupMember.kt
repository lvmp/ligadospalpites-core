package com.ligadospalpites.groups.domain.models

import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import java.time.Instant

data class GroupMember(
    val groupId: GroupId,
    val userId: UserId,
    val joinedAt: Instant = Instant.now(),
    val accumulatedPoints: Int = 0
) {
    fun addPoints(points: Int): GroupMember {
        return copy(accumulatedPoints = accumulatedPoints + points)
    }
}
