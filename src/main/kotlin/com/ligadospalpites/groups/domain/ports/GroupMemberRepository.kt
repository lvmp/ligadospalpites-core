package com.ligadospalpites.groups.domain.ports

import java.util.UUID

interface GroupMemberRepository {
    fun incrementUserPoints(groupId: UUID, userId: UUID, points: Int)
}
