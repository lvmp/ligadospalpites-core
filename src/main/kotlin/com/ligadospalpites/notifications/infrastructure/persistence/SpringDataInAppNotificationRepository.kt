package com.ligadospalpites.notifications.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataInAppNotificationRepository : JpaRepository<InAppNotificationJpaEntity, UUID>
