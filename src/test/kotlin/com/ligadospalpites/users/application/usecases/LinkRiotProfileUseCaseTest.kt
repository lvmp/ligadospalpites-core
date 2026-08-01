package com.ligadospalpites.users.application.usecases

import com.ligadospalpites.users.infrastructure.client.RiotAccountResponse
import com.ligadospalpites.users.infrastructure.client.RiotGamesClient
import com.ligadospalpites.users.infrastructure.persistence.SpringDataUserRiotProfileRepository
import com.ligadospalpites.users.infrastructure.persistence.UserRiotProfileJpaEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.Optional
import java.util.UUID

class LinkRiotProfileUseCaseTest {

    private val riotProfileRepository = mock(SpringDataUserRiotProfileRepository::class.java)
    private val riotGamesClient = mock(RiotGamesClient::class.java)
    private val useCase = LinkRiotProfileUseCase(riotProfileRepository, riotGamesClient)

    @Test
    fun `should link riot profile successfully`() {
        val userId = UUID.randomUUID()
        val command = LinkRiotProfileUseCase.Command(
            userId = userId,
            gameName = "Faker",
            tagLine = "KR1"
        )

        val account = RiotAccountResponse(puuid = "puuid-faker-123", gameName = "Faker", tagLine = "KR1")
        `when`(riotGamesClient.getAccountByRiotId("Faker", "KR1")).thenReturn(account)
        `when`(riotGamesClient.getLolRankByPuuid("puuid-faker-123")).thenReturn("Desafiante I")
        `when`(riotProfileRepository.findByUserId(userId)).thenReturn(Optional.empty())

        `when`(riotProfileRepository.save(any(UserRiotProfileJpaEntity::class.java))).thenAnswer { invocation ->
            invocation.getArgument(0) as UserRiotProfileJpaEntity
        }

        val result = useCase.execute(command)

        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals("puuid-faker-123", result.puuid)
        assertEquals("Faker", result.gameName)
        assertEquals("KR1", result.tagLine)
        assertEquals("Desafiante I", result.lolRank)
    }
}
