package com.ligadospalpites.groups.infrastructure.persistence

import com.ligadospalpites.groups.domain.models.Group
import com.ligadospalpites.groups.domain.ports.GroupRepository
import com.ligadospalpites.shared.domain.models.GroupId
import org.springframework.stereotype.Component

@Component
class JpaGroupRepositoryAdapter(
    private val springDataGroupRepository: SpringDataGroupRepository
) : GroupRepository {

    override fun findById(id: GroupId): Group? {
        return springDataGroupRepository.findById(id.value).map { it.toDomain() }.orElse(null)
    }

    override fun save(group: Group): Group {
        val entity = GroupJpaEntity.fromDomain(group)
        return springDataGroupRepository.save(entity).toDomain()
    }

    override fun existsById(id: GroupId): Boolean {
        return springDataGroupRepository.existsById(id.value)
    }

    override fun deleteById(id: GroupId) {
        springDataGroupRepository.deleteById(id.value)
    }
}
