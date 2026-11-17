package com.ligadospalpites.groups.infrastructure.web

import com.ligadospalpites.groups.application.usecases.ExpelGroupMemberUseCase
import com.ligadospalpites.groups.application.usecases.GetGroupLeaderboardUseCase
import com.ligadospalpites.groups.domain.models.LeaderboardPhase
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.shared.identity.UserResolver
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/groups")
class GroupController(
    private val expelGroupMemberUseCase: ExpelGroupMemberUseCase,
    private val getGroupLeaderboardUseCase: GetGroupLeaderboardUseCase,
    private val userResolver: UserResolver
) {

    // 1. Kick/Remove member (Restricted to group creator)
    @SecurityRequirement(name = "bearerAuth")
    @SecurityRequirement(name = "X-User-Id")
    @DeleteMapping("/{groupId}/members/{memberUserId}")
    fun expelMember(
        @PathVariable groupId: UUID,
        @PathVariable memberUserId: UUID,
        @RequestHeader(value = "X-User-Id", required = false) userIdHeader: String?
    ): ResponseEntity<Map<String, String>> {
        val requesterUUID = userResolver.resolveByUidOrUuid(userIdHeader)

        expelGroupMemberUseCase.execute(
            ExpelGroupMemberUseCase.Command(
                groupId = GroupId(groupId),
                memberUserId = UserId(memberUserId),
                requesterId = UserId(requesterUUID)
            )
        )

        return ResponseEntity.ok(
            mapOf(
                "status" to "SUCCESS",
                "message" to "Membro removido com sucesso de todas as tabelas e leaderboards."
            )
        )
    }

    // 2. Get leaderboard/ranking for specific phase
    @GetMapping("/{groupId}/leaderboard")
    fun getLeaderboard(
        @PathVariable groupId: UUID,
        @RequestParam(defaultValue = "overall") phase: String
    ): ResponseEntity<List<LeaderboardRow>> {
        val selectedPhase = LeaderboardPhase.fromString(phase)

        val rankedMembers = getGroupLeaderboardUseCase.execute(
            GetGroupLeaderboardUseCase.Query(
                groupId = GroupId(groupId),
                phase = selectedPhase,
                limit = 50
            )
        )

        val rows = rankedMembers.map {
            LeaderboardRow(
                position = it.position,
                userId = it.userId,
                displayName = it.displayName,
                avatarUrl = it.avatarUrl,
                score = it.score
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
