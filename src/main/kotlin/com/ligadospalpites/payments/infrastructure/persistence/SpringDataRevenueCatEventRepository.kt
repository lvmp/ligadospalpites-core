package com.ligadospalpites.payments.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataRevenueCatEventRepository : JpaRepository<RevenueCatEventJpaEntity, String> {
}
