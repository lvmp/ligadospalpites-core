package com.ligadospalpites.groups.infrastructure.persistence

import com.ligadospalpites.groups.domain.ports.GroupMemberRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class JpaGroupMemberRepositoryAdapter(
    private val springDataRepository: SpringDataGroupMemberRepository
) : GroupMemberRepository {

    @Transactional
    override fun incrementUserPoints(groupId: UUID, userId: UUID, points: Int) {
        springDataRepository.incrementUserPoints(groupId, userId, points)
    }
}
