package com.ligadospalpites.shared.identity

import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class UserResolver(private val userRepository: UserRepository) {

    @Transactional
    fun resolve(firebaseUid: String, email: String, name: String): User {
        val existing = userRepository.findByFirebaseUid(firebaseUid)
        if (existing != null) {
            val updated = existing.copy(lastAccess = Instant.now())
            return userRepository.save(updated)
        }
        val now = Instant.now()
        return userRepository.save(
            User(
                id = UUID.randomUUID(),
                firebaseUid = firebaseUid,
                email = email,
                name = name,
                createdAt = now,
                lastAccess = now
            )
        )
    }

    @Transactional
    fun resolveByUidOrUuid(headerValue: String?): UUID {
        if (headerValue.isNullOrBlank()) {
            val defaultId = UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            userRepository.findById(defaultId)?.let { user ->
                userRepository.save(user.copy(lastAccess = Instant.now()))
            }
            return defaultId
        }
        try {
            val uuid = UUID.fromString(headerValue)
            userRepository.findById(uuid)?.let { user ->
                userRepository.save(user.copy(lastAccess = Instant.now()))
            }
            return uuid
        } catch (e: IllegalArgumentException) {
            val existing = userRepository.findByFirebaseUid(headerValue)
            if (existing != null) {
                val updated = existing.copy(lastAccess = Instant.now())
                userRepository.save(updated)
                return updated.id
            }
            val now = Instant.now()
            val user = userRepository.save(
                User(
                    id = UUID.randomUUID(),
                    firebaseUid = headerValue,
                    email = "user_${headerValue}@ligadospalpites.com",
                    name = "Usuário ${headerValue.take(6)}",
                    createdAt = now,
                    lastAccess = now
                )
            )
            return user.id
        }
    }

    @Transactional
    fun resolveAuthenticatedUser(headerValue: String?, authentication: Authentication?): UUID {
        val jwtPrincipal = if (authentication != null && authentication.isAuthenticated && authentication.name != "anonymousUser") {
            authentication.name
        } else null

        if (jwtPrincipal != null && !headerValue.isNullOrBlank()) {
            val jwtUserUuid = resolveByUidOrUuid(jwtPrincipal)
            val headerUserUuid = resolveByUidOrUuid(headerValue)
            if (jwtUserUuid != headerUserUuid) {
                throw AccessDeniedException(
                    "Acesso negado: O usuário do Token JWT não corresponde ao X-User-Id informado."
                )
            }
            return jwtUserUuid
        }

        if (jwtPrincipal != null) {
            return resolveByUidOrUuid(jwtPrincipal)
        }

        return resolveByUidOrUuid(headerValue)
    }
}
