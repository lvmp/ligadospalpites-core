package com.ligadospalpites.notifications.application.usecases

import com.ligadospalpites.notifications.domain.models.NotificationChannel
import com.ligadospalpites.notifications.domain.models.NotificationTarget
import com.ligadospalpites.sportsfeed.domain.models.MatchStatus
import com.ligadospalpites.sportsfeed.infrastructure.persistence.LeagueJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.MatchJpaEntity
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataLeagueRepository
import com.ligadospalpites.sportsfeed.infrastructure.persistence.SpringDataMatchRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.jupiter.api.Assertions.*

class DispatchDailyAgendaPushUseCaseTest {

    private lateinit var leagueRepository: SpringDataLeagueRepository
    private lateinit var matchRepository: SpringDataMatchRepository
    private lateinit var dispatcherService: NotificationDispatcherService
    private lateinit var useCase: DispatchDailyAgendaPushUseCase

    private val leagueId1 = UUID.randomUUID()
    private val leagueId2 = UUID.randomUUID()

    private fun <T> anyNonNull(default: T): T {
        any<T>()
        return default
    }

    @BeforeEach
    fun setUp() {
        leagueRepository = mock(SpringDataLeagueRepository::class.java)
        matchRepository = mock(SpringDataMatchRepository::class.java)
        dispatcherService = mock(NotificationDispatcherService::class.java)

        useCase = DispatchDailyAgendaPushUseCase(
            leagueRepository = leagueRepository,
            matchRepository = matchRepository,
            dispatcherService = dispatcherService
        )
    }

    @Test
    fun `should dispatch daily agenda with formatted matches segregated by league`() {
        val zoneId = ZoneId.of("America/Sao_Paulo")
        val today = LocalDate.now(zoneId)
        val kickoffInstant = today.atTime(16, 0).atZone(zoneId).toInstant()

        val activeLeagues = listOf(
            LeagueJpaEntity(id = leagueId1, name = "Brasileirão Série A", sportId = UUID.randomUUID(), isActive = true),
            LeagueJpaEntity(id = leagueId2, name = "Premier League", sportId = UUID.randomUUID(), isActive = true)
        )
        `when`(leagueRepository.findByIsActiveTrue()).thenReturn(activeLeagues)

        val matchesToday = listOf(
            MatchJpaEntity(
                id = UUID.randomUUID(), sportId = UUID.randomUUID(), leagueId = leagueId1, seasonId = UUID.randomUUID(),
                homeTeamName = "Flamengo", awayTeamName = "Palmeiras", kickoffTime = kickoffInstant,
                status = MatchStatus.SCHEDULED
            ),
            MatchJpaEntity(
                id = UUID.randomUUID(), sportId = UUID.randomUUID(), leagueId = leagueId2, seasonId = UUID.randomUUID(),
                homeTeamName = "Arsenal", awayTeamName = "Chelsea", kickoffTime = kickoffInstant,
                status = MatchStatus.SCHEDULED
            )
        )

        `when`(matchRepository.findUpcomingMatchesByLeagueIds(
            anyNonNull(emptyList()),
            anyNonNull(Instant.now()),
            anyNonNull(Instant.now()),
            anyNonNull(emptyList()),
            isNull()
        )).thenReturn(matchesToday)

        useCase.execute()

        val targetCaptor = ArgumentCaptor.forClass(NotificationTarget::class.java)
        val targetIdCaptor = ArgumentCaptor.forClass(UUID::class.java)
        val titleCaptor = ArgumentCaptor.forClass(String::class.java)
        val contentCaptor = ArgumentCaptor.forClass(String::class.java)
        @Suppress("UNCHECKED_CAST")
        val channelsCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<NotificationChannel>>
        @Suppress("UNCHECKED_CAST")
        val metadataCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>

        verify(dispatcherService).dispatch(
            targetCaptor.capture() ?: NotificationTarget.ALL,
            targetIdCaptor.capture(),
            titleCaptor.capture() ?: "",
            contentCaptor.capture() ?: "",
            channelsCaptor.capture() ?: emptyList(),
            metadataCaptor.capture() ?: emptyMap()
        )

        assertEquals(NotificationTarget.ALL, targetCaptor.value)
        assertNull(targetIdCaptor.value)
        assertEquals("📅 AGENDA DO DIA - Liga dos Palpites", titleCaptor.value)
        assertEquals("daily_agenda", metadataCaptor.value["type"])
        assertTrue(contentCaptor.value.contains("🏆 Brasileirão Série A:"))
        assertTrue(contentCaptor.value.contains("• Flamengo x Palmeiras (16:00)"))
        assertTrue(contentCaptor.value.contains("🏆 Premier League:"))
        assertTrue(contentCaptor.value.contains("• Arsenal x Chelsea (16:00)"))
        assertTrue(channelsCaptor.value.contains(NotificationChannel.PUSH))
    }

    @Test
    fun `should skip dispatching notification when no matches exist today`() {
        val activeLeagues = listOf(
            LeagueJpaEntity(id = leagueId1, name = "Brasileirão Série A", sportId = UUID.randomUUID(), isActive = true)
        )
        `when`(leagueRepository.findByIsActiveTrue()).thenReturn(activeLeagues)
        `when`(matchRepository.findUpcomingMatchesByLeagueIds(
            anyNonNull(emptyList()),
            anyNonNull(Instant.now()),
            anyNonNull(Instant.now()),
            anyNonNull(emptyList()),
            isNull()
        )).thenReturn(emptyList())

        useCase.execute()

        verifyNoInteractions(dispatcherService)
    }
}
