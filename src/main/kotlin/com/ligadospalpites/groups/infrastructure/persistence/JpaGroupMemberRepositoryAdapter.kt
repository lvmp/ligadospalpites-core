package com.ligadospalpites.groups.infrastructure.persistence

import com.ligadospalpites.groups.domain.models.GroupMember
import com.ligadospalpites.groups.domain.ports.GroupMemberRepository
import com.ligadospalpites.shared.domain.models.GroupId
import com.ligadospalpites.shared.domain.models.UserId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class JpaGroupMemberRepositoryAdapter(
    private val springDataRepository: SpringDataGroupMemberRepository
) : GroupMemberRepository {

    override fun findByGroupIdAndUserId(groupId: GroupId, userId: UserId): GroupMember? {
        val memberId = GroupMemberId(groupId = groupId.value, userId = userId.value)
        return springDataRepository.findById(memberId).map { it.toDomain() }.orElse(null)
    }

    override fun findByUserId(userId: UserId): List<GroupMember> {
        return springDataRepository.findByUserId(userId.value).map { it.toDomain() }
    }

    override fun existsById(groupId: GroupId, userId: UserId): Boolean {
        val memberId = GroupMemberId(groupId = groupId.value, userId = userId.value)
        return springDataRepository.existsById(memberId)
    }

    override fun countByGroupId(groupId: GroupId): Long {
        return springDataRepository.countByGroupId(groupId.value).toLong()
    }

    @Transactional
    override fun delete(groupId: GroupId, userId: UserId) {
        val memberId = GroupMemberId(groupId = groupId.value, userId = userId.value)
        if (springDataRepository.existsById(memberId)) {
            springDataRepository.deleteById(memberId)
        }
    }

    @Transactional
    override fun incrementUserPoints(groupId: GroupId, userId: UserId, points: Int): Int {
        return springDataRepository.incrementUserPoints(groupId.value, userId.value, points)
    }

    override fun save(member: GroupMember): GroupMember {
        val entity = GroupMemberJpaEntity.fromDomain(member)
        return springDataRepository.save(entity).toDomain()
    }
}
