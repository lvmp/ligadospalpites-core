package com.ligadospalpites.users.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface SpringDataUserRepository : JpaRepository<UserJpaEntity, UUID>, JpaSpecificationExecutor<UserJpaEntity> {
    fun findByFirebaseUid(firebaseUid: String): UserJpaEntity?
}
