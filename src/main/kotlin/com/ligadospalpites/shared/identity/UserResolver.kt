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
}
