package com.ligadospalpites.shared.security

import com.ligadospalpites.admin.infrastructure.web.security.AdminApiKeyFilter
import com.ligadospalpites.shared.config.TraceIdFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val traceIdFilter: TraceIdFilter,
    private val adminApiKeyFilter: AdminApiKeyFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(traceIdFilter, WebAsyncManagerIntegrationFilter::class.java)
            .addFilterBefore(adminApiKeyFilter, AuthorizationFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/v1/internal/**").permitAll()
                    .requestMatchers("/api/v1/payments/revenuecat/webhook").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/v1/admin/**").permitAll()
                    .requestMatchers("/api/v1/workspace/**").permitAll()
                    .requestMatchers("/api/v1/**").permitAll()
                    .anyRequest().permitAll()
            }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*")
        configuration.allowedMethods = listOf("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf(
            "Content-Type",
            "Authorization",
            "X-Admin-Api-Key",
            "X-Workspace-Api-Key",
            "X-User-Id"
        )
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
