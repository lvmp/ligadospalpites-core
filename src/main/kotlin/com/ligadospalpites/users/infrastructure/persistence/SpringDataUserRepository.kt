package com.ligadospalpites.users.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataUserRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByFirebaseUid(firebaseUid: String): UserJpaEntity?
}
