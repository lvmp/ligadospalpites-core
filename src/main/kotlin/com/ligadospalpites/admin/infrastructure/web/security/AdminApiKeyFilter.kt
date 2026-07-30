package com.ligadospalpites.admin.infrastructure.web.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AdminApiKeyFilter(
    @Value("\${app.admin.api-key:lp_ws_live_secret_key_2026_x89f}")
    private val expectedApiKey: String
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        // Filter applies to /api/v1/admin/** and legacy /api/v1/workspace/**
        return !path.startsWith("/api/v1/admin") && !path.startsWith("/api/v1/workspace")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if ("OPTIONS".equals(request.method, ignoreCase = true)) {
            filterChain.doFilter(request, response)
            return
        }

        val apiKeyHeader = request.getHeader("X-Admin-Api-Key")
            ?: request.getHeader("X-Workspace-Api-Key")

        if (apiKeyHeader == null || apiKeyHeader != expectedApiKey) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            response.writer.write(
                """{"error":"UNAUTHORIZED","message":"Chave de API de administração inválida ou não fornecida."}"""
            )
            return
        }

        filterChain.doFilter(request, response)
    }
}
