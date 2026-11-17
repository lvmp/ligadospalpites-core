package com.ligadospalpites.groups.domain.ports

import com.ligadospalpites.groups.domain.models.LeaderboardPhase
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId

data class LeaderboardUserScore(
    val userId: UserId,
    val score: Double
)

interface GroupLeaderboardPort {
    fun incrementScore(groupId: GroupId, phase: LeaderboardPhase, userId: UserId, points: Int)
    fun getTopUsers(groupId: GroupId, phase: LeaderboardPhase, limit: Long): List<LeaderboardUserScore>
    fun getUserRankAndScore(groupId: GroupId, phase: LeaderboardPhase, userId: UserId): Pair<Long?, Double?>
    fun removeUser(groupId: GroupId, phase: LeaderboardPhase, userId: UserId)
    fun removeUserFromAllPhases(groupId: GroupId, userId: UserId)
}
