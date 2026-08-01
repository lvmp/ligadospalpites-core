package com.ligadospalpites.users.application.usecases

import com.ligadospalpites.users.domain.models.UserRiotProfile
import com.ligadospalpites.users.infrastructure.client.RiotGamesClient
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRiotProfileRepository
import com.ligadospalpites.users.infrastructure.persistence.UserRiotProfileJpaEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class LinkRiotProfileUseCase(
    private val riotProfileRepository: SpringDataUserRiotProfileRepository,
    private val riotGamesClient: RiotGamesClient
) {

    data class Command(
        val userId: UUID,
        val gameName: String,
        val tagLine: String
    )

    @Transactional
    fun execute(command: Command): UserRiotProfile {
        val cleanGameName = command.gameName.trim()
        val cleanTagLine = command.tagLine.trim().removePrefix("#")

        require(cleanGameName.isNotBlank()) { "Game Name cannot be blank." }
        require(cleanTagLine.isNotBlank()) { "Tag Line cannot be blank." }

        val account = riotGamesClient.getAccountByRiotId(cleanGameName, cleanTagLine)
            ?: throw IllegalArgumentException("Riot account $cleanGameName#$cleanTagLine not found.")

        val lolRank = riotGamesClient.getLolRankByPuuid(account.puuid)

        val existing = riotProfileRepository.findByUserId(command.userId)
        val entityToSave = if (existing.isPresent) {
            val curr = existing.get()
            UserRiotProfileJpaEntity(
                id = curr.id,
                userId = command.userId,
                puuid = account.puuid,
                gameName = account.gameName,
                tagLine = account.tagLine,
                lolRank = lolRank,
                valorantRank = curr.valorantRank ?: "Sem Rank",
                updatedAt = Instant.now()
            )
        } else {
            UserRiotProfileJpaEntity(
                id = UUID.randomUUID(),
                userId = command.userId,
                puuid = account.puuid,
                gameName = account.gameName,
                tagLine = account.tagLine,
                lolRank = lolRank,
                valorantRank = "Sem Rank",
                updatedAt = Instant.now()
            )
        }

        val saved = riotProfileRepository.save(entityToSave)
        return saved.toDomain()
    }
}
