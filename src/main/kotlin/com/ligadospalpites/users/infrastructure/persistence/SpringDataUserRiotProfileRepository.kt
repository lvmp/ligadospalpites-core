package com.ligadospalpites.users.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface SpringDataUserRiotProfileRepository : JpaRepository<UserRiotProfileJpaEntity, UUID> {
    fun findByUserId(userId: UUID): Optional<UserRiotProfileJpaEntity>
    fun findByPuuid(puuid: String): Optional<UserRiotProfileJpaEntity>
}
