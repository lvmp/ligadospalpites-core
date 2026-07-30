package com.ligadospalpites.admin.infrastructure.web

import com.ligadospalpites.admin.application.usecases.GetAuditLogsUseCase
import com.ligadospalpites.admin.infrastructure.web.dtos.AuditLogDto
import com.ligadospalpites.admin.infrastructure.web.dtos.AuditLogsResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/audit-logs", "/api/v1/workspace/audit-logs")
class AdminAuditController(
    private val getAuditLogsUseCase: GetAuditLogsUseCase
) {

    @GetMapping
    fun getAuditLogs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<AuditLogsResponse> {
        val logs = getAuditLogsUseCase(page, limit).map { domain ->
            AuditLogDto(
                id = domain.id.toString(),
                operatorId = domain.operatorId,
                action = domain.action,
                targetId = domain.targetId,
                details = domain.details,
                timestamp = domain.timestamp
            )
        }
        return ResponseEntity.ok(AuditLogsResponse(logs = logs))
    }
}
