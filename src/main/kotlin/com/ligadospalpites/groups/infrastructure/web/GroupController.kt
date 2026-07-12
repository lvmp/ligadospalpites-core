package com.ligadospalpites.groups.infrastructure.web

import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.groups.infrastructure.persistence.RedisLeaderboardRepository
import com.ligadospalpites.groups.infrastructure.persistence.GroupMemberId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/groups")
class GroupController(
    private val groupRepository: SpringDataGroupRepository,
    private val memberRepository: SpringDataGroupMemberRepository,
    private val leaderboardRepository: RedisLeaderboardRepository
) {

    // 1. Kick/Remove member (Restricted to group creator)
    @DeleteMapping("/{groupId}/members/{memberUserId}")
    fun expelMember(
        @PathVariable groupId: UUID,
        @PathVariable memberUserId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Any> {
        val requesterUUID = userIdHeader?.let { UUID.fromString(it) } ?: UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")

        val group = groupRepository.findById(groupId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "GROUP_NOT_FOUND", "message" to "Grupo não encontrado."))

        // Validation: Verify if requester is the creator/admin
        if (group.creatorId != requesterUUID) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "NOT_GROUP_ADMIN", "message" to "Apenas o criador do grupo pode expulsar membros."))
        }

        // Delete from DB
        val memberId = GroupMemberId(groupId = groupId, userId = memberUserId)
        if (memberRepository.existsById(memberId)) {
            memberRepository.deleteById(memberId)
        }

        // Delete from Redis ZSETs for all periods
        val phases = listOf("overall", "group-stage", "knockout")
        phases.forEach { phase ->
            val key = "leaderboard:group:$groupId:$phase"
            leaderboardRepository.removeUser(key, memberUserId)
        }

        return ResponseEntity.ok(mapOf("status" to "SUCCESS", "message" to "Membro removido com sucesso de todas as tabelas e leaderboards."))
    }

    // 2. Get leaderboard/ranking for specific phase
    @GetMapping("/{groupId}/leaderboard")
    fun getLeaderboard(
        @PathVariable groupId: UUID,
        @RequestParam(defaultValue = "overall") phase: String
    ): ResponseEntity<List<LeaderboardRow>> {
        val validPhases = listOf("overall", "group-stage", "knockout")
        val selectedPhase = if (phase in validPhases) phase else "overall"

        val key = "leaderboard:group:$groupId:$selectedPhase"
        val topUsers = leaderboardRepository.getTopUsers(key, 50)

        var position = 1
        val rows = topUsers.map { tuple ->
            val userIdStr = tuple.value ?: ""
            val score = tuple.score ?: 0.0

            // Simulate profile lookup (Firebase Auth/Firestore resolution)
            val displayName = when (userIdStr) {
                "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" -> "Você"
                else -> "Usuário ${userIdStr.take(5)}"
            }
            val avatarUrl = "https://api.dicebear.com/7.x/bottts/svg?seed=$userIdStr"

            LeaderboardRow(
                position = position++,
                userId = UUID.fromString(userIdStr),
                displayName = displayName,
                avatarUrl = avatarUrl,
                score = score.toInt()
            )
        }

        return ResponseEntity.ok(rows)
    }
}

// Response DTO
data class LeaderboardRow(
    val position: Int,
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String,
    val score: Int
)
