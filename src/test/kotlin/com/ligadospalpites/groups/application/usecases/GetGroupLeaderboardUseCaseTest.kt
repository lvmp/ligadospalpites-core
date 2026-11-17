package com.ligadospalpites.groups.application.usecases

import com.ligadospalpites.groups.domain.models.LeaderboardPhase
import com.ligadospalpites.groups.domain.ports.GroupLeaderboardPort
import com.ligadospalpites.groups.domain.ports.LeaderboardUserScore
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class GetGroupLeaderboardUseCaseTest {

    private class FakeLeaderboardPort : GroupLeaderboardPort {
        val scores = mutableListOf<LeaderboardUserScore>()

        override fun incrementScore(groupId: GroupId, phase: LeaderboardPhase, userId: UserId, points: Int) {}
        override fun getTopUsers(groupId: GroupId, phase: LeaderboardPhase, limit: Long): List<LeaderboardUserScore> = scores
        override fun getUserRankAndScore(groupId: GroupId, phase: LeaderboardPhase, userId: UserId): Pair<Long?, Double?> = Pair(null, null)
        override fun removeUser(groupId: GroupId, phase: LeaderboardPhase, userId: UserId) {}
        override fun removeUserFromAllPhases(groupId: GroupId, userId: UserId) {}
    }

    private class FakeUserRepository : UserRepository {
        val users = mutableMapOf<UUID, User>()

        override fun findByFirebaseUid(firebaseUid: String): User? = null
        override fun findById(id: UUID): User? = users[id]
        override fun findAllById(ids: Collection<UUID>): List<User> = ids.mapNotNull { users[it] }
        override fun save(user: User): User {
            users[user.id] = user
            return user
        }
    }

    private lateinit var leaderboardPort: FakeLeaderboardPort
    private lateinit var userRepository: FakeUserRepository
    private lateinit var useCase: GetGroupLeaderboardUseCase

    private val groupId = GroupId.random()
    private val user1Id = UserId.random()
    private val user2Id = UserId.random()

    @BeforeEach
    fun setUp() {
        leaderboardPort = FakeLeaderboardPort()
        userRepository = FakeUserRepository()
        useCase = GetGroupLeaderboardUseCase(leaderboardPort, userRepository)
    }

    @Test
    fun `should return enriched leaderboard rows ordered by score`() {
        leaderboardPort.scores.add(LeaderboardUserScore(user1Id, 100.0))
        leaderboardPort.scores.add(LeaderboardUserScore(user2Id, 75.0))

        userRepository.save(
            User(
                id = user1Id.value,
                firebaseUid = "uid-1",
                email = "user1@test.com",
                name = "Carlos Artilheiro"
            )
        )
        userRepository.save(
            User(
                id = user2Id.value,
                firebaseUid = "uid-2",
                email = "user2@test.com",
                name = "Bruno Zagueiro"
            )
        )

        val result = useCase.execute(
            GetGroupLeaderboardUseCase.Query(
                groupId = groupId,
                phase = LeaderboardPhase.OVERALL,
                limit = 50
            )
        )

        assertEquals(2, result.size)

        assertEquals(1, result[0].position)
        assertEquals(user1Id.value, result[0].userId)
        assertEquals("Carlos Artilheiro", result[0].displayName)
        assertEquals(100, result[0].score)

        assertEquals(2, result[1].position)
        assertEquals(user2Id.value, result[1].userId)
        assertEquals("Bruno Zagueiro", result[1].displayName)
        assertEquals(75, result[1].score)
    }

    @Test
    fun `should return empty list when leaderboard has no scores`() {
        val result = useCase.execute(
            GetGroupLeaderboardUseCase.Query(
                groupId = groupId,
                phase = LeaderboardPhase.OVERALL,
                limit = 50
            )
        )

        assertEquals(0, result.size)
    }
}
