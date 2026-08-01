package com.ligadospalpites.users.application.usecases

import com.ligadospalpites.users.domain.models.User
import com.ligadospalpites.users.domain.ports.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SyncUserProfileUseCase(
    private val userRepository: UserRepository
) {
    data class Command(
        val userId: UUID,
        val name: String?,
        val email: String?,
        val avatarUrl: String? = null
    )

    @Transactional
    fun execute(command: Command): User {
        val user = userRepository.findById(command.userId)
            ?: throw IllegalArgumentException("Usuário não encontrado: ${command.userId}")

        val updatedEmail = if (!command.email.isNullOrBlank()) command.email else user.email
        val updatedName = if (!command.name.isNullOrBlank()) command.name else user.name
        val updatedAvatar = if (!command.avatarUrl.isNullOrBlank()) command.avatarUrl else user.avatarUrl

        val updatedUser = user.copy(
            email = updatedEmail,
            name = updatedName,
            avatarUrl = updatedAvatar,
            lastAccess = Instant.now()
        )

        return userRepository.save(updatedUser)
    }
}
