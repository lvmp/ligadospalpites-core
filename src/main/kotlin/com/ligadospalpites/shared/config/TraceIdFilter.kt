package com.ligadospalpites.shared.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class TraceIdFilter : OncePerRequestFilter() {

    companion object {
        private const val TRACE_ID_KEY = "traceId"
        private const val TRACE_HEADER = "X-Trace-Id"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = request.getHeader(TRACE_HEADER) ?: UUID.randomUUID().toString()
        
        MDC.put(TRACE_ID_KEY, traceId)
        
        response.addHeader(TRACE_HEADER, traceId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_KEY)
        }
    }
}
