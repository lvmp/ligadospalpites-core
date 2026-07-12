package com.ligadospalpites.sportsfeed.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataSportRepository : JpaRepository<SportJpaEntity, UUID>
