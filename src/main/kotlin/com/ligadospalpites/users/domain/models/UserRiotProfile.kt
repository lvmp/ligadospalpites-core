package com.ligadospalpites.users.domain.models

import java.time.Instant
import java.util.UUID

data class UserRiotProfile(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val puuid: String,
    val gameName: String,
    val tagLine: String,
    val lolRank: String? = null,
    val valorantRank: String? = null,
    val updatedAt: Instant = Instant.now()
)
