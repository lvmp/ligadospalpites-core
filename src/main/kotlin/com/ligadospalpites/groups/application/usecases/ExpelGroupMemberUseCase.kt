package com.ligadospalpites.groups.application.usecases

import com.ligadospalpites.groups.domain.exceptions.GroupNotFoundException
import com.ligadospalpites.groups.domain.ports.GroupLeaderboardPort
import com.ligadospalpites.groups.domain.ports.GroupMemberRepository
import com.ligadospalpites.groups.domain.ports.GroupRepository
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExpelGroupMemberUseCase(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val leaderboardPort: GroupLeaderboardPort
) {
    data class Command(
        val groupId: GroupId,
        val memberUserId: UserId,
        val requesterId: UserId
    )

    @Transactional
    fun execute(command: Command) {
        val group = groupRepository.findById(command.groupId)
            ?: throw GroupNotFoundException("Grupo não encontrado.")

        // Enforce domain invariant: only creator can expel members
        group.validateCanExpel(command.requesterId)

        // Delete from SQL
        groupMemberRepository.delete(command.groupId, command.memberUserId)

        // Delete from Redis ZSETs for all leaderboard phases
        leaderboardPort.removeUserFromAllPhases(command.groupId, command.memberUserId)
    }
}
