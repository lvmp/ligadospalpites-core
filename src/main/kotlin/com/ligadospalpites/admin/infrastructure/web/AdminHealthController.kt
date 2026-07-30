package com.ligadospalpites.admin.infrastructure.web

import com.ligadospalpites.admin.application.usecases.GetConnectorsHealthUseCase
import com.ligadospalpites.admin.domain.models.ConnectorsHealthInfo
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/health", "/api/v1/workspace/health")
class AdminHealthController(
    private val getConnectorsHealthUseCase: GetConnectorsHealthUseCase
) {

    @GetMapping
    fun getHealth(): ResponseEntity<ConnectorsHealthInfo> {
        val health = getConnectorsHealthUseCase()
        return ResponseEntity.ok(health)
    }
}
