package com.ligadospalpites.groups.infrastructure.persistence

import com.ligadospalpites.groups.domain.models.LeaderboardPhase
import com.ligadospalpites.groups.domain.ports.GroupLeaderboardPort
import com.ligadospalpites.groups.domain.ports.LeaderboardUserScore
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RedisGroupLeaderboardAdapter(
    private val redisLeaderboardRepository: RedisLeaderboardRepository
) : GroupLeaderboardPort {

    override fun incrementScore(groupId: GroupId, phase: LeaderboardPhase, userId: UserId, points: Int) {
        val key = buildKey(groupId, phase)
        redisLeaderboardRepository.incrementScore(key, userId.value, points)
    }

    override fun getTopUsers(groupId: GroupId, phase: LeaderboardPhase, limit: Long): List<LeaderboardUserScore> {
        val key = buildKey(groupId, phase)
        val tuples = redisLeaderboardRepository.getTopUsers(key, limit)
        return tuples.mapNotNull { tuple ->
            val userIdStr = tuple.value ?: return@mapNotNull null
            val score = tuple.score ?: 0.0
            val userId = try {
                UserId(UUID.fromString(userIdStr))
            } catch (e: Exception) {
                return@mapNotNull null
            }
            LeaderboardUserScore(userId = userId, score = score)
        }
    }

    override fun getUserRankAndScore(groupId: GroupId, phase: LeaderboardPhase, userId: UserId): Pair<Long?, Double?> {
        val key = buildKey(groupId, phase)
        return redisLeaderboardRepository.getUserRankAndScore(key, userId.value)
    }

    override fun removeUser(groupId: GroupId, phase: LeaderboardPhase, userId: UserId) {
        val key = buildKey(groupId, phase)
        redisLeaderboardRepository.removeUser(key, userId.value)
    }

    override fun removeUserFromAllPhases(groupId: GroupId, userId: UserId) {
        LeaderboardPhase.entries.forEach { phase ->
            removeUser(groupId, phase, userId)
        }
    }

    private fun buildKey(groupId: GroupId, phase: LeaderboardPhase): String =
        "leaderboard:group:${groupId.value}:${phase.keySuffix}"
}
