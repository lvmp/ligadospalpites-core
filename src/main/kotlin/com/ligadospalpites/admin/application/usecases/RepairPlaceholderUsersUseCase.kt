package com.ligadospalpites.admin.application.usecases

import com.google.firebase.auth.FirebaseAuth
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RepairPlaceholderUsersUseCase(
    private val springDataUserRepository: SpringDataUserRepository,
    private val firebaseAuth: FirebaseAuth? = null
) {
    private val log = LoggerFactory.getLogger(RepairPlaceholderUsersUseCase::class.java)

    data class RepairResult(
        val totalEvaluated: Int,
        val repairedCount: Int,
        val failedCount: Int,
        val message: String
    )

    @Transactional
    fun execute(): RepairResult {
        val allUsers = springDataUserRepository.findAll()
        val placeholderUsers = allUsers.filter { user ->
            isPlaceholderEmail(user.email) || isPlaceholderName(user.name)
        }

        if (placeholderUsers.isEmpty()) {
            return RepairResult(
                totalEvaluated = allUsers.size,
                repairedCount = 0,
                failedCount = 0,
                message = "Nenhum usuário com dados placeholder foi encontrado."
            )
        }

        if (firebaseAuth == null) {
            return RepairResult(
                totalEvaluated = allUsers.size,
                repairedCount = 0,
                failedCount = placeholderUsers.size,
                message = "Serviço Firebase Auth não está disponível para consultar dados reais."
            )
        }

        var repairedCount = 0
        var failedCount = 0

        for (userEntity in placeholderUsers) {
            try {
                val userRecord = firebaseAuth.getUser(userEntity.firebaseUid)
                val newEmail = userRecord.email?.ifBlank { null } ?: userEntity.email
                val newName = userRecord.displayName?.ifBlank { null } ?: userEntity.name
                val newAvatar = userRecord.photoUrl?.ifBlank { null } ?: userEntity.avatarUrl

                if (newEmail != userEntity.email || newName != userEntity.name || newAvatar != userEntity.avatarUrl) {
                    val updatedEntity = com.ligadospalpites.users.infrastructure.persistence.UserJpaEntity(
                        id = userEntity.id,
                        firebaseUid = userEntity.firebaseUid,
                        email = newEmail,
                        name = newName,
                        avatarUrl = newAvatar,
                        createdAt = userEntity.createdAt,
                        lastAccess = userEntity.lastAccess
                    )
                    springDataUserRepository.save(updatedEntity)
                    repairedCount++
                    log.info("Usuário ${userEntity.id} (UID: ${userEntity.firebaseUid}) reparado com sucesso: nome='$newName', email='$newEmail'")
                }
            } catch (e: Exception) {
                log.warn("Falha ao reparar usuário ${userEntity.id} (UID: ${userEntity.firebaseUid}): ${e.message}")
                failedCount++
            }
        }

        return RepairResult(
            totalEvaluated = allUsers.size,
            repairedCount = repairedCount,
            failedCount = failedCount,
            message = "Processo de reparo concluído. $repairedCount usuários atualizados, $failedCount falhas."
        )
    }

    private fun isPlaceholderEmail(email: String): Boolean {
        return email.startsWith("user_") || email.endsWith("@ligadospalpites.com") || email.endsWith("@migrated.com")
    }

    private fun isPlaceholderName(name: String): Boolean {
        return name.startsWith("user_") || name.startsWith("Usuário ") || name == "Usuário Migrado"
    }
}
