package com.ligadospalpites.shared.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

import com.ligadospalpites.shared.config.TraceIdFilter
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val traceIdFilter: TraceIdFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .addFilterBefore(traceIdFilter, WebAsyncManagerIntegrationFilter::class.java)
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // Permit endpoints like internal synchronization endpoints, health, h2, etc.
                    .requestMatchers("/api/v1/internal/**").permitAll()
                    .requestMatchers("/api/v1/payments/revenuecat/webhook").permitAll()
                    .requestMatchers("/api/v1/**").permitAll() // Permit for development/testing ease
                    .anyRequest().permitAll()
            }
        return http.build()
    }
}
