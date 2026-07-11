package com.ligadospalpites.users.infrastructure.persistence

import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class JpaUserRepositoryAdapter(
    private val springDataUserRepository: SpringDataUserRepository
) : UserRepository {

    override fun findByFirebaseUid(firebaseUid: String): User? {
        return springDataUserRepository.findByFirebaseUid(firebaseUid)?.toDomain()
    }

    override fun findById(id: UUID): User? {
        return springDataUserRepository.findById(id).map { it.toDomain() }.orElse(null)
    }

    override fun save(user: User): User {
        val entity = UserJpaEntity.fromDomain(user)
        return springDataUserRepository.save(entity).toDomain()
    }
}
