package com.ligadospalpites.users.domain.models

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val firebaseUid: String,
    val email: String,
    val name: String,
    val avatarUrl: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastAccess: Instant = createdAt
)
