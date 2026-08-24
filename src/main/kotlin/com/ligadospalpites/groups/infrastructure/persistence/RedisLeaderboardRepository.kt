package com.ligadospalpites.groups.infrastructure.persistence

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.stereotype.Repository
import java.util.UUID

import org.springframework.data.redis.core.RedisCallback

data class ScoreIncrementItem(val key: String, val userId: UUID, val points: Int)

@Repository
class RedisLeaderboardRepository(private val redisTemplate: StringRedisTemplate) {

    fun incrementScore(leaderboardKey: String, userId: UUID, points: Int) {
        redisTemplate.opsForZSet().incrementScore(leaderboardKey, userId.toString(), points.toDouble())
    }

    fun incrementScoresPipelined(items: List<ScoreIncrementItem>) {
        if (items.isEmpty()) return
        redisTemplate.executePipelined(RedisCallback<Any?> { connection ->
            val zSetConn = connection.zSetCommands()
            items.forEach { item ->
                zSetConn.zIncrBy(
                    item.key.toByteArray(),
                    item.points.toDouble(),
                    item.userId.toString().toByteArray()
                )
            }
            null
        })
    }

    fun getTopUsers(leaderboardKey: String, limit: Long): Set<ZSetOperations.TypedTuple<String>> {
        return redisTemplate.opsForZSet().reverseRangeWithScores(leaderboardKey, 0, limit - 1) ?: emptySet()
    }

    fun getUserRankAndScore(leaderboardKey: String, userId: UUID): Pair<Long?, Double?> {
        val rankZeroBased = redisTemplate.opsForZSet().reverseRank(leaderboardKey, userId.toString())
        val score = redisTemplate.opsForZSet().score(leaderboardKey, userId.toString())
        val rankOneBased = rankZeroBased?.let { it + 1 }
        return Pair(rankOneBased, score)
    }

    fun removeUser(leaderboardKey: String, userId: UUID) {
        redisTemplate.opsForZSet().remove(leaderboardKey, userId.toString())
    }
}
