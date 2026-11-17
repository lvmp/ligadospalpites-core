package com.ligadospalpites.sportsfeed.domain.models

import java.time.Instant
import java.util.UUID

data class League(
    val id: UUID,
    val name: String,
    val sportId: UUID,
    val isActive: Boolean = true,
    val logoUrl: String? = null,
    val format: String = "POINTS",
    val createdAt: Instant = Instant.now()
)
