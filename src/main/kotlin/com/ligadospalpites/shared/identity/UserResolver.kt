package com.ligadospalpites.shared.identity

import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class UserResolver(private val userRepository: UserRepository) {

    @Transactional
    fun resolve(firebaseUid: String, email: String, name: String): User {
        return userRepository.findByFirebaseUid(firebaseUid)
            ?: userRepository.save(
                User(
                    id = UUID.randomUUID(),
                    firebaseUid = firebaseUid,
                    email = email,
                    name = name
                )
            )
    }

    @Transactional
    fun resolveByUidOrUuid(headerValue: String?): UUID {
        if (headerValue.isNullOrBlank()) {
            return UUID.fromString("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        }
        try {
            return UUID.fromString(headerValue)
        } catch (e: IllegalArgumentException) {
            val user = userRepository.findByFirebaseUid(headerValue)
                ?: userRepository.save(
                    User(
                        id = UUID.randomUUID(),
                        firebaseUid = headerValue,
                        email = "user_${headerValue}@ligadospalpites.com",
                        name = "Usuário ${headerValue.take(6)}"
                    )
                )
            return user.id
        }
    }
}
