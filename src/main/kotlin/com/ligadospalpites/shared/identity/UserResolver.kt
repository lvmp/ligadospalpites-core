package com.ligadospalpites.shared.identity

import com.google.firebase.auth.FirebaseAuth
import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class UserResolver(
    private val userRepository: UserRepository,
    private val firebaseAuth: FirebaseAuth? = null
) {
    private val log = LoggerFactory.getLogger(UserResolver::class.java)

    @Transactional
    fun resolve(firebaseUid: String, email: String, name: String, avatarUrl: String? = null): User {
        val existing = userRepository.findByFirebaseUid(firebaseUid)
        if (existing != null) {
            val newEmail = if (email.isNotBlank() && (existing.email != email || isPlaceholderEmail(existing.email))) email else existing.email
            val newName = if (name.isNotBlank() && (existing.name != name || isPlaceholderName(existing.name))) name else existing.name
            val newAvatar = if (!avatarUrl.isNullOrBlank()) avatarUrl else existing.avatarUrl

            val updated = existing.copy(
                email = newEmail,
                name = newName,
                avatarUrl = newAvatar,
                lastAccess = Instant.now()
            )
            return userRepository.save(updated)
        }

        // Try to enrich from Firebase Auth if email/name provided are placeholders
        var finalEmail = email
        var finalName = name
        var finalAvatar = avatarUrl

        if (isPlaceholderEmail(finalEmail) || isPlaceholderName(finalName)) {
            val fbInfo = fetchFirebaseUser(firebaseUid)
            if (fbInfo != null) {
                if (!fbInfo.email.isNullOrBlank()) finalEmail = fbInfo.email
                if (!fbInfo.name.isNullOrBlank()) finalName = fbInfo.name
                if (!fbInfo.avatarUrl.isNullOrBlank()) finalAvatar = fbInfo.avatarUrl
            }
        }

        val now = Instant.now()
        return userRepository.save(
            User(
                id = UUID.randomUUID(),
                firebaseUid = firebaseUid,
                email = finalEmail,
                name = finalName,
                avatarUrl = finalAvatar,
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
                var updated = existing.copy(lastAccess = Instant.now())

                // Check if existing user has placeholder data that can be enriched via Firebase Auth
                if (isPlaceholderEmail(existing.email) || isPlaceholderName(existing.name)) {
                    val fbInfo = fetchFirebaseUser(headerValue)
                    if (fbInfo != null) {
                        updated = updated.copy(
                            email = fbInfo.email ?: updated.email,
                            name = fbInfo.name ?: updated.name,
                            avatarUrl = fbInfo.avatarUrl ?: updated.avatarUrl
                        )
                    }
                }
                userRepository.save(updated)
                return updated.id
            }

            // Create new user, enriching via Firebase Auth if possible
            val fbInfo = fetchFirebaseUser(headerValue)
            val email = fbInfo?.email ?: "user_${headerValue}@ligadospalpites.com"
            val name = fbInfo?.name ?: "Usuário ${headerValue.take(6)}"
            val avatar = fbInfo?.avatarUrl

            val now = Instant.now()
            val user = userRepository.save(
                User(
                    id = UUID.randomUUID(),
                    firebaseUid = headerValue,
                    email = email,
                    name = name,
                    avatarUrl = avatar,
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

    private data class FirebaseUserInfo(val email: String?, val name: String?, val avatarUrl: String?)

    private fun fetchFirebaseUser(firebaseUid: String): FirebaseUserInfo? {
        if (firebaseAuth == null) return null
        return try {
            val userRecord = firebaseAuth.getUser(firebaseUid)
            FirebaseUserInfo(
                email = userRecord.email?.ifBlank { null },
                name = userRecord.displayName?.ifBlank { null },
                avatarUrl = userRecord.photoUrl?.ifBlank { null }
            )
        } catch (e: Exception) {
            log.debug("Não foi possível buscar o usuário $firebaseUid no Firebase Auth: ${e.message}")
            null
        }
    }

    private fun isPlaceholderEmail(email: String): Boolean {
        return email.startsWith("user_") || email.endsWith("@ligadospalpites.com") || email.endsWith("@migrated.com")
    }

    private fun isPlaceholderName(name: String): Boolean {
        return name.startsWith("user_") || name.startsWith("Usuário ") || name == "Usuário Migrado"
    }
}
