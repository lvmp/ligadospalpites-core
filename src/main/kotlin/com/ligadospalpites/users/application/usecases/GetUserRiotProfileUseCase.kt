package com.ligadospalpites.users.application.usecases

import com.ligadospalpites.users.domain.models.UserRiotProfile
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRiotProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class GetUserRiotProfileUseCase(
    private val riotProfileRepository: SpringDataUserRiotProfileRepository
) {

    @Transactional(readOnly = true)
    fun execute(userId: UUID): UserRiotProfile? {
        return riotProfileRepository.findByUserId(userId).map { it.toDomain() }.orElse(null)
    }
}
