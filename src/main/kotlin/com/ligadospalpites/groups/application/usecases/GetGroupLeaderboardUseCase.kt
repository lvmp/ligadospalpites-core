package com.ligadospalpites.groups.application.usecases

import com.ligadospalpites.groups.domain.models.LeaderboardPhase
import com.ligadospalpites.groups.domain.ports.GroupLeaderboardPort
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.users.domain.ports.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetGroupLeaderboardUseCase(
    private val leaderboardPort: GroupLeaderboardPort,
    private val userRepository: UserRepository
) {
    data class Query(
        val groupId: GroupId,
        val phase: LeaderboardPhase,
        val limit: Long = 50
    )

    data class RankedMember(
        val position: Int,
        val userId: UUID,
        val displayName: String,
        val avatarUrl: String,
        val score: Int
    )

    @Transactional(readOnly = true)
    fun execute(query: Query): List<RankedMember> {
        val topUsers = leaderboardPort.getTopUsers(query.groupId, query.phase, query.limit)
        if (topUsers.isEmpty()) return emptyList()

        val userIds = topUsers.map { it.userId.value }
        val users = userRepository.findAllById(userIds)
        val userMap = users.associateBy { it.id }

        var position = 1
        return topUsers.map { tuple ->
            val user = userMap[tuple.userId.value]
            val displayName = user?.name ?: "Usuário ${tuple.userId.value.toString().take(5)}"
            val avatarUrl = user?.avatarUrl ?: "https://api.dicebear.com/7.x/bottts/svg?seed=${tuple.userId.value}"

            RankedMember(
                position = position++,
                userId = tuple.userId.value,
                displayName = displayName,
                avatarUrl = avatarUrl,
                score = tuple.score.toInt()
            )
        }
    }
}
