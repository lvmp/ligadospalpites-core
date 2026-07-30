package com.ligadospalpites.admin.infrastructure.web.dtos

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class UpdateLeagueStatusRequest(
    @field:NotBlank(message = "Status is required")
    val status: String
)

data class AdminLeagueDto(
    val id: String,
    val name: String,
    val sportId: String,
    @get:com.fasterxml.jackson.annotation.JsonProperty("isActive")
    val isActive: Boolean,
    val logoUrl: String?,
    val createdAt: Instant
)

data class GrantUserPlanRequest(
    @field:NotBlank(message = "Plan is required")
    val plan: String,
    @field:NotNull(message = "DurationDays is required")
    val durationDays: Int,
    val reason: String? = null
)

data class DispatchNotificationRequest(
    @field:NotBlank(message = "Target is required")
    val target: String,
    val targetId: String? = null,
    @field:NotBlank(message = "Title is required")
    val title: String,
    @field:NotBlank(message = "Content is required")
    val content: String
)

data class AuditLogDto(
    val id: String,
    val operatorId: String,
    val action: String,
    val targetId: String?,
    val details: String?,
    val timestamp: Instant
)

data class AuditLogsResponse(
    val logs: List<AuditLogDto>
)

data class AdminUserSummaryDto(
    val id: String,
    val firebaseUid: String,
    val email: String,
    val name: String,
    val avatarUrl: String?,
    val plan: String,
    val createdAt: Instant,
    val lastAccess: Instant
)

data class AdminUsersPageResponse(
    val content: List<AdminUserSummaryDto>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)
