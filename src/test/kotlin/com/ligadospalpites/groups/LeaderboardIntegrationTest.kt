package com.ligadospalpites.groups

import com.ligadospalpites.BaseIntegrationTest
import com.ligadospalpites.groups.infrastructure.persistence.GroupMemberId
import com.ligadospalpites.groups.infrastructure.persistence.GroupMemberJpaEntity
import com.ligadospalpites.groups.infrastructure.persistence.RedisLeaderboardRepository
import com.ligadospalpites.groups.infrastructure.persistence.SpringDataGroupMemberRepository
import com.ligadospalpites.predictions.domain.events.PredictionsProcessedEvent
import com.ligadospalpites.predictions.domain.events.UserScoreUpdateDto
import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class LeaderboardIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var groupMemberRepository: SpringDataGroupMemberRepository

    @Autowired
    private lateinit var redisLeaderboardRepository: RedisLeaderboardRepository

    @Autowired
    private lateinit var transactionTemplate: org.springframework.transaction.support.TransactionTemplate

    @Autowired
    private lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate

    @Test
    fun `should asynchronously update SQL and Redis rankings on PredictionsProcessedEvent`() {
        // Arrange
        val user = userRepository.save(
            User(
                id = UUID.randomUUID(),
                firebaseUid = "firebase-uid-leaderboard-test",
                email = "leaderboard@test.com",
                name = "Ranking Champion"
            )
        )

        val groupId = UUID.randomUUID()
        // Save group using native SQL (bypassing full group model implementation details for testing)
        jdbcTemplate.update(
            "INSERT INTO tbl_groups (id, name, creator_id, scoring_rules_json) VALUES (?, ?, ?, ?)",
            groupId, "Test Group", user.id, "{}"
        )

        groupMemberRepository.save(
            GroupMemberJpaEntity(
                groupId = groupId,
                userId = user.id,
                joinedAt = Instant.now(),
                accumulatedPoints = 100
            )
        )

        val event = PredictionsProcessedEvent(
            leagueId = groupId,
            scores = listOf(UserScoreUpdateDto(user.id, 25))
        )

        // Act
        transactionTemplate.executeWithoutResult {
            eventPublisher.publishEvent(event)
        }

        // Wait brief moments for async execution to complete
        Thread.sleep(1000)

        // Assert Postgres Database points updated
        val memberInDb = groupMemberRepository.findById(GroupMemberId(groupId, user.id)).orElse(null)
        assertNotNull(memberInDb)
        assertEquals(125, memberInDb.accumulatedPoints)

        // Assert Redis rankings updated
        val (groupRank, groupScore) = redisLeaderboardRepository.getUserRankAndScore("leaderboard:group:$groupId", user.id)
        assertNotNull(groupScore)
        assertEquals(25.0, groupScore)
        assertEquals(1L, groupRank)

        val (globalRank, globalScore) = redisLeaderboardRepository.getUserRankAndScore("leaderboard:global", user.id)
        assertNotNull(globalScore)
        assertEquals(25.0, globalScore)
        assertEquals(1L, globalRank)
    }
}
