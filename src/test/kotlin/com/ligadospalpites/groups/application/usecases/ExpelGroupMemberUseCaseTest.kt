package com.ligadospalpites.groups.application.usecases

import com.ligadospalpites.groups.domain.exceptions.GroupNotFoundException
import com.ligadospalpites.groups.domain.exceptions.NotGroupAdminException
import com.ligadospalpites.groups.domain.models.Group
import com.ligadospalpites.groups.domain.models.GroupMember
import com.ligadospalpites.groups.domain.models.LeaderboardPhase
import com.ligadospalpites.groups.domain.ports.GroupLeaderboardPort
import com.ligadospalpites.groups.domain.ports.GroupMemberRepository
import com.ligadospalpites.groups.domain.ports.GroupRepository
import com.ligadospalpites.groups.domain.ports.LeaderboardUserScore
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExpelGroupMemberUseCaseTest {

    private class FakeGroupRepository : GroupRepository {
        val groups = mutableMapOf<GroupId, Group>()

        override fun findById(id: GroupId): Group? = groups[id]
        override fun save(group: Group): Group {
            groups[group.id] = group
            return group
        }
        override fun existsById(id: GroupId): Boolean = groups.containsKey(id)
        override fun deleteById(id: GroupId) { groups.remove(id) }
    }

    private class FakeGroupMemberRepository : GroupMemberRepository {
        val members = mutableMapOf<Pair<GroupId, UserId>, GroupMember>()

        override fun findByGroupIdAndUserId(groupId: GroupId, userId: UserId): GroupMember? =
            members[Pair(groupId, userId)]

        override fun findByUserId(userId: UserId): List<GroupMember> =
            members.values.filter { it.userId == userId }

        override fun existsById(groupId: GroupId, userId: UserId): Boolean =
            members.containsKey(Pair(groupId, userId))

        override fun countByGroupId(groupId: GroupId): Long =
            members.values.count { it.groupId == groupId }.toLong()

        override fun delete(groupId: GroupId, userId: UserId) {
            members.remove(Pair(groupId, userId))
        }

        override fun incrementUserPoints(groupId: GroupId, userId: UserId, points: Int): Int {
            val key = Pair(groupId, userId)
            val current = members[key]
            if (current != null) {
                members[key] = current.addPoints(points)
                return 1
            }
            return 0
        }

        override fun save(member: GroupMember): GroupMember {
            members[Pair(member.groupId, member.userId)] = member
            return member
        }
    }

    private class FakeLeaderboardPort : GroupLeaderboardPort {
        val removals = mutableListOf<Pair<GroupId, UserId>>()

        override fun incrementScore(groupId: GroupId, phase: LeaderboardPhase, userId: UserId, points: Int) {}
        override fun getTopUsers(groupId: GroupId, phase: LeaderboardPhase, limit: Long): List<LeaderboardUserScore> = emptyList()
        override fun getUserRankAndScore(groupId: GroupId, phase: LeaderboardPhase, userId: UserId): Pair<Long?, Double?> = Pair(null, null)
        override fun removeUser(groupId: GroupId, phase: LeaderboardPhase, userId: UserId) {
            removals.add(Pair(groupId, userId))
        }
        override fun removeUserFromAllPhases(groupId: GroupId, userId: UserId) {
            removals.add(Pair(groupId, userId))
        }
    }

    private lateinit var groupRepository: FakeGroupRepository
    private lateinit var groupMemberRepository: FakeGroupMemberRepository
    private lateinit var leaderboardPort: FakeLeaderboardPort
    private lateinit var useCase: ExpelGroupMemberUseCase

    private val creatorId = UserId.random()
    private val memberId = UserId.random()
    private val nonAdminId = UserId.random()
    private val groupId = GroupId.random()

    @BeforeEach
    fun setUp() {
        groupRepository = FakeGroupRepository()
        groupMemberRepository = FakeGroupMemberRepository()
        leaderboardPort = FakeLeaderboardPort()
        useCase = ExpelGroupMemberUseCase(groupRepository, groupMemberRepository, leaderboardPort)
    }

    @Test
    fun `should expel member successfully when requester is group creator`() {
        val group = Group(
            id = groupId,
            name = "Futebol dos Amigos",
            creatorId = creatorId
        )
        groupRepository.save(group)

        val member = GroupMember(
            groupId = groupId,
            userId = memberId,
            accumulatedPoints = 10
        )
        groupMemberRepository.save(member)

        useCase.execute(
            ExpelGroupMemberUseCase.Command(
                groupId = groupId,
                memberUserId = memberId,
                requesterId = creatorId
            )
        )

        assertFalse(groupMemberRepository.existsById(groupId, memberId))
        assertEquals(1, leaderboardPort.removals.size)
        assertEquals(Pair(groupId, memberId), leaderboardPort.removals.first())
    }

    @Test
    fun `should throw GroupNotFoundException when group does not exist`() {
        assertThrows<GroupNotFoundException> {
            useCase.execute(
                ExpelGroupMemberUseCase.Command(
                    groupId = groupId,
                    memberUserId = memberId,
                    requesterId = creatorId
                )
            )
        }
    }

    @Test
    fun `should throw NotGroupAdminException when non-creator tries to expel member`() {
        val group = Group(
            id = groupId,
            name = "Futebol dos Amigos",
            creatorId = creatorId
        )
        groupRepository.save(group)

        assertThrows<NotGroupAdminException> {
            useCase.execute(
                ExpelGroupMemberUseCase.Command(
                    groupId = groupId,
                    memberUserId = memberId,
                    requesterId = nonAdminId
                )
            )
        }
    }
}
