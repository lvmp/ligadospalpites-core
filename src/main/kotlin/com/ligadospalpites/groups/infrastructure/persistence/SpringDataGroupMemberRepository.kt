package com.ligadospalpites.groups.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataGroupMemberRepository : JpaRepository<GroupMemberJpaEntity, GroupMemberId> {

    @Modifying
    @Query("""
        UPDATE GroupMemberJpaEntity g 
        SET g.accumulatedPoints = g.accumulatedPoints + :points 
        WHERE g.groupId = :groupId AND g.userId = :userId
    """)
    fun incrementUserPoints(
        @Param("groupId") groupId: UUID,
        @Param("userId") userId: UUID,
        @Param("points") points: Int
    ): Int
}
